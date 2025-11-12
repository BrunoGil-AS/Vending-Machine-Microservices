package com.vendingmachine.common.event.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Payload data for notification-related domain events
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPayload {
    
    private String notificationId;
    private String notificationType; // EMAIL, SMS, PUSH, ADMIN_ALERT
    private String recipient;
    private String subject;
    private String message;
    private String status; // PENDING, SENT, FAILED
    private Long relatedTransactionId;
    private Long relatedProductId;
    private String priority; // LOW, MEDIUM, HIGH, CRITICAL
    private Long timestamp;
    
    /**
     * Factory method for transaction completion notification
     */
    public static NotificationPayload forTransactionComplete(String notificationId, String recipient, 
                                                           Long transactionId, BigDecimal amount) {
        return NotificationPayload.builder()
            .notificationId(notificationId)
            .notificationType("EMAIL")
            .recipient(recipient)
            .subject("Purchase Confirmation")
            .message("Your purchase of $" + amount + " has been completed successfully.")
            .status("PENDING")
            .relatedTransactionId(transactionId)
            .priority("MEDIUM")
            .timestamp(System.currentTimeMillis())
            .build();
    }
    
    /**
     * Factory method for refund notification
     */
    public static NotificationPayload forRefund(String notificationId, String recipient, 
                                              Long transactionId, BigDecimal refundAmount, String reason) {
        return NotificationPayload.builder()
            .notificationId(notificationId)
            .notificationType("EMAIL")
            .recipient(recipient)
            .subject("Refund Processed")
            .message("A refund of $" + refundAmount + " has been processed for your purchase. Reason: " + reason)
            .status("PENDING")
            .relatedTransactionId(transactionId)
            .priority("HIGH")
            .timestamp(System.currentTimeMillis())
            .build();
    }
    
    /**
     * Factory method for low stock admin alert
     */
    public static NotificationPayload forLowStockAlert(String notificationId, Long productId, 
                                                     String productName, Integer currentStock) {
        return NotificationPayload.builder()
            .notificationId(notificationId)
            .notificationType("ADMIN_ALERT")
            .recipient("admin@vendingmachine.com")
            .subject("Low Stock Alert")
            .message("Product " + productName + " (ID: " + productId + ") has low stock: " + currentStock + " units remaining.")
            .status("PENDING")
            .relatedProductId(productId)
            .priority("HIGH")
            .timestamp(System.currentTimeMillis())
            .build();
    }
    
    /**
     * Factory method for system error alert
     */
    public static NotificationPayload forSystemError(String notificationId, String errorMessage, 
                                                   String component, Long relatedTransactionId) {
        return NotificationPayload.builder()
            .notificationId(notificationId)
            .notificationType("ADMIN_ALERT")
            .recipient("admin@vendingmachine.com")
            .subject("System Error Alert")
            .message("Error in " + component + ": " + errorMessage)
            .status("PENDING")
            .relatedTransactionId(relatedTransactionId)
            .priority("CRITICAL")
            .timestamp(System.currentTimeMillis())
            .build();
    }
}