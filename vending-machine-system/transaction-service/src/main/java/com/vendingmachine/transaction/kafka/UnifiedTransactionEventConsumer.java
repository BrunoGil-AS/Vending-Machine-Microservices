package com.vendingmachine.transaction.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vendingmachine.common.event.DomainEvent;
import com.vendingmachine.common.event.payload.PaymentPayload;
import com.vendingmachine.common.event.payload.DispensingPayload;
import com.vendingmachine.common.aop.annotation.Auditable;
import com.vendingmachine.common.aop.annotation.ExecutionTime;
import com.vendingmachine.common.util.CorrelationIdUtil;
import com.vendingmachine.transaction.transaction.ProcessedEvent;
import com.vendingmachine.transaction.transaction.ProcessedEventRepository;
import com.vendingmachine.transaction.transaction.Transaction;
import com.vendingmachine.transaction.transaction.TransactionRepository;
import com.vendingmachine.transaction.transaction.TransactionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UnifiedTransactionEventConsumer {

    private final TransactionRepository transactionRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final KafkaEventService kafkaEventService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @KafkaListener(topics = "vending-machine-domain-events", groupId = "transaction-service-unified-group",
                   containerFactory = "domainEventKafkaListenerContainerFactory")
    @Transactional
    @Auditable(operation = "CONSUME_UNIFIED_EVENT", entityType = "DomainEvent", logParameters = true)
    @ExecutionTime(operation = "Process Unified Event", warningThreshold = 2000, detailed = true)
    public void consumeUnifiedEvent(@Payload DomainEvent event,
                                   @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key,
                                   @Header(value = "X-Correlation-ID", required = false) String correlationId) {
        
        // Set correlation ID from Kafka header or domain event
        String finalCorrelationId = correlationId != null ? correlationId : event.getCorrelationId();
        CorrelationIdUtil.setCorrelationId(finalCorrelationId);
        
        try {
            log.info("Received unified event: {} from {} with type {}", 
                    event.getEventId(), event.getSource(), event.getEventType());

            // Route event based on type and source
            routeEvent(event);
            
        } catch (Exception e) {
            log.error("Failed to process unified event: {} of type {}", event.getEventId(), event.getEventType(), e);
            throw new RuntimeException("Failed to process unified event", e);
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }

    /**
     * Smart routing based on event type and source
     */
    private void routeEvent(DomainEvent event) {
        String eventType = event.getEventType();
        String source = event.getSource();
        
        try {
            switch (eventType) {
                // Payment Events
                case "PAYMENT_COMPLETED":
                case "PAYMENT_FAILED":
                    handlePaymentEvent(event);
                    break;
                    
                // Dispensing Events
                case "DISPENSING_SUCCESS":
                case "DISPENSING_COMPLETED":
                case "DISPENSING_FAILED":
                case "DISPENSING_PARTIAL":
                    handleDispensingEvent(event);
                    break;
                    
                default:
                    log.debug("Ignoring event type: {} from source: {} (not relevant for transaction service)", eventType, source);
            }
        } catch (Exception e) {
            log.error("Failed to route event {} of type {}", event.getEventId(), eventType, e);
            throw e;
        }
    }

    /**
     * Handle payment events with enhanced payload from unified topic
     */
    private void handlePaymentEvent(DomainEvent event) {
        try {
            PaymentPayload payload = parsePayload(event.getPayload(), PaymentPayload.class);
            
            log.info("Processing unified payment event: {} for transaction {}", event.getEventId(), payload.getTransactionId());

            // Check for duplicate event processing using domain event ID
            if (processedEventRepository.existsByEventIdAndEventType(event.getEventId(), "UNIFIED_PAYMENT_EVENT")) {
                log.warn("Unified payment event {} already processed, skipping", event.getEventId());
                return;
            }

            Optional<Transaction> transactionOpt = transactionRepository.findById(payload.getTransactionId());
            if (transactionOpt.isEmpty()) {
                log.warn("Transaction {} not found for payment event {}", payload.getTransactionId(), event.getEventId());
                return;
            }

            Transaction transaction = transactionOpt.get();

            // Update transaction status based on payment result
            if ("PAYMENT_COMPLETED".equals(event.getEventType())) {
                if (transaction.getStatus() == TransactionStatus.PENDING) {
                    transaction.setStatus(TransactionStatus.PROCESSING);
                    transactionRepository.save(transaction);
                    
                    // Publish PROCESSING event to trigger dispensing
                    kafkaEventService.publishTransactionEventWithCompleteData(transaction, "PROCESSING");
                    log.info("Transaction {} moved to PROCESSING after payment success", transaction.getId());
                }
            } else if ("PAYMENT_FAILED".equals(event.getEventType())) {
                transaction.setStatus(TransactionStatus.FAILED);
                transactionRepository.save(transaction);
                
                kafkaEventService.publishTransactionEventWithCompleteData(transaction, "FAILED");
                log.warn("Transaction {} marked as FAILED due to payment failure", transaction.getId());
            }

            // Mark event as processed
            ProcessedEvent processedEvent = ProcessedEvent.builder()
                    .eventId(event.getEventId())
                    .eventType("UNIFIED_PAYMENT_EVENT")
                    .transactionId(payload.getTransactionId())
                    .processedAt(LocalDateTime.now())
                    .build();
            processedEventRepository.save(processedEvent);

            log.info("Successfully processed unified payment event: {}", event.getEventId());

        } catch (Exception e) {
            log.error("Failed to process unified payment event: {}", event.getEventId(), e);
            throw new RuntimeException("Failed to process unified payment event", e);
        }
    }

    /**
     * Handle dispensing events with enhanced payload from unified topic
     */
    private void handleDispensingEvent(DomainEvent event) {
        try {
            log.info("Starting to process dispensing event: {}", event.getEventId());
            log.debug("Raw event payload: {}", event.getPayload());
            
            DispensingPayload payload = parsePayload(event.getPayload(), DispensingPayload.class);
            
            log.info("Processing unified dispensing event: {} for transaction {} product {} status {}",
                    event.getEventId(), payload.getTransactionId(), payload.getProductId(), payload.getStatus());
            
            // Detailed payload validation
            if (payload.getTransactionId() == null) {
                log.error("Transaction ID is null in dispensing payload: {}", event.getPayload());
                throw new IllegalArgumentException("Transaction ID cannot be null");
            }
            
            log.debug("Payload details: transactionId={}, productId={}, dispensedQuantity={}, status={}", 
                     payload.getTransactionId(), payload.getProductId(), payload.getDispensedQuantity(), payload.getStatus());

            // Check for duplicate event processing using domain event ID
            if (processedEventRepository.existsByEventIdAndEventType(event.getEventId(), "UNIFIED_DISPENSING_EVENT")) {
                log.warn("Unified dispensing event {} already processed, skipping", event.getEventId());
                return;
            }

            log.debug("Looking for transaction with ID: {}", payload.getTransactionId());
            Optional<Transaction> transactionOpt = transactionRepository.findById(payload.getTransactionId());
            if (transactionOpt.isEmpty()) {
                log.warn("Transaction {} not found for dispensing event {}", payload.getTransactionId(), event.getEventId());
                return;
            }

            Transaction transaction = transactionOpt.get();
            log.debug("Found transaction: id={}, status={}, totalAmount={}", 
                     transaction.getId(), transaction.getStatus(), transaction.getTotalAmount());

            // Only process if transaction is in PROCESSING state
            if (transaction.getStatus() != TransactionStatus.PROCESSING) {
                log.warn("Transaction {} is not in PROCESSING state (current: {}), skipping dispensing event", 
                         transaction.getId(), transaction.getStatus());
                return;
            }

            log.info("Transaction {} is in PROCESSING state, proceeding with dispensing event handling", transaction.getId());

            // Handle dispensing outcome
            if ("DISPENSING_FAILED".equals(event.getEventType())) {
                log.warn("Processing DISPENSING_FAILED event for transaction {}", transaction.getId());
                transaction.setStatus(TransactionStatus.FAILED);
                transaction = transactionRepository.save(transaction);
                log.info("Transaction {} status updated to FAILED", transaction.getId());
                
                kafkaEventService.publishTransactionEventWithCompleteData(transaction, "FAILED");
                log.warn("Transaction {} marked as FAILED due to dispensing failure: {}", 
                         transaction.getId(), payload.getFailureReason());
            } else if ("DISPENSING_COMPLETED".equals(event.getEventType()) || "DISPENSING_SUCCESS".equals(event.getEventType())) {
                log.info("Processing DISPENSING_SUCCESS/COMPLETED event for transaction {}", transaction.getId());
                
                // Check if all items are dispensed (simplified logic)
                boolean allItemsDispensed = checkAllItemsDispensed(transaction);
                log.debug("All items dispensed check result: {}", allItemsDispensed);
                
                if (allItemsDispensed) {
                    log.info("All items dispensed for transaction {}, updating status to COMPLETED", transaction.getId());
                    transaction.setStatus(TransactionStatus.COMPLETED);
                    transaction = transactionRepository.save(transaction);
                    log.info("Transaction {} successfully saved with COMPLETED status", transaction.getId());
                    
                    kafkaEventService.publishTransactionEventWithCompleteData(transaction, "COMPLETED");
                    log.info("Transaction {} completed successfully after dispensing", transaction.getId());
                } else {
                    log.debug("Not all items dispensed yet for transaction {}", transaction.getId());
                }
            } else if ("DISPENSING_PARTIAL".equals(event.getEventType())) {
                // For partial dispensing, we might want to update status but keep it processing
                log.warn("Partial dispensing for transaction {}: requested {}, dispensed {}", 
                         transaction.getId(), payload.getRequestedQuantity(), payload.getDispensedQuantity());
                // For now, we'll still mark as completed if some items were dispensed
                // In a real system, this would need more sophisticated logic
            }

            log.debug("Creating ProcessedEvent record for event: {}", event.getEventId());
            // Mark event as processed
            ProcessedEvent processedEvent = ProcessedEvent.builder()
                    .eventId(event.getEventId())
                    .eventType("UNIFIED_DISPENSING_EVENT")
                    .transactionId(payload.getTransactionId())
                    .processedAt(LocalDateTime.now())
                    .build();
            processedEventRepository.save(processedEvent);
            log.debug("ProcessedEvent saved successfully for event: {}", event.getEventId());

            log.info("Successfully processed unified dispensing event: {}", event.getEventId());

        } catch (Exception e) {
            log.error("Failed to process unified dispensing event: {} - Error details: {}", event.getEventId(), e.getMessage(), e);
            log.error("Event details - eventId: {}, eventType: {}, source: {}, aggregateId: {}", 
                     event.getEventId(), event.getEventType(), event.getSource(), event.getAggregateId());
            throw new RuntimeException("Failed to process unified dispensing event", e);
        }
    }

    /**
     * Check if all items in transaction are dispensed
     * Simplified logic - in real system would track individual item dispensing
     */
    private boolean checkAllItemsDispensed(Transaction transaction) {
        // Simplified assumption: if we get a dispensing completed event, 
        // all items for that transaction are considered dispensed
        // Real implementation would track individual items
        return true;
    }

    /**
     * Parse JSON payload to specific payload class
     */
    private <T> T parsePayload(String payloadJson, Class<T> payloadClass) {
        try {
            log.debug("Attempting to parse payload for class {}: {}", payloadClass.getSimpleName(), payloadJson);
            
            if (payloadJson == null || payloadJson.trim().isEmpty()) {
                log.error("Payload JSON is null or empty for class {}", payloadClass.getSimpleName());
                throw new IllegalArgumentException("Payload JSON cannot be null or empty");
            }
            
            T result = objectMapper.readValue(payloadJson, payloadClass);
            log.debug("Successfully parsed payload for class {}: {}", payloadClass.getSimpleName(), result);
            
            return result;
        } catch (Exception e) {
            log.error("Failed to parse payload for class {}: {}", payloadClass.getSimpleName(), payloadJson, e);
            log.error("Parse error details: {}", e.getMessage());
            throw new RuntimeException("Failed to parse event payload", e);
        }
    }
}