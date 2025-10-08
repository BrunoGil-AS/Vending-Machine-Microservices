package com.vendingmachine.inventory.kafka;

import com.vendingmachine.common.event.DispensingEvent;
import com.vendingmachine.inventory.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class KafkaConsumerService {

    private static final Logger logger = LoggerFactory.getLogger(KafkaConsumerService.class);

    private final InventoryService inventoryService;
    private final ProcessedEventRepository processedEventRepository;

    public KafkaConsumerService(InventoryService inventoryService, ProcessedEventRepository processedEventRepository) {
        this.inventoryService = inventoryService;
        this.processedEventRepository = processedEventRepository;
    }

    @KafkaListener(topics = "dispensing-events", groupId = "inventory-group")
    @Transactional
    public void consumeDispensingEvent(
            @Payload DispensingEvent event,
            @Header("kafka_receivedTopic") String topic,
            @Header("kafka_receivedPartitionId") int partition,
            @Header("kafka_offset") long offset) {

        logger.info("Received dispensing event: eventId={}, productId={}, quantity={}, topic={}, partition={}, offset={}",
                   event.getEventId(), event.getProductId(), event.getQuantity(), topic, partition, offset);

        // Check for duplicate event processing
        if (processedEventRepository.existsByEventIdAndEventType(event.getEventId(), "DISPENSING_EVENT")) {
            logger.warn("Duplicate dispensing event detected and skipped: eventId={}", event.getEventId());
            return;
        }

        try {
            // Process the dispensing event
            inventoryService.updateStock(event.getProductId(), -event.getQuantity());

            // Mark event as processed
            ProcessedEvent processedEvent = ProcessedEvent.builder()
                    .eventId(event.getEventId())
                    .eventType("DISPENSING_EVENT")
                    .processedAt(LocalDateTime.now())
                    .topic(topic)
                    .partition(partition)
                    .offset(offset)
                    .build();

            processedEventRepository.save(processedEvent);

            logger.info("Successfully processed dispensing event: eventId={}, productId={}",
                       event.getEventId(), event.getProductId());

        } catch (Exception e) {
            logger.error("Failed to process dispensing event: eventId={}, productId={}, error={}",
                        event.getEventId(), event.getProductId(), e.getMessage(), e);
            throw e; // Re-throw to trigger transaction rollback
        }
    }
}
