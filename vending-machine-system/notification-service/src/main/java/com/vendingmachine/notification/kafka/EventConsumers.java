package com.vendingmachine.notification.kafka;

import com.vendingmachine.common.aop.annotation.Auditable;
import com.vendingmachine.common.aop.annotation.ExecutionTime;
import com.vendingmachine.common.event.*;
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
public class EventConsumers {

    private final NotificationService notificationService;

    @KafkaListener(topics = "inventory-events", groupId = "notification-service-group",
                   containerFactory = "lowStockAlertKafkaListenerContainerFactory")
    @Transactional
    @Auditable(operation = "CONSUME_LOW_STOCK_ALERT", entityType = "LowStockAlertEvent", logParameters = true)
    @ExecutionTime(operation = "CONSUME_LOW_STOCK_ALERT", warningThreshold = 1500)
    public void consumeLowStockAlertEvent(@Payload LowStockAlertEvent event,
                                         @Header(value = "X-Correlation-ID", required = false) String correlationId) {
        try {
            if (correlationId != null) {
                CorrelationIdUtil.setCorrelationId(correlationId);
            }
            
            log.info("Received low stock alert event: {} for product {}", event.getEventId(), event.getProductId());

            String message = String.format("Low stock alert for product %s (%s). Current quantity: %d, Minimum threshold: %d",
                    event.getProductName(), event.getProductId(), event.getCurrentQuantity(), event.getMinThreshold());

            String severity = "CRITICAL".equals(event.getAlertType()) ? "HIGH" : "MEDIUM";

            notificationService.createNotification(
                    NotificationType.LOW_STOCK,
                    message,
                    String.format("Alert type: %s", event.getAlertType()),
                    event.getProductId(),
                    "PRODUCT",
                    severity
            );
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }

    // DISABLED FOR UNIFIED CONSUMER VALIDATION
    // @KafkaListener(topics = "transaction-events", groupId = "notification-service-group",
    //                containerFactory = "transactionEventKafkaListenerContainerFactory")
    @Transactional
    @Auditable(operation = "CONSUME_TRANSACTION_EVENT", entityType = "TransactionEvent", logParameters = true)
    @ExecutionTime(operation = "CONSUME_TRANSACTION_EVENT", warningThreshold = 1500)
    public void consumeTransactionEvent(@Payload TransactionEvent event,
                                       @Header(value = "X-Correlation-ID", required = false) String correlationId) {
        try {
            if (correlationId != null) {
                CorrelationIdUtil.setCorrelationId(correlationId);
            }
            
            log.info("Received transaction event: {} with status {}", event.getEventId(), event.getStatus());

            NotificationType type;
            String message;
            String severity;

            switch (event.getStatus()) {
                case "COMPLETED":
                    type = NotificationType.TRANSACTION_COMPLETED;
                    message = String.format("Transaction %d completed successfully. Amount: $%.2f",
                            event.getTransactionId(), event.getTotalAmount());
                    severity = "LOW";
                    break;
                case "FAILED":
                    type = NotificationType.TRANSACTION_FAILED;
                    message = String.format("Transaction %d failed. Amount: $%.2f",
                            event.getTransactionId(), event.getTotalAmount());
                    severity = "HIGH";
                    break;
                default:
                    log.debug("Ignoring transaction event with status: {}", event.getStatus());
                    return;
            }

            notificationService.createNotification(
                    type,
                    message,
                    String.format("Transaction status: %s", event.getStatus()),
                    event.getTransactionId(),
                    "TRANSACTION",
                    severity
            );
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }

    // DISABLED FOR UNIFIED CONSUMER VALIDATION
    // @KafkaListener(topics = "payment-events", groupId = "notification-service-group",
    //                containerFactory = "paymentEventKafkaListenerContainerFactory")
    @Transactional
    @Auditable(operation = "CONSUME_PAYMENT_EVENT", entityType = "PaymentEvent", logParameters = true)
    @ExecutionTime(operation = "CONSUME_PAYMENT_EVENT", warningThreshold = 1500)
    public void consumePaymentEvent(@Payload PaymentEvent event,
                                   @Header(value = "X-Correlation-ID", required = false) String correlationId) {
        try {
            if (correlationId != null) {
                CorrelationIdUtil.setCorrelationId(correlationId);
            }
            
            log.info("Received payment event: {} with status {}", event.getEventId(), event.getStatus());

            NotificationType type;
            String message;
            String severity;

            switch (event.getStatus()) {
                case "SUCCESS":
                    type = NotificationType.PAYMENT_SUCCESS;
                    message = String.format("Payment successful for transaction %d. Amount: $%.2f, Method: %s",
                            event.getTransactionId(), event.getAmount(), event.getMethod());
                    severity = "LOW";
                    break;
                case "FAILED":
                    type = NotificationType.PAYMENT_FAILED;
                    message = String.format("Payment failed for transaction %d. Amount: $%.2f, Method: %s",
                            event.getTransactionId(), event.getAmount(), event.getMethod());
                    severity = "HIGH";
                    break;
                default:
                    log.debug("Ignoring payment event with status: {}", event.getStatus());
                    return;
            }

            notificationService.createNotification(
                    type,
                    message,
                    String.format("Payment method: %s", event.getMethod()),
                    event.getTransactionId(),
                    "TRANSACTION",
                    severity
            );
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }

    // DISABLED FOR UNIFIED CONSUMER VALIDATION
    // @KafkaListener(topics = "dispensing-events", groupId = "notification-service-group",
    //                containerFactory = "dispensingEventKafkaListenerContainerFactory")
    @Transactional
    @Auditable(operation = "CONSUME_DISPENSING_EVENT", entityType = "DispensingEvent", logParameters = true)
    @ExecutionTime(operation = "CONSUME_DISPENSING_EVENT", warningThreshold = 1500)
    public void consumeDispensingEvent(@Payload DispensingEvent event,
                                      @Header(value = "X-Correlation-ID", required = false) String correlationId) {
        try {
            if (correlationId != null) {
                CorrelationIdUtil.setCorrelationId(correlationId);
            }
            
            log.info("Received dispensing event: {} for product {}", event.getEventId(), event.getProductId());

            // Note: DispensingEvent doesn't have status, so we'll assume success for now
            // In a real implementation, you might need to enhance DispensingEvent to include status
            NotificationType type = NotificationType.DISPENSING_SUCCESS;
            String message = String.format("Product dispensing completed. Product ID: %d, Quantity: %d",
                    event.getProductId(), event.getQuantity());
            String severity = "LOW";

            notificationService.createNotification(
                    type,
                    message,
                    "Dispensing operation completed successfully",
                    event.getProductId(),
                    "PRODUCT",
                    severity
            );
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }
}