package com.vendingmachine.transaction.client;

import com.vendingmachine.transaction.transaction.dto.PaymentInfo;
import com.vendingmachine.transaction.transaction.dto.PaymentMethod;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PaymentServiceClient circuit breaker functionality.
 * Tests circuit opening, fallback execution, and retry behavior.
 */
@SpringBootTest(classes = {PaymentServiceClient.class})
@TestPropertySource(properties = {
    "services.payment.url=http://localhost:8082",
    // Circuit breaker settings for faster testing
    "resilience4j.circuitbreaker.instances.payment-service.sliding-window-size=5",
    "resilience4j.circuitbreaker.instances.payment-service.failure-rate-threshold=50",
    "resilience4j.circuitbreaker.instances.payment-service.wait-duration-in-open-state=15s",
    "resilience4j.circuitbreaker.instances.payment-service.permitted-number-of-calls-in-half-open-state=2",
    // Retry settings
    "resilience4j.retry.instances.payment-service.max-attempts=2",
    "resilience4j.retry.instances.payment-service.wait-duration=500ms",
    "resilience4j.retry.instances.payment-service.enable-exponential-backoff=true",
    "resilience4j.retry.instances.payment-service.exponential-backoff-multiplier=2"
})
class PaymentServiceClientTest {

    @Autowired
    private PaymentServiceClient paymentClient;

    @MockBean
    private RestTemplate restTemplate;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        // Reset circuit breaker to CLOSED state before each test
        circuitBreaker = circuitBreakerRegistry.circuitBreaker("payment-service");
        circuitBreaker.reset();
        
        // Reset mock interactions
        reset(restTemplate);
    }

    @Test
    void testProcessPayment_CardPayment_Success() {
        // Given
        String transactionId = "txn-123";
        PaymentInfo paymentInfo = PaymentInfo.builder()
            .paymentMethod(PaymentMethod.CREDIT_CARD)
            .cardNumber("4532-1234-5678-9010")
            .cardHolderName("John Doe")
            .expiryDate("12/25")
            .build();
        BigDecimal amount = BigDecimal.valueOf(15.50);
        
        Map<String, Object> mockResponse = Map.of(
            "success", true,
            "status", "SUCCESS",
            "transactionId", transactionId
        );
        
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(ResponseEntity.ok(mockResponse));

        // When
        Map<String, Object> result = paymentClient.processPayment(transactionId, paymentInfo, amount);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.get("success")).isEqualTo(true);
        assertThat(result.get("status")).isEqualTo("SUCCESS");
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        
        verify(restTemplate, times(1)).exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        );
    }

    @Test
    void testProcessPayment_CashPayment_Success() {
        // Given
        String transactionId = "txn-124";
        PaymentInfo paymentInfo = PaymentInfo.builder()
            .paymentMethod(PaymentMethod.CASH)
            .paidAmount(BigDecimal.valueOf(20.00))
            .build();
        BigDecimal amount = BigDecimal.valueOf(15.50);
        
        Map<String, Object> mockResponse = Map.of(
            "success", true,
            "status", "SUCCESS",
            "change", 4.50
        );
        
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(ResponseEntity.ok(mockResponse));

        // When
        Map<String, Object> result = paymentClient.processPayment(transactionId, paymentInfo, amount);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.get("success")).isEqualTo(true);
        assertThat(result.get("status")).isEqualTo("SUCCESS");
        
        verify(restTemplate, times(1)).exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        );
    }

    @Test
    void testProcessPayment_RetryOnFailure() {
        // Given
        String transactionId = "txn-125";
        PaymentInfo paymentInfo = PaymentInfo.builder()
            .paymentMethod(PaymentMethod.CREDIT_CARD)
            .cardNumber("4532-1234-5678-9010")
            .cardHolderName("John Doe")
            .expiryDate("12/25")
            .build();
        BigDecimal amount = BigDecimal.valueOf(15.50);
        
        Map<String, Object> mockResponse = Map.of(
            "success", true,
            "status", "SUCCESS"
        );

        // Fail once, then succeed (max-attempts=2)
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        ))
            .thenThrow(new RuntimeException("Network timeout"))
            .thenReturn(ResponseEntity.ok(mockResponse));

        // When
        Map<String, Object> result = paymentClient.processPayment(transactionId, paymentInfo, amount);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.get("success")).isEqualTo(true);
        
        // Verify retry happened (1 failure + 1 success = 2 total calls)
        verify(restTemplate, times(2)).exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        );
    }

    @Test
    void testProcessPayment_FallbackAfterRetriesExhausted() {
        // Given
        String transactionId = "txn-126";
        PaymentInfo paymentInfo = PaymentInfo.builder()
            .paymentMethod(PaymentMethod.CREDIT_CARD)
            .cardNumber("4532-1234-5678-9010")
            .cardHolderName("John Doe")
            .expiryDate("12/25")
            .build();
        BigDecimal amount = BigDecimal.valueOf(15.50);

        // All retry attempts fail
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenThrow(new RuntimeException("Payment service unavailable"));

        // When
        Map<String, Object> result = paymentClient.processPayment(transactionId, paymentInfo, amount);

        // Then - fallback returns failure
        assertThat(result).isNotNull();
        assertThat(result.get("success")).isEqualTo(false);
        assertThat(result.get("fallback")).isEqualTo(true);
        assertThat(result.get("message")).isEqualTo("Payment service temporarily unavailable");
        
        // Verify all retry attempts were made (2 attempts)
        verify(restTemplate, times(2)).exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        );
    }

    @Test
    void testProcessPayment_CircuitOpensAfterFailureThreshold() {
        // Given
        String transactionId = "txn-127";
        PaymentInfo paymentInfo = PaymentInfo.builder()
            .paymentMethod(PaymentMethod.CREDIT_CARD)
            .cardNumber("4532-1234-5678-9010")
            .cardHolderName("John Doe")
            .expiryDate("12/25")
            .build();
        BigDecimal amount = BigDecimal.valueOf(15.50);

        // Configure to always fail
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenThrow(new RuntimeException("Payment service unavailable"));

        // When - make enough calls to open circuit
        for (int i = 0; i < 3; i++) {
            paymentClient.processPayment(transactionId, paymentInfo, amount);
        }

        // Then - circuit should be open
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        
        // Additional calls should use fallback without calling RestTemplate
        int callCountBeforeCircuitOpen = mockingDetails(restTemplate).getInvocations().size();
        
        Map<String, Object> result = paymentClient.processPayment(transactionId, paymentInfo, amount);
        
        // Verify fallback response
        assertThat(result.get("success")).isEqualTo(false);
        assertThat(result.get("fallback")).isEqualTo(true);
        
        // RestTemplate should NOT be called when circuit is open
        int callCountAfterCircuitOpen = mockingDetails(restTemplate).getInvocations().size();
        assertThat(callCountAfterCircuitOpen).isEqualTo(callCountBeforeCircuitOpen);
    }

    @Test
    void testRefundPayment_Success() {
        // Given
        String transactionId = "txn-128";
        BigDecimal amount = BigDecimal.valueOf(15.50);
        
        Map<String, Object> mockResponse = Map.of(
            "success", true,
            "refundId", "ref-123"
        );
        
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(ResponseEntity.ok(mockResponse));

        // When
        Map<String, Object> result = paymentClient.refundPayment(transactionId, amount);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.get("success")).isEqualTo(true);
        
        verify(restTemplate, times(1)).exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        );
    }

    @Test
    void testRefundPayment_FallbackOnFailure() {
        // Given
        String transactionId = "txn-129";
        BigDecimal amount = BigDecimal.valueOf(15.50);
        
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenThrow(new RuntimeException("Refund service unavailable"));

        // When
        Map<String, Object> result = paymentClient.refundPayment(transactionId, amount);

        // Then - fallback returns failure
        assertThat(result).isNotNull();
        assertThat(result.get("success")).isEqualTo(false);
        assertThat(result.get("fallback")).isEqualTo(true);
        assertThat(result.get("message")).isEqualTo("Refund service temporarily unavailable");
        
        // Verify retry attempts (2 attempts for payment-service)
        verify(restTemplate, times(2)).exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        );
    }
}
