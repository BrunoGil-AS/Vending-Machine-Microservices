package com.vendingmachine.notification.kafka;

import com.vendingmachine.common.event.*;
import com.vendingmachine.notification.notification.NotificationService;
import com.vendingmachine.notification.notification.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
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
    public void consumeLowStockAlertEvent(LowStockAlertEvent event) {
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
    }

    @KafkaListener(topics = "transaction-events", groupId = "notification-service-group",
                   containerFactory = "transactionEventKafkaListenerContainerFactory")
    @Transactional
    public void consumeTransactionEvent(TransactionEvent event) {
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
    }

    @KafkaListener(topics = "payment-events", groupId = "notification-service-group",
                   containerFactory = "paymentEventKafkaListenerContainerFactory")
    @Transactional
    public void consumePaymentEvent(PaymentEvent event) {
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
    }

    @KafkaListener(topics = "dispensing-events", groupId = "notification-service-group",
                   containerFactory = "dispensingEventKafkaListenerContainerFactory")
    @Transactional
    public void consumeDispensingEvent(DispensingEvent event) {
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
    }
}