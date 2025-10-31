package com.vendingmachine.transaction.client;

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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DispensingServiceClient circuit breaker functionality.
 * Tests circuit opening, fallback execution, and retry behavior.
 */
@SpringBootTest(classes = {DispensingServiceClient.class})
@TestPropertySource(properties = {
    "services.dispensing.url=http://localhost:8084",
    // Circuit breaker settings for faster testing
    "resilience4j.circuitbreaker.instances.dispensing-service.sliding-window-size=5",
    "resilience4j.circuitbreaker.instances.dispensing-service.failure-rate-threshold=50",
    "resilience4j.circuitbreaker.instances.dispensing-service.wait-duration-in-open-state=10s",
    "resilience4j.circuitbreaker.instances.dispensing-service.permitted-number-of-calls-in-half-open-state=3",
    // Retry settings
    "resilience4j.retry.instances.dispensing-service.max-attempts=3",
    "resilience4j.retry.instances.dispensing-service.wait-duration=500ms",
    "resilience4j.retry.instances.dispensing-service.enable-exponential-backoff=true",
    "resilience4j.retry.instances.dispensing-service.exponential-backoff-multiplier=2"
})
class DispensingServiceClientTest {

    @Autowired
    private DispensingServiceClient dispensingClient;

    @MockBean
    private RestTemplate restTemplate;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        // Reset circuit breaker to CLOSED state before each test
        circuitBreaker = circuitBreakerRegistry.circuitBreaker("dispensing-service");
        circuitBreaker.reset();
        
        // Reset mock interactions
        reset(restTemplate);
    }

    @Test
    void testDispenseItems_Success() {
        // Given
        String transactionId = "txn-123";
        List<Map<String, Object>> items = List.of(
            Map.of("productId", 1L, "quantity", 2),
            Map.of("productId", 2L, "quantity", 1)
        );
        
        Map<String, Object> mockResponse = Map.of(
            "success", true,
            "status", "DISPENSED",
            "transactionId", transactionId
        );
        
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(ResponseEntity.ok(mockResponse));

        // When
        Map<String, Object> result = dispensingClient.dispenseItems(transactionId, items);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.get("success")).isEqualTo(true);
        assertThat(result.get("status")).isEqualTo("DISPENSED");
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        
        verify(restTemplate, times(1)).exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        );
    }

    @Test
    void testDispenseItems_RetryOnFailure() {
        // Given
        String transactionId = "txn-124";
        List<Map<String, Object>> items = List.of(
            Map.of("productId", 1L, "quantity", 2)
        );
        
        Map<String, Object> mockResponse = Map.of(
            "success", true,
            "status", "DISPENSED"
        );

        // Fail twice, then succeed (max-attempts=3)
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        ))
            .thenThrow(new RuntimeException("Hardware communication error"))
            .thenThrow(new RuntimeException("Hardware communication error"))
            .thenReturn(ResponseEntity.ok(mockResponse));

        // When
        Map<String, Object> result = dispensingClient.dispenseItems(transactionId, items);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.get("success")).isEqualTo(true);
        
        // Verify retry happened (2 failures + 1 success = 3 total calls)
        verify(restTemplate, times(3)).exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        );
    }

    @Test
    void testDispenseItems_FallbackAfterRetriesExhausted() {
        // Given
        String transactionId = "txn-125";
        List<Map<String, Object>> items = List.of(
            Map.of("productId", 1L, "quantity", 2)
        );

        // All retry attempts fail
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenThrow(new RuntimeException("Dispensing service unavailable"));

        // When
        Map<String, Object> result = dispensingClient.dispenseItems(transactionId, items);

        // Then - fallback returns failure with compensation flag
        assertThat(result).isNotNull();
        assertThat(result.get("success")).isEqualTo(false);
        assertThat(result.get("fallback")).isEqualTo(true);
        assertThat(result.get("requiresCompensation")).isEqualTo(true);
        assertThat(result.get("message")).isEqualTo("Dispensing service temporarily unavailable - compensation required");
        
        // Verify all retry attempts were made (3 attempts)
        verify(restTemplate, times(3)).exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        );
    }

    @Test
    void testDispenseItems_CircuitOpensAfterFailureThreshold() {
        // Given
        String transactionId = "txn-126";
        List<Map<String, Object>> items = List.of(
            Map.of("productId", 1L, "quantity", 2)
        );

        // Configure to always fail
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenThrow(new RuntimeException("Dispensing service unavailable"));

        // When - make enough calls to open circuit
        for (int i = 0; i < 3; i++) {
            dispensingClient.dispenseItems(transactionId, items);
        }

        // Then - circuit should be open
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        
        // Additional calls should use fallback without calling RestTemplate
        int callCountBeforeCircuitOpen = mockingDetails(restTemplate).getInvocations().size();
        
        Map<String, Object> result = dispensingClient.dispenseItems(transactionId, items);
        
        // Verify fallback response
        assertThat(result.get("success")).isEqualTo(false);
        assertThat(result.get("fallback")).isEqualTo(true);
        assertThat(result.get("requiresCompensation")).isEqualTo(true);
        
        // RestTemplate should NOT be called when circuit is open
        int callCountAfterCircuitOpen = mockingDetails(restTemplate).getInvocations().size();
        assertThat(callCountAfterCircuitOpen).isEqualTo(callCountBeforeCircuitOpen);
    }

    @Test
    void testGetDispensingStatus_Success() {
        // Given
        String transactionId = "txn-127";
        
        Map<String, Object> mockResponse = Map.of(
            "status", "COMPLETED",
            "transactionId", transactionId
        );
        
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(ResponseEntity.ok(mockResponse));

        // When
        Map<String, Object> result = dispensingClient.getDispensingStatus(transactionId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.get("status")).isEqualTo("COMPLETED");
        
        verify(restTemplate, times(1)).exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        );
    }

    @Test
    void testGetDispensingStatus_FallbackOnFailure() {
        // Given
        String transactionId = "txn-128";
        
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenThrow(new RuntimeException("Service unavailable"));

        // When
        Map<String, Object> result = dispensingClient.getDispensingStatus(transactionId);

        // Then - fallback returns UNKNOWN status
        assertThat(result).isNotNull();
        assertThat(result.get("status")).isEqualTo("UNKNOWN");
        assertThat(result.get("fallback")).isEqualTo(true);
        assertThat(result.get("message")).isEqualTo("Unable to retrieve dispensing status");
        
        // Verify retry attempts (3 attempts for dispensing-service)
        verify(restTemplate, times(3)).exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        );
    }
}
