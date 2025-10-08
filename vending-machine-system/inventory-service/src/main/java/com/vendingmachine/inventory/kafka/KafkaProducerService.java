package com.vendingmachine.inventory.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CompletableFuture;

@Service
public class KafkaProducerService {

    private static final Logger logger = LoggerFactory.getLogger(KafkaProducerService.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public KafkaProducerService(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Transactional
    public void send(String topic, Object payload) {
        try {
            CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, payload);
            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    logger.error("Failed to send message to topic {}: {}", topic, ex.getMessage(), ex);
                } else {
                    logger.debug("Message sent successfully to topic {} partition {} offset {}",
                               topic, result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                }
            });
        } catch (Exception e) {
            logger.error("Error sending message to topic {}: {}", topic, e.getMessage(), e);
            throw e;
        }
    }
}
