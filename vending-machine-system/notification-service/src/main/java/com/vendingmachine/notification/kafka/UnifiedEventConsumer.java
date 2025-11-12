package com.vendingmachine.notification.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vendingmachine.common.event.DomainEvent;
import com.vendingmachine.common.event.payload.*;
import com.vendingmachine.common.aop.annotation.Auditable;
import com.vendingmachine.common.aop.annotation.ExecutionTime;
import com.vendingmachine.common.util.CorrelationIdUtil;
import com.vendingmachine.notification.notification.NotificationService;
import com.vendingmachine.notification.notification.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UnifiedEventConsumer {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @KafkaListener(topics = "vending-machine-domain-events", groupId = "notification-service-unified-group",
                   containerFactory = "domainEventKafkaListenerContainerFactory")
    @Transactional
    @Auditable(operation = "CONSUME_UNIFIED_EVENT", entityType = "DomainEvent", logParameters = true)
    @ExecutionTime(operation = "CONSUME_UNIFIED_EVENT", warningThreshold = 2000)
    public void consumeUnifiedEvent(@Payload DomainEvent event,
                                   @Header(value = "X-Correlation-ID", required = false) String correlationId) {
        try {
            if (correlationId != null) {
                CorrelationIdUtil.setCorrelationId(correlationId);
            } else {
                CorrelationIdUtil.setCorrelationId(event.getCorrelationId());
            }
            
            log.info("Received unified event: {} from {} with type {}", 
                    event.getEventId(), event.getSource(), event.getEventType());

            // Route event based on type and source
            routeEvent(event);
            
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
    private void routeEvent(DomainEvent event) {
        String eventType = event.getEventType();
        String source = event.getSource();
        
        try {
            switch (eventType) {
                // Transaction Events
                case "TRANSACTION_CREATED":
                case "TRANSACTION_PROCESSING":
                case "TRANSACTION_COMPLETED":
                case "TRANSACTION_FAILED":
                    handleTransactionEvent(event);
                    break;
                    
                // Payment Events
                case "PAYMENT_PROCESSING":
                case "PAYMENT_COMPLETED":
                case "PAYMENT_FAILED":
                    handlePaymentEvent(event);
                    break;
                    
                // Dispensing Events
                case "DISPENSING_STARTED":
                case "DISPENSING_COMPLETED":
                case "DISPENSING_FAILED":
                case "DISPENSING_PARTIAL":
                    handleDispensingEvent(event);
                    break;
                    
                // Inventory Events
                case "INVENTORY_STOCK_UPDATED":
                case "INVENTORY_LOW_STOCK_ALERT":
                case "INVENTORY_OUT_OF_STOCK_ALERT":
                    handleInventoryEvent(event);
                    break;
                    
                default:
                    log.warn("Unknown event type received: {} from source: {}", eventType, source);
            }
        } catch (Exception e) {
            log.error("Failed to route event {} of type {}", event.getEventId(), eventType, e);
            throw e;
        }
    }

    /**
     * Handle transaction events with enhanced payload
     */
    private void handleTransactionEvent(DomainEvent event) {
        try {
            TransactionPayload payload = parsePayload(event.getPayload(), TransactionPayload.class);
            
            NotificationType type;
            String message;
            String severity;

            switch (event.getEventType()) {
                case "TRANSACTION_COMPLETED":
                    type = NotificationType.TRANSACTION_COMPLETED;
                    message = String.format("Transaction %d completed successfully. Total: $%.2f, Quantity: %d", 
                            payload.getTransactionId(), payload.getTotalAmount(), 
                            payload.getQuantity() != null ? payload.getQuantity() : 0);
                    severity = "MEDIUM";
                    break;
                case "TRANSACTION_FAILED":
                    type = NotificationType.TRANSACTION_FAILED;
                    message = String.format("Transaction %d failed. Total: $%.2f, Payment: %s", 
                            payload.getTransactionId(), payload.getTotalAmount(), payload.getPaymentMethod());
                    severity = "HIGH";
                    break;
                default:
                    return; // Don't create notifications for intermediate states
            }

            notificationService.createNotification(
                    type,
                    message,
                    String.format("Payment method: %s", payload.getPaymentMethod()),
                    payload.getTransactionId(),
                    "TRANSACTION",
                    severity
            );
            
            log.debug("Created notification for transaction event: {} status: {}", 
                    payload.getTransactionId(), event.getEventType());
        } catch (Exception e) {
            log.error("Failed to handle transaction event: {}", event.getEventId(), e);
            throw e;
        }
    }

    /**
     * Handle payment events with enhanced payload
     */
    private void handlePaymentEvent(DomainEvent event) {
        try {
            PaymentPayload payload = parsePayload(event.getPayload(), PaymentPayload.class);
            
            NotificationType type;
            String message;
            String severity;

            switch (event.getEventType()) {
                case "PAYMENT_COMPLETED":
                    type = NotificationType.PAYMENT_SUCCESS;
                    message = String.format("Payment completed for transaction %d. Amount: $%.2f via %s", 
                            payload.getTransactionId(), payload.getAmount(), payload.getPaymentMethod());
                    severity = "LOW";
                    break;
                case "PAYMENT_FAILED":
                    type = NotificationType.PAYMENT_FAILED;
                    message = String.format("Payment failed for transaction %d. Amount: $%.2f via %s", 
                            payload.getTransactionId(), payload.getAmount(), payload.getPaymentMethod());
                    severity = "HIGH";
                    break;
                default:
                    return; // Don't create notifications for processing states
            }

            notificationService.createNotification(
                    type,
                    message,
                    String.format("Payment ID: %d", payload.getPaymentId()),
                    payload.getTransactionId(),
                    "TRANSACTION",
                    severity
            );
            
            log.debug("Created notification for payment event: transaction {} status: {}", 
                    payload.getTransactionId(), event.getEventType());
        } catch (Exception e) {
            log.error("Failed to handle payment event: {}", event.getEventId(), e);
            throw e;
        }
    }

    /**
     * Handle dispensing events with enhanced payload
     */
    private void handleDispensingEvent(DomainEvent event) {
        try {
            DispensingPayload payload = parsePayload(event.getPayload(), DispensingPayload.class);
            
            NotificationType type;
            String message;
            String severity;

            switch (event.getEventType()) {
                case "DISPENSING_COMPLETED":
                    type = NotificationType.DISPENSING_SUCCESS;
                    message = String.format("Product %d dispensed successfully for transaction %d. Quantity: %d", 
                            payload.getProductId(), payload.getTransactionId(), payload.getDispensedQuantity());
                    severity = "LOW";
                    break;
                case "DISPENSING_FAILED":
                    type = NotificationType.DISPENSING_FAILED;
                    message = String.format("Dispensing failed for product %d in transaction %d. Reason: %s", 
                            payload.getProductId(), payload.getTransactionId(), 
                            payload.getFailureReason() != null ? payload.getFailureReason() : "Unknown");
                    severity = "HIGH";
                    break;
                case "DISPENSING_PARTIAL":
                    type = NotificationType.DISPENSING_FAILED;
                    message = String.format("Partial dispensing for product %d in transaction %d. Requested: %d, Dispensed: %d", 
                            payload.getProductId(), payload.getTransactionId(), 
                            payload.getRequestedQuantity(), payload.getDispensedQuantity());
                    severity = "MEDIUM";
                    break;
                default:
                    return; // Don't create notifications for started states
            }

            notificationService.createNotification(
                    type,
                    message,
                    String.format("Dispensing ID: %d", payload.getDispensingId()),
                    payload.getTransactionId(),
                    "TRANSACTION",
                    severity
            );
            
            log.debug("Created notification for dispensing event: transaction {} status: {}", 
                    payload.getTransactionId(), event.getEventType());
        } catch (Exception e) {
            log.error("Failed to handle dispensing event: {}", event.getEventId(), e);
            throw e;
        }
    }

    /**
     * Handle inventory events with enhanced payload
     */
    private void handleInventoryEvent(DomainEvent event) {
        try {
            InventoryPayload payload = parsePayload(event.getPayload(), InventoryPayload.class);
            
            // Only handle low stock and out of stock alerts
            if ("INVENTORY_LOW_STOCK_ALERT".equals(event.getEventType()) || 
                "INVENTORY_OUT_OF_STOCK_ALERT".equals(event.getEventType())) {
                
                String message = String.format("Low stock alert for product %s (%d). Current: %d, Threshold: %d", 
                        payload.getProductName(), payload.getProductId(), 
                        payload.getCurrentStock(), payload.getAlertThreshold());

                String severity = "INVENTORY_OUT_OF_STOCK_ALERT".equals(event.getEventType()) ? "HIGH" : "MEDIUM";

                notificationService.createNotification(
                        NotificationType.LOW_STOCK,
                        message,
                        String.format("Change type: %s, Reason: %s", payload.getChangeType(), payload.getReason()),
                        payload.getProductId(),
                        "PRODUCT",
                        severity
                );
                
                log.debug("Created notification for inventory event: product {} type: {}", 
                        payload.getProductId(), event.getEventType());
            }
        } catch (Exception e) {
            log.error("Failed to handle inventory event: {}", event.getEventId(), e);
            throw e;
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