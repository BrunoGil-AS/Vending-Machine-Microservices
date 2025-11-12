package com.vendingmachine.dispensing.dispensing;

import com.vendingmachine.common.aop.annotation.Auditable;
import com.vendingmachine.common.aop.annotation.ExecutionTime;
import com.vendingmachine.dispensing.exception.HardwareException;
import com.vendingmachine.dispensing.kafka.DispensingKafkaEventService;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import static com.vendingmachine.dispensing.dispensing.SimulationConfig.SimulationConstants.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class DispensingService {

    private final DispensingOperationRepository dispensingRepository;
    private final HardwareStatusService hardwareStatusService;
    private final DispensingKafkaEventService dispensingKafkaEventService;

    @Value(DISPENSING_SIMULATION_SUCCESS_RATE)
    private double successRate;

    @Value(DISPENSING_SIMULATION_JAM_PROBABILITY)
    private double jamProbability;

    @Value(DISPENSING_SIMULATION_ENABLED)
    private boolean simulationEnabled;

    @Value(DISPENSING_SIMULATION_VERIFICATION_RATE)
    private double verificationSuccessRate;

    private final Random random = new Random();

    @Transactional
    @Bulkhead(name = "hardware-operations", fallbackMethod = "dispenseProductsFallback", type = Bulkhead.Type.SEMAPHORE)
    @Auditable(operation = "DISPENSE_PRODUCTS", entityType = "DispensingOperation", logParameters = true)
    @ExecutionTime(operation = "DISPENSE_PRODUCTS", warningThreshold = 2000, detailed = true)
    public void dispenseProductsForTransaction(Long transactionId, List<DispensingItem> items) {
        log.info("Starting dispensing for transaction {} with {} items", transactionId, items.size());

        // Check hardware status before dispensing
        if (!hardwareStatusService.isHardwareOperational()) {
            log.error("Hardware is not operational, cannot dispense for transaction {}", transactionId);
            throw HardwareException.hardwareNotOperational();
        }

        for (DispensingItem item : items) {
            DispensingOperation dispensing = new DispensingOperation();
            dispensing.setTransactionId(transactionId);
            dispensing.setProductId(item.getProductId());
            dispensing.setQuantity(item.getQuantity());
            dispensing.setStatus("PENDING");

            dispensing = dispensingRepository.save(dispensing);

            try {
                boolean success = simulateDispensing(item);
                
                if (success) {
                    dispensing.setStatus("SUCCESS");
                    log.info("Successfully dispensed {} units of product {} for transaction {}",
                            item.getQuantity(), item.getProductId(), transactionId);
                } else {
                    dispensing.setStatus("FAILED");
                    dispensing.setErrorMessage("Dispensing failed - unknown hardware error");
                    log.warn("Failed to dispense product {} for transaction {}", item.getProductId(), transactionId);
                }
            } catch (HardwareException e) {
                dispensing.setStatus("FAILED");
                dispensing.setErrorMessage(String.format("Hardware failure: %s [Component: %s, Operation: %s]", 
                        e.getMessage(), e.getHardwareComponent(), e.getOperationType()));
                log.error("Hardware exception during dispensing for product {} in transaction {}: {}", 
                        item.getProductId(), transactionId, e.toString());
                // Report hardware error
                hardwareStatusService.reportHardwareError(e.getHardwareComponent(), e.getMessage());
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
                throw HardwareException.productJam(item.getProductId());
            }
            
            boolean dispensed = random.nextDouble() < successRate;
            if (dispensed) {
                // Verification simulation
                if (random.nextDouble() < verificationSuccessRate) {
                    log.debug("Dispensing verification succeeded for product {}", item.getProductId());
                    return true;
                } else {
                    log.warn("Dispensing verification failed for product {}", item.getProductId());
                    hardwareStatusService.reportHardwareError("sensor_array", "Verification failed after dispensing");
                    throw HardwareException.verificationFailure(item.getProductId());
                }
            } else {
                log.warn("Dispensing failed for product {} due to simulated hardware failure", item.getProductId());
                throw HardwareException.dispensingMotorFailure(item.getProductId());
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
        // Use enhanced service with complete payload data for unified topic
        dispensingKafkaEventService.publishDispensingEventWithCompleteData(dispensing, dispensing.getStatus());
        log.info("Published dispensing event with complete data: transaction {}, status {}, product {}", 
                dispensing.getTransactionId(), dispensing.getStatus(), dispensing.getProductId());
    }

    @Bulkhead(name = "database-operations", fallbackMethod = "getAllDispensingTransactionsFallback", type = Bulkhead.Type.SEMAPHORE)
    @ExecutionTime(operation = "GET_ALL_DISPENSING_TRANSACTIONS", warningThreshold = 800)
    public List<DispensingOperation> getAllDispensingTransactions() {
        return dispensingRepository.findAll();
    }

    @Bulkhead(name = "database-operations", fallbackMethod = "getDispensingTransactionsByTransactionIdFallback", type = Bulkhead.Type.SEMAPHORE)
    @ExecutionTime(operation = "GET_DISPENSING_BY_TRANSACTION", warningThreshold = 600)
    public List<DispensingOperation> getDispensingTransactionsByTransactionId(Long transactionId) {
        return dispensingRepository.findByTransactionId(transactionId);
    }

    // Fallback methods for Bulkhead pattern

    /**
     * Fallback method when hardware operations bulkhead is full
     */
    private void dispenseProductsFallback(Long transactionId, List<DispensingItem> items, Exception ex) {
        log.error("Hardware operations bulkhead full for transaction: {}. Error: {}", 
                transactionId, ex.getMessage());
        log.warn("Dispensing service hardware operations at capacity - rejecting transaction {}", transactionId);
        
        // Create failed dispensing operations for all items
        for (DispensingItem item : items) {
            DispensingOperation failedDispensing = new DispensingOperation();
            failedDispensing.setTransactionId(transactionId);
            failedDispensing.setProductId(item.getProductId());
            failedDispensing.setQuantity(item.getQuantity());
            failedDispensing.setStatus("FAILED_CAPACITY");
            failedDispensing.setErrorMessage("Dispensing service at capacity - hardware operations bulkhead full");
            
            dispensingRepository.save(failedDispensing);
            publishDispensingEvent(failedDispensing);
        }
    }

    /**
     * Fallback method when database operations bulkhead is full
     */
    private List<DispensingOperation> getAllDispensingTransactionsFallback(Exception ex) {
        log.error("Database operations bulkhead full for getAllDispensingTransactions. Error: {}", ex.getMessage());
        return List.of(); // Return empty list when database is at capacity
    }

    /**
     * Fallback method when database operations bulkhead is full for specific transaction
     */
    private List<DispensingOperation> getDispensingTransactionsByTransactionIdFallback(Long transactionId, Exception ex) {
        log.error("Database operations bulkhead full for transaction {}. Error: {}", transactionId, ex.getMessage());
        return List.of(); // Return empty list when database is at capacity
    }
}