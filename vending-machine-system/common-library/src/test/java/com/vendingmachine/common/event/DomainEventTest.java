package com.vendingmachine.common.event;

import com.vendingmachine.common.event.payload.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.Arrays;

/**
 * Test class for DomainEvent unified event model
 */
class DomainEventTest {
    
    @Test
    void testTransactionCreatedEvent() {
        // Arrange
        Long transactionId = 12345L;
        TransactionPayload payload = TransactionPayload.forCreated(
            transactionId, 1L, 101L, 2, BigDecimal.valueOf(5.50), "CARD"
        );
        
        // Act
        DomainEvent event = DomainEvent.transactionCreated(transactionId, payload);
        
        // Assert
        assertNotNull(event.getEventId());
        assertEquals("TRANSACTION_CREATED", event.getEventType());
        assertEquals(transactionId.toString(), event.getAggregateId());
        assertEquals("TRANSACTION", event.getAggregateType());
        assertNotNull(event.getTimestamp());
        assertNotNull(event.getPayload());
        assertEquals("1.0", event.getVersion());
        
        // Test payload deserialization
        TransactionPayload deserializedPayload = event.getPayloadAs(TransactionPayload.class);
        assertEquals(transactionId, deserializedPayload.getTransactionId());
        assertEquals("CARD", deserializedPayload.getPaymentMethod());
        assertEquals(BigDecimal.valueOf(5.50), deserializedPayload.getTotalAmount());
    }
    
    @Test
    void testPaymentCompletedEvent() {
        // Arrange
        Long transactionId = 12345L;
        PaymentPayload payload = PaymentPayload.forCompleted(
            1L, transactionId, BigDecimal.valueOf(5.50), "CARD", "gw_tx_12345"
        );
        
        // Act
        DomainEvent event = DomainEvent.paymentCompleted(transactionId, payload);
        
        // Assert
        assertEquals("PAYMENT_COMPLETED", event.getEventType());
        assertEquals("PAYMENT", event.getAggregateType());
        assertTrue(event.isEventType("PAYMENT_COMPLETED"));
        assertTrue(event.isAggregateType("PAYMENT"));
        
        // Test payload deserialization
        PaymentPayload deserializedPayload = event.getPayloadAs(PaymentPayload.class);
        assertEquals("COMPLETED", deserializedPayload.getStatus());
        assertEquals("gw_tx_12345", deserializedPayload.getGatewayTransactionId());
    }
    
    @Test
    void testDispensingFailedEvent() {
        // Arrange
        Long transactionId = 12345L;
        DispensingPayload payload = DispensingPayload.forFailure(
            1L, transactionId, 101L, 2, "Mechanical jam in slot A1"
        );
        
        // Act
        DomainEvent event = DomainEvent.dispensingFailed(transactionId, "Mechanical jam in slot A1", payload);
        
        // Assert
        assertEquals("DISPENSING_FAILED", event.getEventType());
        assertEquals("DISPENSING", event.getAggregateType());
        assertEquals("Mechanical jam in slot A1", event.getMetadata().get("failureReason"));
        
        // Test payload deserialization
        DispensingPayload deserializedPayload = event.getPayloadAs(DispensingPayload.class);
        assertEquals("FAILED", deserializedPayload.getStatus());
        assertEquals("Mechanical jam in slot A1", deserializedPayload.getFailureReason());
        assertEquals(0, deserializedPayload.getDispensedQuantity());
    }
    
    @Test
    void testStockUpdatedEvent() {
        // Arrange
        Long productId = 101L;
        InventoryPayload payload = InventoryPayload.forPurchase(
            productId, 10, 8, 2, 12345L
        );
        
        // Act
        DomainEvent event = DomainEvent.stockUpdated(productId, payload);
        
        // Assert
        assertEquals("STOCK_UPDATED", event.getEventType());
        assertEquals("INVENTORY", event.getAggregateType());
        assertEquals(productId.toString(), event.getAggregateId());
        
        // Test payload deserialization
        InventoryPayload deserializedPayload = event.getPayloadAs(InventoryPayload.class);
        assertEquals(10, deserializedPayload.getPreviousStock());
        assertEquals(8, deserializedPayload.getCurrentStock());
        assertEquals(-2, deserializedPayload.getQuantityChanged());
        assertEquals("PURCHASE", deserializedPayload.getChangeType());
    }
    
    @Test
    void testLowStockAlertEvent() {
        // Arrange
        Long productId = 101L;
        InventoryPayload payload = InventoryPayload.forLowStockAlert(
            productId, "Coca Cola", 3, 5
        );
        
        // Act
        DomainEvent event = DomainEvent.lowStockAlert(productId, 3, payload);
        
        // Assert
        assertEquals("LOW_STOCK_ALERT", event.getEventType());
        assertEquals("INVENTORY", event.getAggregateType());
        assertEquals("3", event.getMetadata().get("currentStock"));
        
        // Test payload deserialization
        InventoryPayload deserializedPayload = event.getPayloadAs(InventoryPayload.class);
        assertEquals("Coca Cola", deserializedPayload.getProductName());
        assertEquals(3, deserializedPayload.getCurrentStock());
        assertEquals(5, deserializedPayload.getAlertThreshold());
    }
    
    @Test
    void testEventMetadata() {
        // Arrange
        Long transactionId = 12345L;
        TransactionPayload payload = TransactionPayload.forCreated(
            transactionId, 1L, 101L, 2, BigDecimal.valueOf(5.50), "CARD"
        );
        
        // Act
        DomainEvent event = DomainEvent.transactionCreated(transactionId, payload)
            .withCorrelationId("corr-123-456")
            .withUserContext("user-789")
            .addMetadata("source", "mobile-app");
        
        // Assert
        assertEquals("corr-123-456", event.getCorrelationId());
        assertEquals("user-789", event.getMetadata().get("userId"));
        assertEquals("mobile-app", event.getMetadata().get("source"));
    }
    
    @Test
    void testEventUtilityMethods() {
        // Arrange
        Long transactionId = 12345L;
        TransactionPayload payload = TransactionPayload.forCreated(
            transactionId, 1L, 101L, 2, BigDecimal.valueOf(5.50), "CARD"
        );
        
        // Act
        DomainEvent event = DomainEvent.transactionCreated(transactionId, payload);
        
        // Assert
        assertTrue(event.isEventType("TRANSACTION_CREATED"));
        assertFalse(event.isEventType("PAYMENT_COMPLETED"));
        assertTrue(event.isAggregateType("TRANSACTION"));
        assertFalse(event.isAggregateType("PAYMENT"));
        
        String logString = event.toLogString();
        assertTrue(logString.contains("TRANSACTION_CREATED"));
        assertTrue(logString.contains("12345"));
        assertTrue(logString.contains("TRANSACTION"));
    }
    
    @Test
    void testComplexDispensingPayload() {
        // Arrange
        DispensingPayload.DispensingItem item1 = DispensingPayload.DispensingItem.builder()
            .slotPosition("A1")
            .productId(101L)
            .quantity(1)
            .status("SUCCESS")
            .build();
        
        DispensingPayload.DispensingItem item2 = DispensingPayload.DispensingItem.builder()
            .slotPosition("A2")
            .productId(101L)
            .quantity(1)
            .status("FAILED")
            .build();
        
        DispensingPayload payload = DispensingPayload.forPartial(
            1L, 12345L, 101L, 2, Arrays.asList(item1), "One slot jammed"
        );
        
        // Act
        DomainEvent event = DomainEvent.dispensingFailed(12345L, "One slot jammed", payload);
        
        // Assert
        DispensingPayload deserializedPayload = event.getPayloadAs(DispensingPayload.class);
        assertEquals("PARTIAL", deserializedPayload.getStatus());
        assertEquals(2, deserializedPayload.getRequestedQuantity());
        assertEquals(1, deserializedPayload.getDispensedQuantity());
        assertEquals(1, deserializedPayload.getDispensedItems().size());
        assertEquals("A1", deserializedPayload.getDispensedItems().get(0).getSlotPosition());
    }
    
    @Test
    void testNotificationEvent() {
        // Arrange
        NotificationPayload payload = NotificationPayload.forRefund(
            "notif-123", "user@example.com", 12345L, BigDecimal.valueOf(5.50), "Dispensing failed"
        );
        
        // Act
        DomainEvent event = DomainEvent.notificationSent("notif-123", payload);
        
        // Assert
        assertEquals("NOTIFICATION_SENT", event.getEventType());
        assertEquals("NOTIFICATION", event.getAggregateType());
        assertEquals("notif-123", event.getAggregateId());
        
        NotificationPayload deserializedPayload = event.getPayloadAs(NotificationPayload.class);
        assertEquals("Refund Processed", deserializedPayload.getSubject());
        assertEquals("HIGH", deserializedPayload.getPriority());
        assertTrue(deserializedPayload.getMessage().contains("$5.5"));
    }
}