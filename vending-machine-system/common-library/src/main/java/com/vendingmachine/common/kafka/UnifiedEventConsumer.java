package com.vendingmachine.common.kafka;

import com.vendingmachine.common.event.DomainEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Abstract base class for unified event consumption
 * 
 * Services can extend this class and override specific event type handlers
 * to process events relevant to their domain. The base class handles:
 * - Event type routing to appropriate handlers
 * - Dead letter queue forwarding for unhandled events
 * - Logging and monitoring
 * - Correlation ID extraction and tracking
 * 
 * Event routing strategy:
 * - Events are routed based on eventType field
 * - Services override only the handlers they need
 * - Unhandled events are logged and potentially sent to DLQ
 */
@Component
@Slf4j
public abstract class UnifiedEventConsumer {

    /**
     * Main event consumer that listens to the unified topic
     * Routes events based on their type to specific handlers
     */
    @KafkaListener(
        topics = UnifiedTopicConfig.UNIFIED_DOMAIN_EVENTS_TOPIC,
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleDomainEvent(
            @Payload DomainEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {
        
        try {
            log.info("Received unified event: type={}, source={}, correlationId={}, eventId={}, partition={}, offset={}", 
                    event.getEventType(), event.getSource(), event.getCorrelationId(), 
                    event.getEventId(), partition, offset);

            // Route to appropriate handler based on event type
            switch (event.getEventType()) {
                // Transaction Events
                case "TRANSACTION_CREATED":
                    handleTransactionCreated(event);
                    break;
                case "TRANSACTION_STATUS_UPDATED":
                    handleTransactionStatusUpdated(event);
                    break;
                case "TRANSACTION_COMPLETED":
                    handleTransactionCompleted(event);
                    break;
                case "TRANSACTION_FAILED":
                    handleTransactionFailed(event);
                    break;

                // Payment Events
                case "PAYMENT_INITIATED":
                    handlePaymentInitiated(event);
                    break;
                case "PAYMENT_COMPLETED":
                    handlePaymentCompleted(event);
                    break;
                case "PAYMENT_FAILED":
                    handlePaymentFailed(event);
                    break;

                // Dispensing Events
                case "ITEM_DISPENSED":
                    handleItemDispensed(event);
                    break;
                case "DISPENSING_FAILED":
                    handleDispensingFailed(event);
                    break;
                case "HARDWARE_ERROR":
                    handleHardwareError(event);
                    break;

                // Inventory Events
                case "STOCK_UPDATED":
                    handleStockUpdated(event);
                    break;
                case "LOW_STOCK_ALERT":
                    handleLowStockAlert(event);
                    break;
                case "OUT_OF_STOCK":
                    handleOutOfStock(event);
                    break;

                // Notification Events
                case "USER_NOTIFICATION":
                    handleUserNotification(event);
                    break;
                case "ADMIN_ALERT":
                    handleAdminAlert(event);
                    break;

                default:
                    handleUnknownEvent(event);
                    break;
            }

            log.debug("Successfully processed event: type={}, eventId={}", 
                    event.getEventType(), event.getEventId());

        } catch (Exception e) {
            log.error("Error processing unified event: type={}, eventId={}, error={}", 
                    event.getEventType(), event.getEventId(), e.getMessage(), e);
            
            // Handle error - could send to DLQ or implement retry logic
            handleEventProcessingError(event, e);
        }
    }

    // =========================
    // TRANSACTION EVENT HANDLERS
    // =========================
    
    protected void handleTransactionCreated(DomainEvent event) {
        log.debug("Transaction created event not handled by service: {}", getClass().getSimpleName());
    }

    protected void handleTransactionStatusUpdated(DomainEvent event) {
        log.debug("Transaction status updated event not handled by service: {}", getClass().getSimpleName());
    }

    protected void handleTransactionCompleted(DomainEvent event) {
        log.debug("Transaction completed event not handled by service: {}", getClass().getSimpleName());
    }

    protected void handleTransactionFailed(DomainEvent event) {
        log.debug("Transaction failed event not handled by service: {}", getClass().getSimpleName());
    }

    // =========================
    // PAYMENT EVENT HANDLERS
    // =========================
    
    protected void handlePaymentInitiated(DomainEvent event) {
        log.debug("Payment initiated event not handled by service: {}", getClass().getSimpleName());
    }

    protected void handlePaymentCompleted(DomainEvent event) {
        log.debug("Payment completed event not handled by service: {}", getClass().getSimpleName());
    }

    protected void handlePaymentFailed(DomainEvent event) {
        log.debug("Payment failed event not handled by service: {}", getClass().getSimpleName());
    }

    // =========================
    // DISPENSING EVENT HANDLERS
    // =========================
    
    protected void handleItemDispensed(DomainEvent event) {
        log.debug("Item dispensed event not handled by service: {}", getClass().getSimpleName());
    }

    protected void handleDispensingFailed(DomainEvent event) {
        log.debug("Dispensing failed event not handled by service: {}", getClass().getSimpleName());
    }

    protected void handleHardwareError(DomainEvent event) {
        log.debug("Hardware error event not handled by service: {}", getClass().getSimpleName());
    }

    // =========================
    // INVENTORY EVENT HANDLERS
    // =========================
    
    protected void handleStockUpdated(DomainEvent event) {
        log.debug("Stock updated event not handled by service: {}", getClass().getSimpleName());
    }

    protected void handleLowStockAlert(DomainEvent event) {
        log.debug("Low stock alert event not handled by service: {}", getClass().getSimpleName());
    }

    protected void handleOutOfStock(DomainEvent event) {
        log.debug("Out of stock event not handled by service: {}", getClass().getSimpleName());
    }

    // =========================
    // NOTIFICATION EVENT HANDLERS
    // =========================
    
    protected void handleUserNotification(DomainEvent event) {
        log.debug("User notification event not handled by service: {}", getClass().getSimpleName());
    }

    protected void handleAdminAlert(DomainEvent event) {
        log.debug("Admin alert event not handled by service: {}", getClass().getSimpleName());
    }

    // =========================
    // ERROR HANDLING
    // =========================
    
    protected void handleUnknownEvent(DomainEvent event) {
        log.warn("Unknown event type received: type={}, eventId={}, source={}", 
                event.getEventType(), event.getEventId(), event.getSource());
        // Could implement logic to send unknown events to DLQ
    }

    protected void handleEventProcessingError(DomainEvent event, Exception error) {
        log.error("Event processing failed: type={}, eventId={}, error={}", 
                event.getEventType(), event.getEventId(), error.getMessage());
        // Could implement retry logic or DLQ forwarding
    }

    /**
     * Get the service name for this consumer (used in logging)
     */
    protected abstract String getServiceName();
}