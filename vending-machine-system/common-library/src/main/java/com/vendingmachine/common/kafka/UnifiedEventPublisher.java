package com.vendingmachine.common.kafka;

import com.vendingmachine.common.event.DomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Unified Event Publisher for Phase 2 Kafka Optimization
 * 
 * Replaces individual service event publishers with a single, optimized publisher
 * that routes all events through the unified domain events topic.
 * 
 * Features:
 * - Automatic event type classification and routing
 * - Correlation ID propagation for event tracing
 * - Partition key strategy for load balancing
 * - Comprehensive logging and metrics
 * - Asynchronous publishing with callback handling
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UnifiedEventPublisher {

    private final KafkaTemplate<String, DomainEvent> kafkaTemplate;

    /**
     * Publishes a domain event to the unified topic with automatic routing
     * 
     * @param event The domain event to publish
     * @return CompletableFuture for async handling
     */
    public CompletableFuture<SendResult<String, DomainEvent>> publishEvent(DomainEvent event) {
        try {
            String partitionKey = generatePartitionKey(event);
            
            log.info("Publishing unified event: type={}, source={}, correlationId={}, eventId={}", 
                    event.getEventType(), event.getSource(), event.getCorrelationId(), event.getEventId());

            // Send message asynchronously
            CompletableFuture<SendResult<String, DomainEvent>> future = kafkaTemplate.send(
                UnifiedTopicConfig.UNIFIED_DOMAIN_EVENTS_TOPIC,
                partitionKey,
                event
            );
            
            // Add success callback
            future.whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.error("Failed to publish event: type={}, eventId={}, error={}", 
                            event.getEventType(), event.getEventId(), throwable.getMessage());
                } else {
                    log.debug("Successfully published event: type={}, eventId={}, partition={}, offset={}", 
                            event.getEventType(), event.getEventId(), 
                            result.getRecordMetadata().partition(), 
                            result.getRecordMetadata().offset());
                }
            });
            
            return future;
            
        } catch (Exception e) {
            log.error("Error preparing event for publishing: type={}, eventId={}", 
                    event.getEventType(), event.getEventId(), e);
            
            CompletableFuture<SendResult<String, DomainEvent>> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(e);
            return failedFuture;
        }
    }

    /**
     * Synchronous event publishing for critical operations
     * Use sparingly - prefer async publishing for better performance
     */
    public SendResult<String, DomainEvent> publishEventSync(DomainEvent event) {
        try {
            return publishEvent(event).get();
        } catch (Exception e) {
            log.error("Synchronous event publishing failed: type={}, eventId={}", 
                    event.getEventType(), event.getEventId(), e);
            throw new RuntimeException("Failed to publish event synchronously", e);
        }
    }

    /**
     * Generates partition key for load balancing
     * Strategy: Use correlation ID for related events to maintain order
     */
    private String generatePartitionKey(DomainEvent event) {
        // Use correlation ID to keep related events in same partition for ordering
        if (event.getCorrelationId() != null) {
            return event.getCorrelationId();
        }
        
        // Fallback to event type for general load balancing
        return event.getEventType();
    }

    /**
     * Publishes event with custom partition key
     * Useful for specific ordering requirements
     */
    public CompletableFuture<SendResult<String, DomainEvent>> publishEventWithKey(
            DomainEvent event, String customPartitionKey) {
        
        log.info("Publishing event with custom key: type={}, key={}, eventId={}", 
                event.getEventType(), customPartitionKey, event.getEventId());

        return kafkaTemplate.send(
            UnifiedTopicConfig.UNIFIED_DOMAIN_EVENTS_TOPIC,
            customPartitionKey,
            event
        );
    }
}