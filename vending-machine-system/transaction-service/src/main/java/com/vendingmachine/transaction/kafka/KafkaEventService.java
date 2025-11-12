package com.vendingmachine.transaction.kafka;

import com.vendingmachine.common.event.TransactionEvent;
import com.vendingmachine.common.event.DomainEvent;
import com.vendingmachine.common.event.payload.TransactionPayload;
import com.vendingmachine.common.kafka.UnifiedEventPublisher;
import com.vendingmachine.common.kafka.UnifiedTopicConfig;
import com.vendingmachine.common.aop.annotation.Auditable;
import com.vendingmachine.common.aop.annotation.ExecutionTime;
import com.vendingmachine.common.util.CorrelationIdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaEventService {

    private final KafkaTemplate<String, TransactionEvent> kafkaTemplate;
    private final UnifiedEventPublisher unifiedEventPublisher;

    @Auditable(operation = "PUBLISH_TRANSACTION_EVENT", entityType = "TransactionEvent", logParameters = true)
    @ExecutionTime(operation = "Publish Transaction Event", warningThreshold = 1000, detailed = true)
    public void publishTransactionEvent(TransactionEvent event) {
        try {
            // Get current correlation ID
            String correlationId = CorrelationIdUtil.getCorrelationId();
            
            // ===== LEGACY PUBLISHING (Phase 1 of migration) =====
            publishToLegacyTopic(event, correlationId);
            
            // ===== UNIFIED PUBLISHING (Phase 2 of migration) =====
            publishToUnifiedTopic(event, correlationId);
            
            log.info("Published transaction event to BOTH legacy and unified topics: {} for transaction {} with correlation ID: {}", 
                    event.getEventId(), event.getTransactionId(), correlationId);
        } catch (Exception e) {
            log.error("Failed to publish transaction event: {}", event.getEventId(), e);
            throw new RuntimeException("Failed to publish transaction event", e);
        }
    }
    
    /**
     * Publish to legacy topic (existing behavior)
     */
    private void publishToLegacyTopic(TransactionEvent event, String correlationId) {
        try {
            Message<TransactionEvent> message = MessageBuilder
                .withPayload(event)
                .setHeader(KafkaHeaders.TOPIC, "transaction-events")
                .setHeader(KafkaHeaders.KEY, event.getEventId())
                .setHeader("X-Correlation-ID", correlationId)
                .build();
            
            kafkaTemplate.send(message);
            log.debug("Published to legacy topic: transaction-events");
        } catch (Exception e) {
            log.error("Failed to publish to legacy topic", e);
            throw e;
        }
    }
    
    /**
     * Publish to unified topic (new behavior)
     */
    private void publishToUnifiedTopic(TransactionEvent event, String correlationId) {
        try {
            // Convert TransactionEvent to DomainEvent
            DomainEvent domainEvent = convertToDomainEvent(event, correlationId);
            
            // Publish using UnifiedEventPublisher
            unifiedEventPublisher.publishEvent(domainEvent);
            log.debug("Published to unified topic: vending-machine-domain-events");
        } catch (Exception e) {
            log.warn("Failed to publish to unified topic (non-critical during migration phase)", e);
            // Don't throw - unified publishing is supplementary during migration
        }
    }
    
    /**
     * Convert legacy TransactionEvent to unified DomainEvent
     */
    private DomainEvent convertToDomainEvent(TransactionEvent event, String correlationId) {
        // Create payload from TransactionEvent data (legacy event has limited fields)
        TransactionPayload payload = TransactionPayload.builder()
                .transactionId(event.getTransactionId())
                .userId(null) // Not available in legacy event
                .productId(null) // Not available in legacy event
                .quantity(null) // Not available in legacy event
                .totalAmount(event.getTotalAmount() != null ? 
                    java.math.BigDecimal.valueOf(event.getTotalAmount()) : null)
                .status(event.getStatus()) // Use status from event
                .timestamp(event.getTimestamp())
                .build();
        
        // Determine event type based on TransactionEvent status
        String eventType;
        switch (event.getStatus() != null ? event.getStatus().toUpperCase() : "UNKNOWN") {
            case "CREATED":
            case "PENDING":
                eventType = "TRANSACTION_CREATED";
                break;
            case "COMPLETED":
            case "SUCCESS":
                eventType = "TRANSACTION_COMPLETED";
                break;
            case "FAILED":
            case "ERROR":
                eventType = "TRANSACTION_FAILED";
                break;
            default:
                eventType = "TRANSACTION_STATUS_UPDATED";
                break;
        }
        
        return DomainEvent.builder()
                .eventId(event.getEventId())
                .eventType(eventType)
                .aggregateId(event.getTransactionId().toString())
                .aggregateType("TRANSACTION")
                .source(UnifiedTopicConfig.TRANSACTION_SERVICE)
                .correlationId(correlationId)
                .timestamp(System.currentTimeMillis())
                .payload(serializePayload(payload))
                .version("1.0")
                .build();
    }
    
    /**
     * Serialize payload to JSON
     */
    private String serializePayload(Object payload) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload);
        } catch (Exception e) {
            log.error("Failed to serialize payload", e);
            return "{}";
        }
    }
}