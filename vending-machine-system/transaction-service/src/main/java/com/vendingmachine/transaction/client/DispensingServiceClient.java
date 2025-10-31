package com.vendingmachine.transaction.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Client for communicating with Dispensing Service.
 * Implements Circuit Breaker and Retry patterns for fault tolerance.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DispensingServiceClient {

    private final RestTemplate restTemplate;

    @Value("${services.dispensing.url:http://localhost:8084}")
    private String dispensingServiceUrl;

    /**
     * Triggers product dispensing through the dispensing service.
     * Uses Circuit Breaker to prevent cascading failures.
     * 
     * @param transactionId Transaction ID
     * @param items List of items to dispense
     * @return Dispensing response with status
     */
    @CircuitBreaker(name = "dispensing-service", fallbackMethod = "dispenseItemsFallback")
    @Retry(name = "dispensing-service")
    public Map<String, Object> dispenseItems(String transactionId, List<Map<String, Object>> items) {
        log.debug("Initiating dispensing for transaction {} - {} items", transactionId, items.size());
        
        String url = dispensingServiceUrl + "/api/dispensing/dispense";
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Service", "transaction-service");
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

        Map<String, Object> dispenseRequest = Map.of(
            "transactionId", transactionId,
            "items", items
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(dispenseRequest, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url,
            HttpMethod.POST,
            entity,
            new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {}
        );

        log.debug("Dispensing completed for transaction {}", transactionId);
        return response.getBody();
    }

    /**
     * Fallback method when dispensing service is unavailable.
     * Returns failed status to trigger compensation (refund).
     * 
     * @param transactionId Transaction ID
     * @param items Items that should have been dispensed
     * @param ex Exception that triggered the fallback
     * @return Map indicating dispensing failed
     */
    private Map<String, Object> dispenseItemsFallback(
            String transactionId, 
            List<Map<String, Object>> items, 
            Exception ex) {
        
        log.error("Circuit breaker activated for dispensing. Transaction: {}, Error: {}", 
                  transactionId, ex.getMessage());
        log.warn("Dispensing service unavailable - marking as failed for transaction {}", transactionId);
        log.error("CRITICAL: Compensation (refund) required for transaction {}", transactionId);

        // Fail-safe: Return failed status to trigger refund compensation
        // Customer has paid but items couldn't be dispensed
        return Map.of(
            "success", false,
            "status", "FAILED",
            "transactionId", transactionId,
            "dispensedItems", List.of(), // No items dispensed
            "failedItems", items, // All items failed
            "reason", "Dispensing service temporarily unavailable",
            "fallback", true,
            "requiresCompensation", true // Flag for refund processing
        );
    }

    /**
     * Checks the status of a dispensing operation.
     * 
     * @param transactionId Transaction ID to check
     * @return Dispensing status
     */
    @CircuitBreaker(name = "dispensing-service", fallbackMethod = "getDispensingStatusFallback")
    public Map<String, Object> getDispensingStatus(String transactionId) {
        log.debug("Checking dispensing status for transaction {}", transactionId);
        
        String url = dispensingServiceUrl + "/api/dispensing/status/" + transactionId;
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Service", "transaction-service");

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url,
            HttpMethod.GET,
            entity,
            new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {}
        );

        return response.getBody();
    }

    /**
     * Fallback for status check failures.
     */
    @SuppressWarnings("unused")
    private Map<String, Object> getDispensingStatusFallback(String transactionId, Exception ex) {
        log.error("Failed to get dispensing status for transaction {}. Error: {}", 
                  transactionId, ex.getMessage());
        
        return Map.of(
            "status", "UNKNOWN",
            "transactionId", transactionId,
            "reason", "Dispensing service unavailable",
            "fallback", true
        );
    }
}
