package com.vendingmachine.dispensing.dispensing;

import com.vendingmachine.common.event.DispensingEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Random;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DispensingService {

    private final DispensingTransactionRepository dispensingRepository;
    private final KafkaTemplate<String, DispensingEvent> kafkaTemplate;

    @Value("${dispensing.simulation.success-rate:0.98}")
    private double successRate;

    @Value("${dispensing.simulation.jam-probability:0.02}")
    private double jamProbability;

    @Value("${spring.kafka.topic.dispensing-events:dispensing-events}")
    private String dispensingEventsTopic;

    private final Random random = new Random();

    @Transactional
    public void dispenseProductsForTransaction(Long transactionId, List<DispensingItem> items) {
        log.info("Starting dispensing for transaction {} with {} items", transactionId, items.size());

        for (DispensingItem item : items) {
            DispensingTransaction dispensing = new DispensingTransaction();
            dispensing.setTransactionId(transactionId);
            dispensing.setProductId(item.getProductId());
            dispensing.setQuantity(item.getQuantity());
            dispensing.setStatus("PENDING");

            dispensing = dispensingRepository.save(dispensing);

            boolean success = simulateDispensing(item);

            if (success) {
                dispensing.setStatus("SUCCESS");
                log.info("Successfully dispensed {} units of product {} for transaction {}",
                        item.getQuantity(), item.getProductId(), transactionId);
            } else {
                dispensing.setStatus("FAILED");
                dispensing.setErrorMessage("Dispensing failed - possible jam or hardware error");
                log.warn("Failed to dispense product {} for transaction {}", item.getProductId(), transactionId);
            }

            dispensingRepository.save(dispensing);

            // Publish dispensing event
            publishDispensingEvent(dispensing);
        }

        log.info("Completed dispensing operations for transaction {}", transactionId);
    }

    private boolean simulateDispensing(DispensingItem item) {
        // Check for jam
        if (random.nextDouble() < jamProbability) {
            log.warn("Dispensing jam detected for product {}", item.getProductId());
            return false;
        }

        // Check for general failure
        return random.nextDouble() < successRate;
    }

    private void publishDispensingEvent(DispensingTransaction dispensing) {
        DispensingEvent event = new DispensingEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setProductId(dispensing.getProductId());
        event.setQuantity(dispensing.getQuantity());
        event.setTimestamp(System.currentTimeMillis());

        kafkaTemplate.send(dispensingEventsTopic, event.getEventId(), event);
        log.info("Published dispensing event: {}", event);
    }

    public List<DispensingTransaction> getAllDispensingTransactions() {
        return dispensingRepository.findAll();
    }

    public List<DispensingTransaction> getDispensingTransactionsByTransactionId(Long transactionId) {
        return dispensingRepository.findByTransactionId(transactionId);
    }
}