package com.vendingmachine.inventory.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vendingmachine.common.event.DomainEvent;
import com.vendingmachine.common.event.payload.DispensingPayload;
import com.vendingmachine.common.aop.annotation.Auditable;
import com.vendingmachine.common.aop.annotation.ExecutionTime;
import com.vendingmachine.common.util.CorrelationIdUtil;
import com.vendingmachine.inventory.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class UnifiedEventConsumer {

    private final InventoryService inventoryService;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @KafkaListener(topics = "vending-machine-domain-events", groupId = "inventory-service-unified-group",
                   containerFactory = "unifiedEventKafkaListenerContainerFactory")
    @Transactional
    @Auditable(operation = "CONSUME_UNIFIED_EVENT", entityType = "DomainEvent", logParameters = true)
    @ExecutionTime(operation = "Process Unified Event", warningThreshold = 2000, detailed = true)
    public void consumeUnifiedEvent(@Payload DomainEvent event,
                                   @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key,
                                   @Header(value = "X-Correlation-ID", required = false) String correlationId,
                                   @Header(value = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic,
                                   @Header(value = KafkaHeaders.RECEIVED_PARTITION, required = false) Integer partition,
                                   @Header(value = KafkaHeaders.OFFSET, required = false) Long offset) {
        
        // Set correlation ID from Kafka header or domain event
        String finalCorrelationId = correlationId != null ? correlationId : event.getCorrelationId();
        CorrelationIdUtil.setCorrelationId(finalCorrelationId);
        
        try {
            log.info("Received unified event: {} from {} with type {}", 
                    event.getEventId(), event.getSource(), event.getEventType());

            // Route event based on type and source
            routeEvent(event, topic, partition, offset);
            
        } catch (Exception e) {
            log.error("Failed to process unified event: {} of type {}", event.getEventId(), event.getEventType(), e);
            throw new RuntimeException("Failed to process unified event", e);
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }

    /**
     * Smart routing based on event type and source
     */
    private void routeEvent(DomainEvent event, String topic, Integer partition, Long offset) {
        String eventType = event.getEventType();
        String source = event.getSource();
        
        try {
            switch (eventType) {
                // Dispensing Events - only process successful dispensing to reduce stock
                case "DISPENSING_SUCCESS":
                case "DISPENSING_COMPLETED":
                    if ("dispensing-service".equals(source)) {
                        handleDispensingEvent(event, topic, partition, offset);
                    }
                    break;
                    
                default:
                    log.debug("Ignoring event type: {} from source: {} (not relevant for inventory service)", eventType, source);
            }
        } catch (Exception e) {
            log.error("Failed to route event {} of type {}", event.getEventId(), eventType, e);
            throw e;
        }
    }

    /**
     * Handle dispensing events to update inventory stock
     */
    private void handleDispensingEvent(DomainEvent event, String kafkaTopic, Integer kafkaPartition, Long kafkaOffset) {
        try {
            DispensingPayload payload = parsePayload(event.getPayload(), DispensingPayload.class);
            
            log.info("Processing unified dispensing event: {} for product {} quantity {}",
                    event.getEventId(), payload.getProductId(), payload.getDispensedQuantity());

            // Check for duplicate event processing using domain event ID
            if (processedEventRepository.existsByEventIdAndEventType(event.getEventId(), "UNIFIED_DISPENSING_EVENT")) {
                log.warn("Unified dispensing event {} already processed, skipping", event.getEventId());
                return;
            }

            // Only reduce stock if dispensing was successful and items were actually dispensed
            if (("SUCCESS".equals(payload.getStatus()) || "COMPLETED".equals(payload.getStatus())) && payload.getDispensedQuantity() > 0) {
                // Update stock - reduce quantity
                inventoryService.updateStock(payload.getProductId(), -payload.getDispensedQuantity());
                
                log.info("Successfully reduced stock for product {} by {} units due to successful dispensing",
                         payload.getProductId(), payload.getDispensedQuantity());
            } else {
                log.warn("Dispensing event indicates failure or zero dispensed quantity, not updating stock: {} - status: {}, dispensed: {}",
                         event.getEventId(), payload.getStatus(), payload.getDispensedQuantity());
            }

            // Mark event as processed with complete Kafka metadata
            ProcessedEvent processedEvent = ProcessedEvent.builder()
                    .eventId(event.getEventId())
                    .eventType("UNIFIED_DISPENSING_EVENT")
                    .processedAt(LocalDateTime.now())
                    .topic(kafkaTopic != null ? kafkaTopic : "vending-machine-domain-events")
                    .partition(kafkaPartition != null ? kafkaPartition : 0)
                    .offset(kafkaOffset != null ? kafkaOffset : 0L)
                    .build();
            processedEventRepository.save(processedEvent);

            log.info("Successfully processed unified dispensing event: {}", event.getEventId());

        } catch (Exception e) {
            log.error("Failed to process unified dispensing event: {}", event.getEventId(), e);
            throw new RuntimeException("Failed to process unified dispensing event", e);
        }
    }

    /**
     * Parse JSON payload to specific payload class
     */
    private <T> T parsePayload(String payloadJson, Class<T> payloadClass) {
        try {
            return objectMapper.readValue(payloadJson, payloadClass);
        } catch (Exception e) {
            log.error("Failed to parse payload for class {}: {}", payloadClass.getSimpleName(), payloadJson, e);
            throw new RuntimeException("Failed to parse event payload", e);
        }
    }
}