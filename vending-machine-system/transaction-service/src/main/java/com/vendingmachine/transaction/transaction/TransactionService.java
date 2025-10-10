package com.vendingmachine.transaction.transaction;

import com.vendingmachine.transaction.transaction.dto.PurchaseRequestDTO;
import com.vendingmachine.transaction.transaction.dto.PurchaseItemDTO;
import com.vendingmachine.transaction.transaction.dto.TransactionDTO;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final RestTemplate restTemplate;

    @Value("${services.inventory.url:http://localhost:8081}")
    private String inventoryServiceUrl;

    @Value("${services.payment.url:http://localhost:8082}")
    private String paymentServiceUrl;

    @Transactional
    public TransactionDTO purchase(PurchaseRequestDTO request) {
        log.info("Starting anonymous purchase transaction for {} items", request.getItems().size());

        // Create transaction entity (anonymous - no customerId)
        Transaction transaction = Transaction.builder()
                .status(TransactionStatus.PENDING)
                .totalAmount(BigDecimal.ZERO) // Will calculate after inventory check
                .build();

        // Check inventory availability
        if (!checkInventoryAvailability(request.getItems())) {
            transaction.setStatus(TransactionStatus.FAILED);
            transactionRepository.save(transaction);
            throw new RuntimeException("Insufficient inventory for one or more items");
        }

        // Calculate total amount from inventory response
        BigDecimal totalAmount = calculateTotalAmount(request.getItems());
        transaction.setTotalAmount(totalAmount);

        // Process payment (anonymous transaction)
        boolean paymentSuccess = processPayment(totalAmount);
        if (!paymentSuccess) {
            transaction.setStatus(TransactionStatus.FAILED);
            transactionRepository.save(transaction);
            throw new RuntimeException("Payment processing failed");
        }

        // Create transaction items
        List<TransactionItem> items = request.getItems().stream()
                .map(item -> TransactionItem.builder()
                        .productId(item.getProductId())
                        .quantity(item.getQuantity())
                        .price(getProductPrice(item.getProductId())) // From inventory
                        .transaction(transaction)
                        .build())
                .collect(Collectors.toList());

        transaction.setItems(items);
        transaction.setStatus(TransactionStatus.COMPLETED);

        Transaction saved = transactionRepository.save(transaction);

        // TODO: Send dispensing event via Kafka
        // TODO: Update inventory via Kafka event

        log.info("Purchase transaction completed successfully: {}", saved.getId());
        return mapToDTO(saved);
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
    private boolean processPayment(BigDecimal amount) {
        try {
            String url = paymentServiceUrl + "/api/payment/process";
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Service", "transaction-service");
            Map<String, Object> paymentRequest = Map.of(
                "amount", amount
            );
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(paymentRequest, headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, new ParameterizedTypeReference<Map<String, Object>>() {});
            if (response.getBody() != null) {
                Boolean success = (Boolean) response.getBody().get("success");
                log.info("Payment processing result: {}", success);
                return success != null && success;
            }
            return false;
        } catch (Exception e) {
            log.error("Failed to process payment", e);
            return false;
        }
    }

    public List<TransactionDTO> getAllTransactions() {
        return transactionRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
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
                .status(transaction.getStatus())
                .createdAt(transaction.getCreatedAt())
                .updatedAt(transaction.getUpdatedAt())
                .build();
    }
}