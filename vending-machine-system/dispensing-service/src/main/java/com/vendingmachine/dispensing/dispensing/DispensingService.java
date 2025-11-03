package com.vendingmachine.dispensing.dispensing;

import com.vendingmachine.common.aop.annotation.Auditable;
import com.vendingmachine.common.aop.annotation.ExecutionTime;
import com.vendingmachine.common.event.DispensingEvent;
import com.vendingmachine.common.util.CorrelationIdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import static com.vendingmachine.dispensing.dispensing.SimulationConfig.SimulationConstants.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
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

    @Value(DISPENSING_SIMULATION_ENABLED)
    private boolean simulationEnabled;

    @Value(DISPENSING_SIMULATION_VERIFICATION_RATE)
    private double verificationSuccessRate;

    @Value(SPRING_KAFKA_TOPIC_DISPENSING_EVENTS_DEFAULT)
    private String dispensingEventsTopic;

    private final Random random = new Random();

    @Transactional
    @Auditable(operation = "DISPENSE_PRODUCTS", entityType = "DispensingOperation", logParameters = true)
    @ExecutionTime(operation = "DISPENSE_PRODUCTS", warningThreshold = 2000, detailed = true)
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

    @ExecutionTime(operation = "SIMULATE_DISPENSING", warningThreshold = 500)
    private boolean simulateDispensing(DispensingItem item) {
        if (simulationEnabled) {
            log.debug("The value of simulationEnabled is: {}", simulationEnabled);
            log.debug("Dispensing simulation enabled; running jam simulation with probability: {}, success rate: {} and verification rate: {}", jamProbability, successRate, verificationSuccessRate);
            // RANDOM: jam probability
            if (random.nextDouble() < jamProbability) {
                log.warn("Dispensing jam detected for product {}", item.getProductId());
                hardwareStatusService.reportHardwareError("product_chute", "Jam detected during dispensing");
                return false;
            }
            boolean dispensed = random.nextDouble() < successRate;
            if (dispensed) {
                // 2) Verification simulation
                if (random.nextDouble() < verificationSuccessRate) {
                    log.debug("Dispensing verification succeeded for product {}", item.getProductId());
                    return true;
                } else {
                    log.warn("Dispensing verification failed for product {}", item.getProductId());
                    hardwareStatusService.reportHardwareError("sensor_array", "Verification failed after dispensing");
                    return false;
                }
            } else {
                log.warn("Dispensing failed for product {} due to simulated hardware failure", item.getProductId());
                return false;
            }
        } else {
            log.debug("Dispensing simulation disabled; skipping jam simulation");
            // When simulation is disabled, assume no jam occurs
            // deterministic behavior: do nothing here
        }

        return true; // Default to success if simulation is disabled
    }

    @Auditable(operation = "PUBLISH_DISPENSING_EVENT", entityType = "DispensingEvent", logParameters = true)
    @ExecutionTime(operation = "PUBLISH_DISPENSING_EVENT", warningThreshold = 1000)
    private void publishDispensingEvent(DispensingOperation dispensing) {
        DispensingEvent event = new DispensingEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setTransactionId(dispensing.getTransactionId());
        event.setProductId(dispensing.getProductId());
        event.setQuantity(dispensing.getQuantity());
        event.setStatus(dispensing.getStatus()); // SUCCESS or FAILED
        event.setTimestamp(System.currentTimeMillis());

        Message<DispensingEvent> message = MessageBuilder
            .withPayload(event)
            .setHeader("X-Correlation-ID", CorrelationIdUtil.getCorrelationId())
            .build();

        kafkaTemplate.send(dispensingEventsTopic, event.getEventId(), message.getPayload());
        log.info("Published dispensing event: {}", event);
    }

    @ExecutionTime(operation = "GET_ALL_DISPENSING_TRANSACTIONS", warningThreshold = 800)
    public List<DispensingOperation> getAllDispensingTransactions() {
        return dispensingRepository.findAll();
    }

    @ExecutionTime(operation = "GET_DISPENSING_BY_TRANSACTION", warningThreshold = 600)
    public List<DispensingOperation> getDispensingTransactionsByTransactionId(Long transactionId) {
        return dispensingRepository.findByTransactionId(transactionId);
    }
}