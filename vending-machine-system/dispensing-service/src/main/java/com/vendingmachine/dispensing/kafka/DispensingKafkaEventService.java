package com.vendingmachine.dispensing.kafka;

import com.vendingmachine.common.event.DomainEvent;
import com.vendingmachine.common.event.payload.DispensingPayload;
import com.vendingmachine.common.kafka.UnifiedEventPublisher;
import com.vendingmachine.common.aop.annotation.Auditable;
import com.vendingmachine.common.aop.annotation.ExecutionTime;
import com.vendingmachine.common.util.CorrelationIdUtil;
import com.vendingmachine.dispensing.dispensing.DispensingOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DispensingKafkaEventService {

    private final UnifiedEventPublisher unifiedEventPublisher;

    /**
     * Publishes dispensing events with complete data to unified domain events topic
     */
    @Auditable(operation = "PUBLISH_DISPENSING_EVENT", entityType = "DispensingEvent", logParameters = true)
    @ExecutionTime(operation = "Publish Dispensing Event", warningThreshold = 1000, detailed = true)
    public void publishDispensingEventWithCompleteData(DispensingOperation dispensing, String eventStatus) {
        try {
            log.debug("Creating dispensing event payload for dispensing ID: {}, transaction ID: {}, product ID: {}, status: {}", 
                     dispensing.getId(), dispensing.getTransactionId(), dispensing.getProductId(), eventStatus);
            
            // Create complete payload with dispensing data
            DispensingPayload payload = DispensingPayload.builder()
                .dispensingId(dispensing.getId())
                .transactionId(dispensing.getTransactionId()) // ⚠️ CRITICAL FIX: Add missing transactionId
                .productId(dispensing.getProductId())
                .requestedQuantity(dispensing.getQuantity())
                .dispensedQuantity(eventStatus.equals("SUCCESS") ? dispensing.getQuantity() : 0)
                .status(eventStatus)
                .failureReason(eventStatus.equals("FAILED") ? dispensing.getErrorMessage() : null)
                .timestamp(System.currentTimeMillis())
                .build();

            log.debug("Created dispensing payload: {}", payload);

            // Create unified domain event
            DomainEvent domainEvent = DomainEvent.builder()
                .eventId("disp-" + eventStatus.toLowerCase() + "-" + dispensing.getId() + "-" + System.currentTimeMillis())
                .eventType("DISPENSING_" + eventStatus)
                .aggregateId(dispensing.getId().toString())
                .aggregateType("DISPENSING")
                .source("dispensing-service")
                .correlationId(CorrelationIdUtil.getCorrelationId())
                .timestamp(System.currentTimeMillis())
                .payload(serializePayload(payload))
                .version("1.0")
                .build();

            log.debug("Created domain event: eventId={}, eventType={}, payload length={}", 
                     domainEvent.getEventId(), domainEvent.getEventType(), domainEvent.getPayload().length());

            // Publish to unified topic only
            unifiedEventPublisher.publishEvent(domainEvent);
            
            log.info("Successfully published dispensing event to unified topic: {} for dispensing {}", 
                domainEvent.getEventId(), dispensing.getId());

        } catch (Exception e) {
            log.error("Error creating unified dispensing event for dispensing {}: {}", 
                dispensing.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to publish unified dispensing event", e);
        }
    }

    /**
     * Serialize payload to JSON
     */
    private String serializePayload(Object payload) {
        try {
            String jsonPayload = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload);
            log.debug("Serialized payload to JSON: {}", jsonPayload);
            return jsonPayload;
        } catch (Exception e) {
            log.error("Failed to serialize payload: {}", payload, e);
            log.error("Serialization error details: {}", e.getMessage());
            return "{}";
        }
    }
}