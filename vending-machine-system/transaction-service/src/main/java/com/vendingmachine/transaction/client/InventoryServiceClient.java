package com.vendingmachine.transaction.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Client for communicating with Inventory Service.
 * Implements Circuit Breaker and Retry patterns for fault tolerance.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryServiceClient {

    private final RestTemplate restTemplate;

    @Value("${services.inventory.url:http://localhost:8081}")
    private String inventoryServiceUrl;

    /**
     * Checks product availability in inventory.
     * Uses Circuit Breaker to prevent cascading failures.
     * 
     * @param items List of items with productId and quantity
     * @return Map of productId to availability status
     */
    @CircuitBreaker(name = "inventory-service", fallbackMethod = "checkAvailabilityFallback")
    @Retry(name = "inventory-service")
    public Map<Long, Map<String, Object>> checkAvailability(List<Map<String, Object>> items) {
        log.debug("Checking inventory availability for {} items", items.size());
        
        String url = inventoryServiceUrl + "/api/inventory/check-multiple";
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Service", "transaction-service");
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

        HttpEntity<List<Map<String, Object>>> entity = new HttpEntity<>(items, headers);

        ResponseEntity<Map<Long, Map<String, Object>>> response = restTemplate.exchange(
            url,
            HttpMethod.POST,
            entity,
            new ParameterizedTypeReference<Map<Long, Map<String, Object>>>() {}
        );

        log.debug("Inventory check successful for {} items", items.size());
        return response.getBody();
    }

    /**
     * Fallback method when inventory service is unavailable.
     * Returns unavailable status for all items to fail-safe.
     * 
     * @param items List of items that were being checked
     * @param ex Exception that triggered the fallback
     * @return Map indicating all items are unavailable
     */
    private Map<Long, Map<String, Object>> checkAvailabilityFallback(
            List<Map<String, Object>> items, 
            Exception ex) {
        
        log.error("Circuit breaker activated for inventory check. Error: {}", ex.getMessage());
        log.warn("Inventory service unavailable - marking all items as unavailable");

        // Fail-safe: Return unavailable for all items
        // This prevents selling products when we can't verify stock
        return items.stream()
            .collect(java.util.stream.Collectors.toMap(
                item -> ((Number) item.get("productId")).longValue(),
                item -> Map.of(
                    "available", false,
                    "reason", "Inventory service temporarily unavailable",
                    "fallback", true
                )
            ));
    }

    /**
     * Updates stock levels after a successful purchase.
     * 
     * @param productId Product ID to update
     * @param quantity Quantity to deduct
     */
    @CircuitBreaker(name = "inventory-service", fallbackMethod = "updateStockFallback")
    @Retry(name = "inventory-service")
    public void updateStock(Long productId, Integer quantity) {
        log.debug("Updating stock for product {} - deducting {} units", productId, quantity);
        
        String url = inventoryServiceUrl + "/api/inventory/products/" + productId + "/stock/deduct";
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Service", "transaction-service");

        Map<String, Object> body = Map.of("quantity", quantity);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        restTemplate.exchange(url, HttpMethod.PUT, entity, Void.class);
        log.debug("Stock updated successfully for product {}", productId);
    }

    /**
     * Fallback for stock update failures.
     * Logs the failure for manual reconciliation.
     */
    private void updateStockFallback(Long productId, Integer quantity, Exception ex) {
        log.error("Failed to update stock for product {}. Quantity: {}. Error: {}", 
                  productId, quantity, ex.getMessage());
        log.error("CRITICAL: Manual stock reconciliation required for product {}", productId);
        // In production, this should trigger an alert/notification
    }

    /**
     * Gets the price of a product.
     * 
     * @param productId Product ID
     * @return Product price, or 0.0 if unavailable
     */
    @CircuitBreaker(name = "inventory-service", fallbackMethod = "getProductPriceFallback")
    @Retry(name = "inventory-service")
    public java.math.BigDecimal getProductPrice(Long productId) {
        log.debug("Getting price for product {}", productId);
        
        String url = inventoryServiceUrl + "/api/inventory/products/" + productId;
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Service", "transaction-service");
        HttpEntity<?> entity = new HttpEntity<>(headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, HttpMethod.GET, entity, 
            new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {}
        );
        
        Map<String, Object> body = response.getBody();
        if (body != null) {
            Double price = (Double) body.get("price");
            java.math.BigDecimal result = java.math.BigDecimal.valueOf(price != null ? price : 0.0);
            log.debug("Retrieved price for product {}: {}", productId, result);
            return result;
        }
        
        log.warn("No response body when getting price for product {}", productId);
        return java.math.BigDecimal.ZERO;
    }

    /**
     * Fallback for product price retrieval failures.
     */
    @SuppressWarnings("unused")
    private java.math.BigDecimal getProductPriceFallback(Long productId, Exception ex) {
        log.error("Failed to get price for product {}. Error: {}. Returning 0.0", 
                  productId, ex.getMessage());
        return java.math.BigDecimal.ZERO;
    }
}
