package com.vendingmachine.transaction.transaction;

import com.vendingmachine.transaction.client.InventoryServiceClient;
import com.vendingmachine.transaction.client.PaymentServiceClient;
import com.vendingmachine.transaction.client.DispensingServiceClient;
import com.vendingmachine.transaction.transaction.dto.PurchaseRequestDTO;
import com.vendingmachine.transaction.transaction.dto.PurchaseItemDTO;
import com.vendingmachine.transaction.transaction.dto.TransactionDTO;
import com.vendingmachine.transaction.transaction.dto.TransactionSummaryDTO;
import com.vendingmachine.transaction.transaction.dto.PaymentInfo;
import com.vendingmachine.transaction.transaction.dto.PaymentMethod;
import com.vendingmachine.transaction.kafka.KafkaEventService;
import com.vendingmachine.transaction.exception.InsufficientStockException;
import com.vendingmachine.transaction.exception.PaymentFailedException;
import com.vendingmachine.common.event.TransactionEvent;
import com.vendingmachine.common.aop.annotation.Auditable;
import com.vendingmachine.common.aop.annotation.ExecutionTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final KafkaEventService kafkaEventService;
    
    // Circuit Breaker enabled clients
    private final InventoryServiceClient inventoryClient;
    private final PaymentServiceClient paymentClient;
    private final DispensingServiceClient dispensingClient;

    @Transactional
    @Auditable(operation = "Purchase Transaction", entityType = "Transaction", logParameters = true, logResult = true)
    @ExecutionTime(operation = "purchase", warningThreshold = 2000, detailed = true)
    public TransactionDTO purchase(PurchaseRequestDTO request) {
        log.info("Starting anonymous purchase transaction for {} items", request.getItems().size());

        // Validate payment information
        PaymentInfo paymentInfo = request.getPaymentInfo();
        if (paymentInfo == null) {
            throw new RuntimeException("Payment information is required");
        }

        // Create transaction entity (anonymous - no customerId)
        Transaction transaction = Transaction.builder()
                .status(TransactionStatus.PENDING)
                .totalAmount(BigDecimal.ZERO) // Will calculate after inventory check
                .paymentMethod(paymentInfo.getPaymentMethod().name())
                .build();

        // Check inventory availability synchronously (critical for immediate feedback)
        if (!checkInventoryAvailability(request.getItems())) {
            transaction.setStatus(TransactionStatus.FAILED);
            transactionRepository.save(transaction);
            throw new InsufficientStockException("Stock unavailable - Product(s) out of stock or inventory service unreachable");
        }

        // Calculate total amount from inventory response
        BigDecimal totalAmount = calculateTotalAmount(request.getItems());
        transaction.setTotalAmount(totalAmount);

        // Save transaction to get ID before processing payment
        Transaction savedTransaction = transactionRepository.save(transaction);

        // Process payment synchronously with transaction ID
        boolean paymentSuccess = processPayment(savedTransaction.getId(), paymentInfo, totalAmount);
        if (!paymentSuccess) {
            savedTransaction.setStatus(TransactionStatus.FAILED);
            transactionRepository.save(savedTransaction);
            throw new PaymentFailedException("Payment processing failed - Service unavailable or insufficient funds");
        }

        // Calculate change for cash payments
        if (paymentInfo.getPaymentMethod() == PaymentMethod.CASH) {
            BigDecimal paidAmount = paymentInfo.getPaidAmount();
            if (paidAmount != null && paidAmount.compareTo(totalAmount) >= 0) {
                savedTransaction.setPaidAmount(paidAmount);
                savedTransaction.setChangeAmount(paidAmount.subtract(totalAmount));
            } else {
                savedTransaction.setStatus(TransactionStatus.FAILED);
                transactionRepository.save(savedTransaction);
                throw new PaymentFailedException("Insufficient cash amount provided - Required: " + totalAmount);
            }
        } else {
            // For card payments, paid amount equals total amount
            savedTransaction.setPaidAmount(totalAmount);
            savedTransaction.setChangeAmount(BigDecimal.ZERO);
        }

        // Create transaction items
        List<TransactionItem> items = request.getItems().stream()
                .map(item -> TransactionItem.builder()
                        .productId(item.getProductId())
                        .quantity(item.getQuantity())
                        .price(getProductPrice(item.getProductId())) // From inventory
                        .transaction(savedTransaction)
                        .build())
                .collect(Collectors.toList());

        savedTransaction.setItems(items);
        savedTransaction.setStatus(TransactionStatus.PROCESSING); // Move to processing after payment
        Transaction finalTransaction = transactionRepository.save(savedTransaction);

        // Publish transaction PROCESSING event to trigger dispensing
        TransactionEvent transactionEvent = new TransactionEvent(
            "txn-processing-" + finalTransaction.getId() + "-" + System.currentTimeMillis(),
            finalTransaction.getId(),
            "PROCESSING",
            totalAmount.doubleValue(),
            System.currentTimeMillis()
        );
        kafkaEventService.publishTransactionEvent(transactionEvent);

        log.info("Purchase transaction initiated with PROCESSING status: {}", finalTransaction.getId());
        return mapToDTO(finalTransaction);
    }

    /**
     * Check inventory availability using circuit breaker enabled client
     */
    @ExecutionTime(operation = "checkInventoryAvailability", warningThreshold = 500)
    private boolean checkInventoryAvailability(List<PurchaseItemDTO> items) {
        try {
            // Convert items to format expected by client
            List<Map<String, Object>> itemMaps = items.stream()
                .map(item -> Map.of(
                    "productId", (Object) item.getProductId(),
                    "quantity", (Object) item.getQuantity()
                ))
                .collect(Collectors.toList());

            // Use circuit breaker enabled client
            Map<Long, Map<String, Object>> availabilityMap = inventoryClient.checkAvailability(itemMaps);
            
            if (availabilityMap == null || availabilityMap.isEmpty()) {
                log.warn("No availability response received from inventory service");
                return false;
            }

            // Check if all items are available
            for (PurchaseItemDTO item : items) {
                Map<String, Object> itemStatus = availabilityMap.get(item.getProductId());
                if (itemStatus == null) {
                    log.warn("No availability info for product {}", item.getProductId());
                    return false;
                }

                Boolean available = (Boolean) itemStatus.get("available");
                if (available == null || !available) {
                    log.warn("Product {} is not available", item.getProductId());
                    
                    // Check if this is a fallback response
                    Boolean isFallback = (Boolean) itemStatus.get("fallback");
                    if (Boolean.TRUE.equals(isFallback)) {
                        log.error("Inventory service is unavailable (circuit breaker open)");
                    }
                    return false;
                }
            }

            log.info("All {} items are available", items.size());
            return true;
        } catch (Exception e) {
            log.error("Failed to check inventory availability", e);
            return false;
        }
    }

    private BigDecimal calculateTotalAmount(List<PurchaseItemDTO> items) {
        // For now, sum up prices from inventory
        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseItemDTO item : items) {
            BigDecimal price = getProductPrice(item.getProductId());
            total = total.add(price.multiply(BigDecimal.valueOf(item.getQuantity())));
        }
        return total;
    }

    /**
     * Get product price using circuit breaker enabled client
     */
    @SuppressWarnings("null")
    private BigDecimal getProductPrice(Long productId) {
        try {
            // Use circuit breaker enabled inventory client
            return inventoryClient.getProductPrice(productId);
        } catch (Exception e) {
            log.error("Failed to get product price for {}", productId, e);
            return BigDecimal.ZERO;
        }
    }

    /**
     * Process payment using circuit breaker enabled client
     */
    @SuppressWarnings("null")
    @ExecutionTime(operation = "processPayment", warningThreshold = 800)
    private boolean processPayment(Long transactionId, PaymentInfo paymentInfo, BigDecimal amount) {
        try {
            // Use circuit breaker enabled payment client
            Map<String, Object> paymentResponse = paymentClient.processPayment(
                transactionId.toString(), 
                paymentInfo, 
                amount
            );

            if (paymentResponse == null) {
                log.error("No response received from payment service");
                return false;
            }

            // Check if this is a fallback response
            Boolean isFallback = (Boolean) paymentResponse.get("fallback");
            if (Boolean.TRUE.equals(isFallback)) {
                log.error("Payment service is unavailable (circuit breaker open)");
                return false;
            }

            // Check payment success
            Object successObj = paymentResponse.get("success");
            Boolean success = successObj instanceof Boolean ? (Boolean) successObj : false;
            
            String status = (String) paymentResponse.get("status");
            boolean isSuccess = success || "SUCCESS".equals(status);
            
            log.info("Payment processing result: {} (status: {})", isSuccess, status);
            return isSuccess;
            
        } catch (Exception e) {
            log.error("Failed to process payment", e);
            return false;
        }
    }

    @Transactional
    @Auditable(operation = "Compensate Transaction", entityType = "Transaction", logParameters = true)
    @ExecutionTime(operation = "compensateTransaction", warningThreshold = 1500, detailed = true)
    public void compensateTransaction(Long transactionId, String reason) {
        log.info("Starting compensation for transaction {}: {}", transactionId, reason);

        Optional<Transaction> transactionOpt = transactionRepository.findById(transactionId);
        if (transactionOpt.isEmpty()) {
            log.warn("Transaction {} not found for compensation", transactionId);
            return;
        }

        Transaction transaction = transactionOpt.get();

        // Only compensate if transaction is in processing state
        if (transaction.getStatus() != TransactionStatus.PROCESSING) {
            log.warn("Transaction {} is not in PROCESSING state, cannot compensate. Current status: {}",
                    transactionId, transaction.getStatus());
            return;
        }

        try {
            // Attempt to refund payment
            boolean refundSuccess = refundPayment(transactionId, transaction.getTotalAmount());
            if (refundSuccess) {
                transaction.setStatus(TransactionStatus.CANCELLED);
                log.info("Successfully compensated transaction {} with refund", transactionId);
            } else {
                transaction.setStatus(TransactionStatus.FAILED);
                log.warn("Failed to refund payment for transaction {}, marking as FAILED", transactionId);
            }

            transactionRepository.save(transaction);

            // Publish compensation event
            TransactionEvent compensationEvent = new TransactionEvent(
                "txn-comp-" + transactionId + "-" + System.currentTimeMillis(),
                transactionId,
                "COMPENSATED",
                transaction.getTotalAmount().doubleValue(),
                System.currentTimeMillis()
            );
            kafkaEventService.publishTransactionEvent(compensationEvent);

        } catch (Exception e) {
            log.error("Failed to compensate transaction {}: {}", transactionId, e.getMessage());
            // Mark as failed but don't throw - compensation should be idempotent
            transaction.setStatus(TransactionStatus.FAILED);
            transactionRepository.save(transaction);
        }
    }

    /**
     * Refund payment using circuit breaker enabled client
     */
    @SuppressWarnings("null")
    @ExecutionTime(operation = "refundPayment", warningThreshold = 800)
    private boolean refundPayment(Long transactionId, BigDecimal amount) {
        try {
            // Use circuit breaker enabled payment client
            Map<String, Object> refundResponse = paymentClient.refundPayment(
                transactionId.toString(), 
                amount
            );

            if (refundResponse == null) {
                log.error("No response received from payment service for refund");
                return false;
            }

            // Check if this is a fallback response
            Boolean isFallback = (Boolean) refundResponse.get("fallback");
            if (Boolean.TRUE.equals(isFallback)) {
                log.error("Payment service is unavailable for refund (circuit breaker open)");
                return false;
            }

            // Check refund success
            Boolean success = (Boolean) refundResponse.get("success");
            boolean isSuccess = success != null && success;
            
            log.info("Payment refund result: {}", isSuccess);
            return isSuccess;
            
        } catch (Exception e) {
            log.error("Failed to process refund", e);
            return false;
        }
    }

    public List<TransactionDTO> getAllTransactions() {
        return transactionRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public List<TransactionDTO> getTransactionsByStatus(TransactionStatus status) {
        return transactionRepository.findByStatusOrderByCreatedAtDesc(status)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public TransactionDTO getTransactionById(Long id) {
        return transactionRepository.findById(id)
                .map(this::mapToDTO)
                .orElseThrow(() -> new RuntimeException("Transaction not found: " + id));
    }

    @ExecutionTime(operation = "getTransactionSummary", warningThreshold = 1000)
    public TransactionSummaryDTO getTransactionSummary() {
        List<Transaction> allTransactions = transactionRepository.findAll();

        long totalTransactions = allTransactions.size();
        BigDecimal totalRevenue = allTransactions.stream()
                .filter(t -> t.getStatus() == TransactionStatus.COMPLETED)
                .map(Transaction::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Long> transactionsByStatus = allTransactions.stream()
                .collect(Collectors.groupingBy(t -> t.getStatus().name(), Collectors.counting()));

        Map<LocalDate, BigDecimal> dailyRevenue = allTransactions.stream()
                .filter(t -> t.getStatus() == TransactionStatus.COMPLETED)
                .collect(Collectors.groupingBy(
                        t -> t.getCreatedAt().toLocalDate(),
                        Collectors.reducing(BigDecimal.ZERO, Transaction::getTotalAmount, BigDecimal::add)
                ));

        Map<LocalDate, Long> dailyTransactionCount = allTransactions.stream()
                .collect(Collectors.groupingBy(
                        t -> t.getCreatedAt().toLocalDate(),
                        Collectors.counting()
                ));

        BigDecimal averageTransactionValue = totalTransactions > 0 ?
                totalRevenue.divide(BigDecimal.valueOf(totalTransactions), 2, java.math.RoundingMode.HALF_UP) :
                BigDecimal.ZERO;

        long successfulTransactions = transactionsByStatus.getOrDefault("COMPLETED", 0L);
        long failedTransactions = transactionsByStatus.getOrDefault("FAILED", 0L) +
                                 transactionsByStatus.getOrDefault("CANCELLED", 0L);
        BigDecimal successRate = totalTransactions > 0 ?
                BigDecimal.valueOf(successfulTransactions).divide(BigDecimal.valueOf(totalTransactions), 4, java.math.RoundingMode.HALF_UP) :
                BigDecimal.ZERO;

        return new TransactionSummaryDTO(
                totalTransactions,
                totalRevenue,
                transactionsByStatus,
                dailyRevenue,
                dailyTransactionCount,
                averageTransactionValue,
                successfulTransactions,
                failedTransactions,
                successRate
        );
    }

    private TransactionDTO mapToDTO(Transaction transaction) {
        return TransactionDTO.builder()
                .id(transaction.getId())
                // No customerId - anonymous transactions
                .items(transaction.getItems().stream()
                        .map(item -> com.vendingmachine.transaction.transaction.dto.TransactionItemDTO.builder()
                                .id(item.getId())
                                .productId(item.getProductId())
                                .quantity(item.getQuantity())
                                .price(item.getPrice())
                                .build())
                        .collect(Collectors.toList()))
                .totalAmount(transaction.getTotalAmount())
                .paymentMethod(transaction.getPaymentMethod())
                .paidAmount(transaction.getPaidAmount())
                .changeAmount(transaction.getChangeAmount())
                .status(transaction.getStatus())
                .createdAt(transaction.getCreatedAt())
                .updatedAt(transaction.getUpdatedAt())
                .build();
    }
}