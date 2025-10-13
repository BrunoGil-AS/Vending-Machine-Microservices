package com.vendingmachine.transaction.kafka;

import com.vendingmachine.common.event.TransactionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaEventService {

    private final KafkaTemplate<String, TransactionEvent> kafkaTemplate;

    public void publishTransactionEvent(TransactionEvent event) {
        try {
            kafkaTemplate.send("transaction-events", event.getEventId(), event);
            log.info("Published transaction event: {} for transaction {}", event.getEventId(), event.getTransactionId());
        } catch (Exception e) {
            log.error("Failed to publish transaction event: {}", event.getEventId(), e);
            throw new RuntimeException("Failed to publish transaction event", e);
        }
    }
}