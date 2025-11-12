package com.vendingmachine.dispensing.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vendingmachine.dispensing.entity.FailedEvent;
import com.vendingmachine.dispensing.repository.FailedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaErrorHandler implements ConsumerRecordRecoverer {

    private final FailedEventRepository failedEventRepository;
    @Qualifier("dlqKafkaProducer")
    private final KafkaProducer<String, Object> kafkaProducer;
    private final ObjectMapper objectMapper;

    @Override
    public void accept(ConsumerRecord<?, ?> record, Exception exception) {
        String eventId = UUID.randomUUID().toString();
        String originalTopic = record.topic();
        String dlqTopic = originalTopic + "-dlq";

        try {
            // Log the error
            log.error("Error processing Kafka message from topic {}, partition {}, offset {}: {}", 
                     originalTopic, record.partition(), record.offset(), exception.getMessage(), exception);

            // Save failed event to database for critical tracking
            saveFailedEventToDatabase(record, exception, eventId);

            // Send to DLQ topic
            sendToDlq(record, dlqTopic, eventId, exception);

        } catch (Exception dlqException) {
            log.error("Failed to process error for record from topic {}: {}", 
                     originalTopic, dlqException.getMessage(), dlqException);
        }
    }

    private void saveFailedEventToDatabase(ConsumerRecord<?, ?> record, Exception exception, String eventId) {
        try {
            FailedEvent failedEvent = FailedEvent.builder()
                    .eventId(eventId)
                    .originalTopic(record.topic())
                    .partition(record.partition())
                    .offset(record.offset())
                    .eventData(objectMapper.writeValueAsString(record.value()))
                    .errorMessage(exception.getMessage())
                    .errorType(exception.getClass().getSimpleName())
                    .retryCount(0)
                    .status("FAILED")
                    .failedAt(LocalDateTime.now())
                    .build();

            failedEventRepository.save(failedEvent);
            log.info("Failed event saved to database with ID: {}", eventId);

        } catch (Exception e) {
            log.error("Failed to save failed event to database: {}", e.getMessage(), e);
        }
    }

    private void sendToDlq(ConsumerRecord<?, ?> record, String dlqTopic, String eventId, Exception exception) {
        try {
            ProducerRecord<String, Object> dlqRecord = new ProducerRecord<>(
                    dlqTopic,
                    record.key() != null ? record.key().toString() : eventId,
                    createDlqPayload(record, exception, eventId)
            );

            kafkaProducer.send(dlqRecord, (metadata, ex) -> {
                if (ex != null) {
                    log.error("Failed to send message to DLQ topic {}: {}", dlqTopic, ex.getMessage(), ex);
                } else {
                    log.info("Message sent to DLQ topic {} successfully. Event ID: {}", dlqTopic, eventId);
                }
            });

        } catch (Exception e) {
            log.error("Error creating DLQ message for topic {}: {}", dlqTopic, e.getMessage(), e);
        }
    }

    private Object createDlqPayload(ConsumerRecord<?, ?> record, Exception exception, String eventId) {
        return DlqMessage.builder()
                .eventId(eventId)
                .originalTopic(record.topic())
                .originalPartition(record.partition())
                .originalOffset(record.offset())
                .originalValue(record.value())
                .errorMessage(exception.getMessage())
                .errorType(exception.getClass().getSimpleName())
                .failedAt(LocalDateTime.now())
                .retryCount(0)
                .build();
    }

    @lombok.Builder
    @lombok.Data
    public static class DlqMessage {
        private String eventId;
        private String originalTopic;
        private Integer originalPartition;
        private Long originalOffset;
        private Object originalValue;
        private String errorMessage;
        private String errorType;
        private LocalDateTime failedAt;
        private Integer retryCount;
    }
}