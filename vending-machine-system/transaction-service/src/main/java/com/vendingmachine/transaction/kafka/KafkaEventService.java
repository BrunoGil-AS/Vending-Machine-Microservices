package com.vendingmachine.transaction.kafka;

import com.vendingmachine.common.event.DomainEvent;
import com.vendingmachine.common.event.payload.TransactionPayload;
import com.vendingmachine.common.kafka.UnifiedEventPublisher;
import com.vendingmachine.common.aop.annotation.Auditable;
import com.vendingmachine.common.aop.annotation.ExecutionTime;
import com.vendingmachine.common.util.CorrelationIdUtil;
import com.vendingmachine.transaction.transaction.Transaction;
import com.vendingmachine.transaction.transaction.TransactionItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaEventService {

    private final UnifiedEventPublisher unifiedEventPublisher;

    /**
     * Publishes transaction events with complete data to unified domain events topic
     * @param transaction The complete transaction entity
     * @param eventType The type of transaction event (CREATED, PROCESSING, COMPLETED, FAILED)
     */
    @Auditable(operation = "PUBLISH_TRANSACTION_EVENT", entityType = "TransactionEvent", logParameters = true)
    @ExecutionTime(operation = "Publish Transaction Event", warningThreshold = 1000, detailed = true)
    public void publishTransactionEventWithCompleteData(Transaction transaction, String eventType) {
        try {
            // Create enhanced payload with complete transaction data
            TransactionPayload payload = TransactionPayload.builder()
                .transactionId(transaction.getId())
                .userId(null) // Anonymous purchases
                .productId(transaction.getItems().isEmpty() ? null : transaction.getItems().get(0).getProductId())
                .quantity(transaction.getItems().stream().mapToInt(TransactionItem::getQuantity).sum())
                .totalAmount(transaction.getTotalAmount())
                .status(transaction.getStatus().name())
                .paymentMethod(transaction.getPaymentMethod())
                .timestamp(System.currentTimeMillis())
                .build();

            // Create unified domain event
            DomainEvent domainEvent = DomainEvent.builder()
                .eventId("txn-" + eventType.toLowerCase() + "-" + transaction.getId() + "-" + System.currentTimeMillis())
                .eventType("TRANSACTION_" + eventType)
                .aggregateId(transaction.getId().toString())
                .aggregateType("TRANSACTION")
                .source("transaction-service")
                .correlationId(CorrelationIdUtil.getCorrelationId())
                .timestamp(System.currentTimeMillis())
                .payload(serializePayload(payload))
                .version("1.0")
                .build();

            // Publish to unified topic only
            unifiedEventPublisher.publishEvent(domainEvent);
            
            log.info("Successfully published transaction event to unified topic: {} for transaction {}", 
                domainEvent.getEventId(), transaction.getId());

        } catch (Exception e) {
            log.error("Error creating unified transaction event for transaction {}: {}", 
                transaction.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to publish unified transaction event", e);
        }
    }

    /**
     * Serialize payload to JSON
     */
    private String serializePayload(Object payload) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload);
        } catch (Exception e) {
            log.error("Failed to serialize payload", e);
            return "{}";
        }
    }
}