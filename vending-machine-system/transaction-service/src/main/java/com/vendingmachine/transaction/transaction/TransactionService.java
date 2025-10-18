package com.vendingmachine.transaction.transaction;

import com.vendingmachine.transaction.transaction.dto.PurchaseRequestDTO;
import com.vendingmachine.transaction.transaction.dto.PurchaseItemDTO;
import com.vendingmachine.transaction.transaction.dto.TransactionDTO;
import com.vendingmachine.transaction.transaction.dto.TransactionSummaryDTO;
import com.vendingmachine.transaction.transaction.dto.PaymentInfo;
import com.vendingmachine.transaction.transaction.dto.PaymentMethod;
import com.vendingmachine.transaction.kafka.KafkaEventService;
import com.vendingmachine.common.event.TransactionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

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
    private final RestTemplate restTemplate;

    @Value("${services.inventory.url:http://localhost:8081}")
    private String inventoryServiceUrl;

    @Value("${services.payment.url:http://localhost:8082}")
    private String paymentServiceUrl;

    @Transactional
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
            throw new RuntimeException("Insufficient inventory for one or more items");
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
            throw new RuntimeException("Payment processing failed");
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
                throw new RuntimeException("Insufficient cash amount provided");
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

        // Publish transaction started event (will trigger dispensing)
        TransactionEvent transactionEvent = new TransactionEvent(
            "txn-start-" + finalTransaction.getId() + "-" + System.currentTimeMillis(),
            finalTransaction.getId(),
            "STARTED",
            totalAmount.doubleValue(),
            System.currentTimeMillis()
        );
        kafkaEventService.publishTransactionEvent(transactionEvent);

        log.info("Purchase transaction initiated: {}", finalTransaction.getId());
        return mapToDTO(finalTransaction);
    }

    private boolean checkInventoryAvailability(List<PurchaseItemDTO> items) {
        try {
            String url = inventoryServiceUrl + "/api/inventory/check-availability";
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Service", "transaction-service");
            HttpEntity<List<PurchaseItemDTO>> entity = new HttpEntity<>(items, headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, new ParameterizedTypeReference<Map<String, Object>>() {});
            if (response.getBody() != null) {
                @SuppressWarnings("null")
                Boolean available = (Boolean) response.getBody().get("available");
                log.info("Inventory check result: {}", available);
                return available != null && available;
            }
            return false;
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

    @SuppressWarnings("null")
    private BigDecimal getProductPrice(Long productId) {
        try {
            String url = inventoryServiceUrl + "/api/inventory/products/" + productId;
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Service", "transaction-service");
            HttpEntity<?> entity = new HttpEntity<>(headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, new ParameterizedTypeReference<Map<String, Object>>() {});
            if (response.getBody() != null) {
                Double price = (Double) response.getBody().get("price");
                return BigDecimal.valueOf(price != null ? price : 0.0);
            }
            return BigDecimal.ZERO;
        } catch (Exception e) {
            log.error("Failed to get product price for {}", productId, e);
            return BigDecimal.ZERO;
        }
    }

    @SuppressWarnings("null")
    private boolean processPayment(Long transactionId, PaymentInfo paymentInfo, BigDecimal amount) {
        try {
            String url = paymentServiceUrl + "/api/payment/process";
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Service", "transaction-service");
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

            // Create payment request in the correct format
            Map<String, Object> paymentRequest = new java.util.HashMap<>();
            paymentRequest.put("transactionId", transactionId);
            paymentRequest.put("paymentMethod", paymentInfo.getPaymentMethod().name());
            paymentRequest.put("amount", amount);

            // Add card details for card payments
            if (paymentInfo.getPaymentMethod() != PaymentMethod.CASH) {
                paymentRequest.put("cardNumber", paymentInfo.getCardNumber());
                paymentRequest.put("cardHolderName", paymentInfo.getCardHolderName());
                paymentRequest.put("expiryDate", paymentInfo.getExpiryDate());
            } else {
                // Add paid amount for cash payments
                paymentRequest.put("paidAmount", paymentInfo.getPaidAmount());
            }

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(paymentRequest, headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, new ParameterizedTypeReference<Map<String, Object>>() {});
            if (response.getBody() != null) {
                String status = (String) response.getBody().get("status");
                boolean success = "SUCCESS".equals(status);
                log.info("Payment processing result: {} (status: {})", success, status);
                return success;
            }
            return false;
        } catch (Exception e) {
            log.error("Failed to process payment", e);
            return false;
        }
    }

    @Transactional
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
            boolean refundSuccess = refundPayment(transaction.getTotalAmount());
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

    @SuppressWarnings("null")
    private boolean refundPayment(BigDecimal amount) {
        try {
            String url = paymentServiceUrl + "/api/payment/refund";
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Service", "transaction-service");
            Map<String, Object> refundRequest = Map.of(
                "amount", amount
            );
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(refundRequest, headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, new ParameterizedTypeReference<Map<String, Object>>() {});
            if (response.getBody() != null) {
                Boolean success = (Boolean) response.getBody().get("success");
                log.info("Payment refund result: {}", success);
                return success != null && success;
            }
            return false;
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