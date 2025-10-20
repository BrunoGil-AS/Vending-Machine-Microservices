package com.vendingmachine.dispensing.dispensing;

import com.vendingmachine.common.event.DispensingEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import static com.vendingmachine.dispensing.dispensing.SimulationConfig.SimulationConstants.*;
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

    private final DispensingOperationRepository dispensingRepository;
    private final HardwareStatusService hardwareStatusService;
    private final KafkaTemplate<String, DispensingEvent> kafkaTemplate;

    @Value(DISPENSING_SIMULATION_SUCCESS_RATE)
    private double successRate;

    @Value(DISPENSING_SIMULATION_JAM_PROBABILITY)
    private double jamProbability;

    @Value(DISPENSING_SIMULATION_ENABLED_DEFAULT)
    private boolean simulationEnabled;

    @Value(DISPENSING_SIMULATION_VERIFICATION_RATE_DEFAULT)
    private double verificationSuccessRate;

    @Value(SPRING_KAFKA_TOPIC_DISPENSING_EVENTS_DEFAULT)
    private String dispensingEventsTopic;

    private final Random random = new Random();

    @Transactional
    public void dispenseProductsForTransaction(Long transactionId, List<DispensingItem> items) {
        log.info("Starting dispensing for transaction {} with {} items", transactionId, items.size());

        // Check hardware status before dispensing
        if (!hardwareStatusService.isHardwareOperational()) {
            log.error("Hardware is not operational, cannot dispense for transaction {}", transactionId);
            // Publish failure event or handle accordingly
            return;
        }

        for (DispensingItem item : items) {
            DispensingOperation dispensing = new DispensingOperation();
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
                // Report hardware error
                hardwareStatusService.reportHardwareError("dispenser_motor", "Dispensing failure detected");
            }

            dispensingRepository.save(dispensing);

            // Publish dispensing event
            publishDispensingEvent(dispensing);
        }

        log.info("Completed dispensing operations for transaction {}", transactionId);
    }

    private boolean simulateDispensing(DispensingItem item) {
        // Simulation points below introduce randomness. Marked so they can be disabled via properties.
        // 1) Jam simulation
        if (simulationEnabled) {
            // RANDOM: jam probability
            if (random.nextDouble() < jamProbability) {
                log.warn("Dispensing jam detected for product {}", item.getProductId());
                hardwareStatusService.reportHardwareError("product_chute", "Jam detected during dispensing");
                return false;
            }
        } else {
            // When simulation is disabled, assume no jam occurs
            // deterministic behavior: do nothing here
        }

        // 2) Dispense success simulation
        boolean dispensed;
        if (simulationEnabled) {
            // RANDOM: success rate
            dispensed = random.nextDouble() < successRate;
        } else {
            // Force deterministic success when simulation is disabled
            dispensed = true;
        }

        if (dispensed) {
            // 3) Verification simulation (95% by default) — mark and disable when needed
            if (simulationEnabled) {
                // RANDOM: verification (configurable)
                boolean verified = random.nextDouble() < verificationSuccessRate;
                if (!verified) {
                    log.warn("Dispensing verification failed for product {}", item.getProductId());
                    hardwareStatusService.reportHardwareError("sensor_array", "Sensor verification failed");
                    return false;
                }
            } else {
                // When simulation is disabled, verification always succeeds
            }
        }

        return dispensed;
    }

    private void publishDispensingEvent(DispensingOperation dispensing) {
        DispensingEvent event = new DispensingEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setTransactionId(dispensing.getTransactionId());
        event.setProductId(dispensing.getProductId());
        event.setQuantity(dispensing.getQuantity());
        event.setStatus(dispensing.getStatus()); // SUCCESS or FAILED
        event.setTimestamp(System.currentTimeMillis());

        kafkaTemplate.send(dispensingEventsTopic, event.getEventId(), event);
        log.info("Published dispensing event: {}", event);
    }

    public List<DispensingOperation> getAllDispensingTransactions() {
        return dispensingRepository.findAll();
    }

    public List<DispensingOperation> getDispensingTransactionsByTransactionId(Long transactionId) {
        return dispensingRepository.findByTransactionId(transactionId);
    }
}