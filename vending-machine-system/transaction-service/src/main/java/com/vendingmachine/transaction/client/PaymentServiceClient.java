package com.vendingmachine.transaction.client;

import com.vendingmachine.transaction.transaction.dto.PaymentInfo;
import com.vendingmachine.transaction.transaction.dto.PaymentMethod;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
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

import java.math.BigDecimal;
import java.util.Map;

/**
 * Client for communicating with Payment Service.
 * Implements Circuit Breaker, Retry, and Bulkhead patterns for fault tolerance.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("unused")
public class PaymentServiceClient {

    private final RestTemplate restTemplate;

    @Value("${services.payment.url:http://localhost:8082}")
    private String paymentServiceUrl;

    /**
     * Processes a payment through the payment service.
     * Uses Circuit Breaker to prevent cascading failures.
     * Uses Bulkhead to limit concurrent payment processing.
     * 
     * @param transactionId Transaction ID
     * @param paymentInfo Payment information
     * @param amount Amount to charge
     * @return Payment response with status and details
     */
    @Bulkhead(name = "payment-service", fallbackMethod = "processPaymentFallback", type = Bulkhead.Type.SEMAPHORE)
    @CircuitBreaker(name = "payment-service", fallbackMethod = "processPaymentFallback")
    @Retry(name = "payment-service")
    public Map<String, Object> processPayment(String transactionId, PaymentInfo paymentInfo, BigDecimal amount) {
        log.debug("Processing payment for transaction {} - Amount: {}", transactionId, amount);
        
        String url = paymentServiceUrl + "/api/payment/process";
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Service", "transaction-service");
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

        // Create payment request
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
            url,
            HttpMethod.POST,
            entity,
            new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {}
        );

        log.debug("Payment processed successfully for transaction {}", transactionId);
        return response.getBody();
    }

    /**
     * Fallback method when payment service is unavailable or bulkhead is full.
     * Returns failed payment status to prevent incomplete transactions.
     * 
     * @param transactionId Transaction ID
     * @param paymentInfo Payment information
     * @param amount Amount
     * @param ex Exception that triggered the fallback
     * @return Map indicating payment failed
     */
    private Map<String, Object> processPaymentFallback(
            String transactionId, 
            PaymentInfo paymentInfo, 
            BigDecimal amount, 
            Exception ex) {
        
        if (ex.getClass().getName().contains("BulkheadFullException")) {
            log.error("Bulkhead full for payment service. Transaction: {}", transactionId);
            log.warn("Too many concurrent payment requests - rate limiting active");
        } else {
            log.error("Circuit breaker activated for payment processing. Transaction: {}, Error: {}", 
                      transactionId, ex.getMessage());
        }
        
        log.warn("Payment service unavailable - failing transaction {}", transactionId);

        // Fail-safe: Return failed payment status
        // This ensures we don't complete transactions when payment can't be verified
        return Map.of(
            "success", false,
            "status", "FAILED",
            "transactionId", transactionId,
            "reason", ex.getClass().getName().contains("BulkheadFullException")
                ? "Payment service at capacity - please retry"
                : "Payment service temporarily unavailable",
            "fallback", true,
            "amount", amount
        );
    }

    /**
     * Refunds a payment.
     * 
     * @param transactionId Transaction ID to refund
     * @param amount Amount to refund
     * @return Refund response
     */
    @Bulkhead(name = "payment-service", fallbackMethod = "refundPaymentFallback", type = Bulkhead.Type.SEMAPHORE)
    @CircuitBreaker(name = "payment-service", fallbackMethod = "refundPaymentFallback")
    @Retry(name = "payment-service")
    public Map<String, Object> refundPayment(String transactionId, BigDecimal amount) {
        log.debug("Processing refund for transaction {} - Amount: {}", transactionId, amount);
        
        String url = paymentServiceUrl + "/api/payment/refund";
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Service", "transaction-service");
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

        Map<String, Object> refundRequest = Map.of(
            "transactionId", transactionId,
            "amount", amount
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(refundRequest, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url,
            HttpMethod.POST,
            entity,
            new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {}
        );

        log.debug("Refund processed successfully for transaction {}", transactionId);
        return response.getBody();
    }

    /**
     * Fallback for refund failures.
     * Logs the failure for manual processing.
     */
    private Map<String, Object> refundPaymentFallback(
            String transactionId, 
            BigDecimal amount, 
            Exception ex) {
        
        if (ex.getClass().getName().contains("BulkheadFullException")) {
            log.error("Bulkhead full for payment refund service. Transaction: {}", transactionId);
            log.warn("Too many concurrent refund requests - rate limiting active");
        } else {
            log.error("Failed to process refund for transaction {}. Amount: {}. Error: {}", 
                      transactionId, amount, ex.getMessage());
        }
        
        log.error("CRITICAL: Manual refund processing required for transaction {}", transactionId);
        
        // Return failed status for manual intervention
        return Map.of(
            "success", false,
            "status", "FAILED",
            "transactionId", transactionId,
            "amount", amount,
            "reason", ex.getClass().getName().contains("BulkheadFullException")
                ? "Refund service at capacity - please retry"
                : "Refund service temporarily unavailable - manual processing required",
            "fallback", true
        );
    }
}
