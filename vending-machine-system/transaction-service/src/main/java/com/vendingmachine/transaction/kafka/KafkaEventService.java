package com.vendingmachine.transaction.kafka;

import com.vendingmachine.common.event.TransactionEvent;
import com.vendingmachine.common.aop.annotation.Auditable;
import com.vendingmachine.common.aop.annotation.ExecutionTime;
import com.vendingmachine.common.util.CorrelationIdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaEventService {

    private final KafkaTemplate<String, TransactionEvent> kafkaTemplate;

    @Auditable(operation = "PUBLISH_TRANSACTION_EVENT", entityType = "TransactionEvent", logParameters = true)
    @ExecutionTime(operation = "Publish Transaction Event", warningThreshold = 1000, detailed = true)
    public void publishTransactionEvent(TransactionEvent event) {
        try {
            // Get current correlation ID
            String correlationId = CorrelationIdUtil.getCorrelationId();
            
            // Build message with correlation ID in header
            Message<TransactionEvent> message = MessageBuilder
                .withPayload(event)
                .setHeader(KafkaHeaders.TOPIC, "transaction-events")
                .setHeader(KafkaHeaders.KEY, event.getEventId())
                .setHeader("X-Correlation-ID", correlationId)
                .build();
            
            kafkaTemplate.send(message);
            log.info("Published transaction event: {} for transaction {} with correlation ID: {}", 
                    event.getEventId(), event.getTransactionId(), correlationId);
        } catch (Exception e) {
            log.error("Failed to publish transaction event: {}", event.getEventId(), e);
            throw new RuntimeException("Failed to publish transaction event", e);
        }
    }
}