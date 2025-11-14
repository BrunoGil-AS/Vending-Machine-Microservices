package com.vendingmachine.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Unified domain event for all business events in the vending machine system.
 * Replaces multiple specific event classes with a single, flexible structure.
 * 
 * This event supports:
 * - Event sourcing patterns
 * - Unified Kafka topic architecture
 * - Type-safe event creation via factory methods
 * - Metadata and tracing support
 * 
 * @since Phase 2: Kafka Optimization
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class DomainEvent {

    /**
     * Unique identifier for this specific event instance
     */
    private String eventId;

    /**
     * Type of business event (e.g., TRANSACTION_CREATED, PAYMENT_COMPLETED)
     */
    private String eventType;

    /**
     * ID of the aggregate root this event belongs to (usually transactionId)
     */
    private String aggregateId;

    /**
     * Type of aggregate (TRANSACTION, PAYMENT, INVENTORY, DISPENSING)
     */
    private String aggregateType;

    /**
     * Source service that generated this event
     */
    private String source;

    /**
     * Correlation ID for tracking related events across services
     */
    private String correlationId;

    /**
     * Event creation timestamp (milliseconds since epoch)
     */
    private Long timestamp;

    /**
     * JSON payload containing specific event data
     */
    private String payload;

    /**
     * Additional metadata (correlation IDs, tracing info, etc.)
     */
    @Builder.Default
    private Map<String, String> metadata = new HashMap<>();

    /**
     * Version for event schema evolution
     */
    @Builder.Default
    private String version = "1.0";

    // Static ObjectMapper for JSON operations
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // =========================
    // FACTORY METHODS
    // =========================

    /**
     * Create a transaction created event
     */
    public static DomainEvent transactionCreated(Long transactionId, Object payloadData) {
        return createDomainEvent(
                "TRANSACTION_CREATED",
                transactionId.toString(),
                "TRANSACTION",
                payloadData);
    }

    /**
     * Create a transaction status updated event
     */
    public static DomainEvent transactionStatusUpdated(Long transactionId, String status, Object payloadData) {
        DomainEvent event = createDomainEvent(
                "TRANSACTION_STATUS_UPDATED",
                transactionId.toString(),
                "TRANSACTION",
                payloadData);
        event.getMetadata().put("newStatus", status);
        return event;
    }

    /**
     * Create a payment completed event
     */
    public static DomainEvent paymentCompleted(Long transactionId, Object payloadData) {
        return createDomainEvent(
                "PAYMENT_COMPLETED",
                transactionId.toString(),
                "PAYMENT",
                payloadData);
    }

    /**
     * Create a payment failed event
     */
    public static DomainEvent paymentFailed(Long transactionId, String reason, Object payloadData) {
        DomainEvent event = createDomainEvent(
                "PAYMENT_FAILED",
                transactionId.toString(),
                "PAYMENT",
                payloadData);
        event.getMetadata().put("failureReason", reason);
        return event;
    }

    /**
     * Create an item dispensed event
     */
    public static DomainEvent itemDispensed(Long transactionId, Object payloadData) {
        return createDomainEvent(
                "ITEM_DISPENSED",
                transactionId.toString(),
                "DISPENSING",
                payloadData);
    }

    /**
     * Create a dispensing failed event
     */
    public static DomainEvent dispensingFailed(Long transactionId, String reason, Object payloadData) {
        DomainEvent event = createDomainEvent(
                "DISPENSING_FAILED",
                transactionId.toString(),
                "DISPENSING",
                payloadData);
        event.getMetadata().put("failureReason", reason);
        return event;
    }

    /**
     * Create a stock updated event
     */
    public static DomainEvent stockUpdated(Long productId, Object payloadData) {
        return createDomainEvent(
                "STOCK_UPDATED",
                productId.toString(),
                "INVENTORY",
                payloadData);
    }

    /**
     * Create a low stock alert event
     */
    public static DomainEvent lowStockAlert(Long productId, Integer currentStock, Object payloadData) {
        DomainEvent event = createDomainEvent(
                "LOW_STOCK_ALERT",
                productId.toString(),
                "INVENTORY",
                payloadData);
        event.getMetadata().put("currentStock", currentStock.toString());
        return event;
    }

    /**
     * Create a refund processed event
     */
    public static DomainEvent refundProcessed(Long transactionId, Object payloadData) {
        return createDomainEvent(
                "REFUND_PROCESSED",
                transactionId.toString(),
                "REFUND",
                payloadData);
    }

    /**
     * Create a notification sent event
     */
    public static DomainEvent notificationSent(String notificationId, Object payloadData) {
        return createDomainEvent(
                "NOTIFICATION_SENT",
                notificationId,
                "NOTIFICATION",
                payloadData);
    }

    // =========================
    // UTILITY METHODS
    // =========================

    /**
     * Core factory method for creating domain events
     */
    private static DomainEvent createDomainEvent(String eventType, String aggregateId,
            String aggregateType, Object payloadData) {
        return DomainEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .aggregateId(aggregateId)
                .aggregateType(aggregateType)
                .source("vending-machine-system") // Default source
                .correlationId(UUID.randomUUID().toString()) // Default correlation ID
                .timestamp(System.currentTimeMillis())
                .payload(serializePayload(payloadData))
                .metadata(new HashMap<>())
                .version("1.0")
                .build();
    }

    /**
     * Serialize payload object to JSON string
     */
    private static String serializePayload(Object payloadData) {
        try {
            return objectMapper.writeValueAsString(payloadData);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize payload to JSON", e);
        }
    }

    /**
     * Deserialize payload from JSON string to specified class
     */
    public <T> T getPayloadAs(Class<T> clazz) {
        try {
            return objectMapper.readValue(this.payload, clazz);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize payload from JSON", e);
        }
    }

    /**
     * Add metadata to the event
     */
    public DomainEvent addMetadata(String key, String value) {
        this.metadata.put(key, value);
        return this;
    }

    /**
     * Add correlation ID for request tracing
     */
    public DomainEvent withCorrelationId(String correlationId) {
        this.correlationId = correlationId;
        return this;
    }

    /**
     * Set the source service for this event
     */
    public DomainEvent withSource(String source) {
        this.source = source;
        return this;
    }

    /**
     * Add user context for audit trails
     */
    public DomainEvent withUserContext(String userId) {
        return addMetadata("userId", userId);
    }

    /**
     * Check if this event is of a specific type
     */
    public boolean isEventType(String eventType) {
        return this.eventType.equals(eventType);
    }

    /**
     * Check if this event belongs to a specific aggregate type
     */
    public boolean isAggregateType(String aggregateType) {
        return this.aggregateType.equals(aggregateType);
    }

    /**
     * Get a formatted string representation for logging
     */
    public String toLogString() {
        return String.format(
                "DomainEvent{eventType='%s', aggregateId='%s', aggregateType='%s', eventId='%s'}",
                eventType, aggregateId, aggregateType, eventId);
    }
}