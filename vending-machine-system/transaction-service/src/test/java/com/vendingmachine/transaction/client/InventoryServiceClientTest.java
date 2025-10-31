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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for InventoryServiceClient circuit breaker functionality.
 * Tests circuit opening, fallback execution, and retry behavior.
 */
@SpringBootTest(classes = {InventoryServiceClient.class})
@TestPropertySource(properties = {
    "services.inventory.url=http://localhost:8081",
    // Circuit breaker settings for faster testing
    "resilience4j.circuitbreaker.instances.inventory-service.sliding-window-size=5",
    "resilience4j.circuitbreaker.instances.inventory-service.failure-rate-threshold=50",
    "resilience4j.circuitbreaker.instances.inventory-service.wait-duration-in-open-state=10s",
    "resilience4j.circuitbreaker.instances.inventory-service.permitted-number-of-calls-in-half-open-state=3",
    // Retry settings
    "resilience4j.retry.instances.inventory-service.max-attempts=3",
    "resilience4j.retry.instances.inventory-service.wait-duration=100ms",
    "resilience4j.retry.instances.inventory-service.enable-exponential-backoff=true",
    "resilience4j.retry.instances.inventory-service.exponential-backoff-multiplier=2"
})
class InventoryServiceClientTest {

    @Autowired
    private InventoryServiceClient inventoryClient;

    @MockBean
    private RestTemplate restTemplate;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        // Reset circuit breaker to CLOSED state before each test
        circuitBreaker = circuitBreakerRegistry.circuitBreaker("inventory-service");
        circuitBreaker.reset();
        
        // Reset mock interactions
        reset(restTemplate);
    }

    @Test
    void testCheckAvailability_Success() {
        // Given
        List<Map<String, Object>> items = List.of(
            Map.of("productId", 1L, "quantity", 2)
        );
        
        Map<String, Object> mockResponse = Map.of(
            "1", Map.of(
                "available", true,
                "currentStock", 10
            )
        );
        
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(ResponseEntity.ok(mockResponse));

        // When
        Map<Long, Map<String, Object>> result = inventoryClient.checkAvailability(items);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).containsKey(1L);
        assertThat(result.get(1L).get("available")).isEqualTo(true);
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        
        verify(restTemplate, times(1)).exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        );
    }

    @Test
    void testCheckAvailability_RetryOnFailure() {
        // Given
        List<Map<String, Object>> items = List.of(
            Map.of("productId", 1L, "quantity", 2)
        );
        
        Map<String, Object> mockResponse = Map.of(
            "1", Map.of("available", true, "currentStock", 10)
        );

        // Fail twice, then succeed (testing retry with max-attempts=3)
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        ))
            .thenThrow(new RuntimeException("Connection timeout"))
            .thenThrow(new RuntimeException("Connection timeout"))
            .thenReturn(ResponseEntity.ok(mockResponse));

        // When
        Map<Long, Map<String, Object>> result = inventoryClient.checkAvailability(items);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).containsKey(1L);
        assertThat(result.get(1L).get("available")).isEqualTo(true);
        
        // Verify retry happened 3 times (2 failures + 1 success)
        verify(restTemplate, times(3)).exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        );
    }

    @Test
    void testCheckAvailability_FallbackAfterRetriesExhausted() {
        // Given
        List<Map<String, Object>> items = List.of(
            Map.of("productId", 1L, "quantity", 2),
            Map.of("productId", 2L, "quantity", 1)
        );

        // All retry attempts fail
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenThrow(new RuntimeException("Service unavailable"));

        // When
        Map<Long, Map<String, Object>> result = inventoryClient.checkAvailability(items);

        // Then - fallback returns unavailable for all items
        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);
        assertThat(result.get(1L).get("available")).isEqualTo(false);
        assertThat(result.get(1L).get("fallback")).isEqualTo(true);
        assertThat(result.get(1L).get("reason")).isEqualTo("Inventory service temporarily unavailable");
        assertThat(result.get(2L).get("available")).isEqualTo(false);
        assertThat(result.get(2L).get("fallback")).isEqualTo(true);
        
        // Verify all retry attempts were made (3 attempts)
        verify(restTemplate, times(3)).exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        );
    }

    @Test
    void testCheckAvailability_CircuitOpensAfterFailureThreshold() {
        // Given
        List<Map<String, Object>> items = List.of(
            Map.of("productId", 1L, "quantity", 2)
        );

        // Configure to always fail
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenThrow(new RuntimeException("Service unavailable"));

        // When - make enough calls to open circuit (sliding-window-size=5, failure-rate=50%)
        // Each call will retry 3 times, so we need at least 3 failed calls to reach 50% failure rate
        for (int i = 0; i < 3; i++) {
            inventoryClient.checkAvailability(items);
        }

        // Then - circuit should be open
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        
        // Additional calls should use fallback without calling RestTemplate
        int callCountBeforeCircuitOpen = mockingDetails(restTemplate).getInvocations().size();
        
        Map<Long, Map<String, Object>> result = inventoryClient.checkAvailability(items);
        
        // Verify fallback response
        assertThat(result.get(1L).get("available")).isEqualTo(false);
        assertThat(result.get(1L).get("fallback")).isEqualTo(true);
        
        // RestTemplate should NOT be called when circuit is open
        int callCountAfterCircuitOpen = mockingDetails(restTemplate).getInvocations().size();
        assertThat(callCountAfterCircuitOpen).isEqualTo(callCountBeforeCircuitOpen);
    }

    @Test
    void testGetProductPrice_Success() {
        // Given
        Long productId = 1L;
        Map<String, Object> mockResponse = Map.of("price", 5.99);
        
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(ResponseEntity.ok(mockResponse));

        // When
        BigDecimal result = inventoryClient.getProductPrice(productId);

        // Then
        assertThat(result).isEqualTo(BigDecimal.valueOf(5.99));
        verify(restTemplate, times(1)).exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        );
    }

    @Test
    void testGetProductPrice_FallbackReturnsZero() {
        // Given
        Long productId = 1L;
        
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenThrow(new RuntimeException("Service unavailable"));

        // When
        BigDecimal result = inventoryClient.getProductPrice(productId);

        // Then - fallback returns ZERO
        assertThat(result).isEqualTo(BigDecimal.ZERO);
        
        // Verify retry attempts (3 times)
        verify(restTemplate, times(3)).exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        );
    }

    @Test
    void testUpdateStock_Success() {
        // Given
        Long productId = 1L;
        Integer quantity = 2;
        
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.PUT),
            any(HttpEntity.class),
            eq(Void.class)
        )).thenReturn(ResponseEntity.ok().build());

        // When
        inventoryClient.updateStock(productId, quantity);

        // Then
        verify(restTemplate, times(1)).exchange(
            anyString(),
            eq(HttpMethod.PUT),
            any(HttpEntity.class),
            eq(Void.class)
        );
    }

    @Test
    void testUpdateStock_FallbackLogsError() {
        // Given
        Long productId = 1L;
        Integer quantity = 2;
        
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.PUT),
            any(HttpEntity.class),
            eq(Void.class)
        )).thenThrow(new RuntimeException("Service unavailable"));

        // When - should not throw exception, fallback handles it
        inventoryClient.updateStock(productId, quantity);

        // Then - verify retry attempts
        verify(restTemplate, times(3)).exchange(
            anyString(),
            eq(HttpMethod.PUT),
            any(HttpEntity.class),
            eq(Void.class)
        );
    }
}
