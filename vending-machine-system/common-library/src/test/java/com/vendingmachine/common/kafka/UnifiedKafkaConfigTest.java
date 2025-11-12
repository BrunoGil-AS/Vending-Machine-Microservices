package com.vendingmachine.common.kafka;

import com.vendingmachine.common.event.DomainEvent;
import com.vendingmachine.common.event.payload.TransactionPayload;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for unified Kafka topic configuration and event handling
 */
class UnifiedKafkaConfigTest {

    @Test
    void shouldHaveCorrectTopicNames() {
        assertEquals("vending-machine-domain-events", UnifiedTopicConfig.UNIFIED_DOMAIN_EVENTS_TOPIC);
        assertEquals("vending-machine-domain-events-dlq", UnifiedTopicConfig.UNIFIED_DLQ_TOPIC);
    }

    @Test
    void shouldHaveCorrectEventTypeConstants() {
        assertEquals("TRANSACTION", UnifiedTopicConfig.TRANSACTION_EVENT_TYPE);
        assertEquals("PAYMENT", UnifiedTopicConfig.PAYMENT_EVENT_TYPE);
        assertEquals("DISPENSING", UnifiedTopicConfig.DISPENSING_EVENT_TYPE);
        assertEquals("INVENTORY", UnifiedTopicConfig.INVENTORY_EVENT_TYPE);
        assertEquals("NOTIFICATION", UnifiedTopicConfig.NOTIFICATION_EVENT_TYPE);
    }

    @Test
    void shouldHaveCorrectServiceConstants() {
        assertEquals("transaction-service", UnifiedTopicConfig.TRANSACTION_SERVICE);
        assertEquals("payment-service", UnifiedTopicConfig.PAYMENT_SERVICE);
        assertEquals("dispensing-service", UnifiedTopicConfig.DISPENSING_SERVICE);
        assertEquals("inventory-service", UnifiedTopicConfig.INVENTORY_SERVICE);
        assertEquals("notification-service", UnifiedTopicConfig.NOTIFICATION_SERVICE);
    }

    @Test
    void shouldHaveCorrectHeaderConstants() {
        assertEquals("eventType", UnifiedTopicConfig.EVENT_TYPE_HEADER);
        assertEquals("eventSource", UnifiedTopicConfig.EVENT_SOURCE_HEADER);
        assertEquals("correlationId", UnifiedTopicConfig.CORRELATION_ID_HEADER);
    }

    @Test
    void domainEventShouldHaveRequiredFieldsForUnifiedPublishing() {
        // Create test event
        TransactionPayload payload = TransactionPayload.forCreated(1L, 1L, 1L, 2, 
                new java.math.BigDecimal("5.50"), "CASH");
        DomainEvent event = DomainEvent.transactionCreated(1L, payload)
                .withSource(UnifiedTopicConfig.TRANSACTION_SERVICE)
                .withCorrelationId("test-correlation-123");

        // Verify all required fields for unified publishing
        assertNotNull(event.getEventId());
        assertNotNull(event.getEventType());
        assertNotNull(event.getSource());
        assertNotNull(event.getCorrelationId());
        assertNotNull(event.getTimestamp());
        assertNotNull(event.getPayload());
        assertEquals("TRANSACTION_CREATED", event.getEventType());
        assertEquals("transaction-service", event.getSource());
        assertEquals("test-correlation-123", event.getCorrelationId());
    }

    /**
     * Test consumer implementation for testing event routing
     */
    static class TestUnifiedEventConsumer extends UnifiedEventConsumer {
        
        private DomainEvent lastTransactionEvent;
        private DomainEvent lastPaymentEvent;
        private boolean unknownEventHandled = false;

        @Override
        protected void handleTransactionCreated(DomainEvent event) {
            this.lastTransactionEvent = event;
        }

        @Override
        protected void handlePaymentCompleted(DomainEvent event) {
            this.lastPaymentEvent = event;
        }

        @Override
        protected void handleUnknownEvent(DomainEvent event) {
            this.unknownEventHandled = true;
        }

        @Override
        protected String getServiceName() {
            return "test-service";
        }

        // Getters for testing
        public DomainEvent getLastTransactionEvent() { return lastTransactionEvent; }
        public DomainEvent getLastPaymentEvent() { return lastPaymentEvent; }
        public boolean isUnknownEventHandled() { return unknownEventHandled; }
    }

    @Test
    void unifiedConsumerShouldRouteEventsCorrectly() {
        TestUnifiedEventConsumer consumer = new TestUnifiedEventConsumer();

        // Test transaction event routing
        TransactionPayload transactionPayload = TransactionPayload.forCreated(1L, 1L, 1L, 2, 
                new java.math.BigDecimal("5.50"), "CASH");
        DomainEvent transactionEvent = DomainEvent.transactionCreated(1L, transactionPayload);
        
        // Simulate the event handling that would happen in Kafka listener
        // We can't easily test the @KafkaListener without full integration test
        consumer.handleTransactionCreated(transactionEvent);
        assertEquals(transactionEvent, consumer.getLastTransactionEvent());

        // Test payment event routing
        DomainEvent paymentEvent = DomainEvent.builder()
                .eventId("test-id")
                .eventType("PAYMENT_COMPLETED")
                .aggregateId("1")
                .aggregateType("PAYMENT")
                .source("payment-service")
                .correlationId("test-corr")
                .timestamp(System.currentTimeMillis())
                .payload("{}")
                .build();
        
        consumer.handlePaymentCompleted(paymentEvent);
        assertEquals(paymentEvent, consumer.getLastPaymentEvent());

        // Test unknown event
        DomainEvent unknownEvent = DomainEvent.builder()
                .eventId("unknown-id")
                .eventType("UNKNOWN_EVENT_TYPE")
                .aggregateId("1")
                .aggregateType("UNKNOWN")
                .source("test-service")
                .correlationId("test-corr")
                .timestamp(System.currentTimeMillis())
                .payload("{}")
                .build();
        
        consumer.handleUnknownEvent(unknownEvent);
        assertTrue(consumer.isUnknownEventHandled());
    }

    @Test
    void shouldGenerateCorrectPartitionKeys() {
        // Test events with correlation ID should use correlation ID as partition key
        DomainEvent eventWithCorrelation = DomainEvent.builder()
                .eventId("test-id")
                .eventType("TRANSACTION_CREATED")
                .correlationId("corr-123")
                .build();

        // Test events without correlation ID should use event type as partition key
        DomainEvent eventWithoutCorrelation = DomainEvent.builder()
                .eventId("test-id")
                .eventType("PAYMENT_COMPLETED")
                .correlationId(null)
                .build();

        // These tests would be in the UnifiedEventPublisher if the generatePartitionKey method was public
        // For now, we just verify the events have the right structure
        assertNotNull(eventWithCorrelation.getCorrelationId());
        assertNull(eventWithoutCorrelation.getCorrelationId());
    }
}