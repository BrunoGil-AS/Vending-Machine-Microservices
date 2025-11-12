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
            DispensingPayload payload = parsePayload(event.getPayload(), DispensingPayload.class);
            
            log.info("Processing unified dispensing event: {} for transaction {} product {} status {}",
                    event.getEventId(), payload.getTransactionId(), payload.getProductId(), payload.getStatus());

            // Check for duplicate event processing using domain event ID
            if (processedEventRepository.existsByEventIdAndEventType(event.getEventId(), "UNIFIED_DISPENSING_EVENT")) {
                log.warn("Unified dispensing event {} already processed, skipping", event.getEventId());
                return;
            }

            Optional<Transaction> transactionOpt = transactionRepository.findById(payload.getTransactionId());
            if (transactionOpt.isEmpty()) {
                log.warn("Transaction {} not found for dispensing event {}", payload.getTransactionId(), event.getEventId());
                return;
            }

            Transaction transaction = transactionOpt.get();

            // Only process if transaction is in PROCESSING state
            if (transaction.getStatus() != TransactionStatus.PROCESSING) {
                log.warn("Transaction {} is not in PROCESSING state (current: {}), skipping dispensing event", 
                         transaction.getId(), transaction.getStatus());
                return;
            }

            // Handle dispensing outcome
            if ("DISPENSING_FAILED".equals(event.getEventType())) {
                transaction.setStatus(TransactionStatus.FAILED);
                transactionRepository.save(transaction);
                
                kafkaEventService.publishTransactionEventWithCompleteData(transaction, "FAILED");
                log.warn("Transaction {} marked as FAILED due to dispensing failure: {}", 
                         transaction.getId(), payload.getFailureReason());
            } else if ("DISPENSING_COMPLETED".equals(event.getEventType())) {
                // Check if all items are dispensed (simplified logic)
                if (checkAllItemsDispensed(transaction)) {
                    transaction.setStatus(TransactionStatus.COMPLETED);
                    transactionRepository.save(transaction);
                    
                    kafkaEventService.publishTransactionEventWithCompleteData(transaction, "COMPLETED");
                    log.info("Transaction {} completed successfully after dispensing", transaction.getId());
                }
            } else if ("DISPENSING_PARTIAL".equals(event.getEventType())) {
                // For partial dispensing, we might want to update status but keep it processing
                log.warn("Partial dispensing for transaction {}: requested {}, dispensed {}", 
                         transaction.getId(), payload.getRequestedQuantity(), payload.getDispensedQuantity());
                // For now, we'll still mark as completed if some items were dispensed
                // In a real system, this would need more sophisticated logic
            }

            // Mark event as processed
            ProcessedEvent processedEvent = ProcessedEvent.builder()
                    .eventId(event.getEventId())
                    .eventType("UNIFIED_DISPENSING_EVENT")
                    .transactionId(payload.getTransactionId())
                    .processedAt(LocalDateTime.now())
                    .build();
            processedEventRepository.save(processedEvent);

            log.info("Successfully processed unified dispensing event: {}", event.getEventId());

        } catch (Exception e) {
            log.error("Failed to process unified dispensing event: {}", event.getEventId(), e);
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
            return objectMapper.readValue(payloadJson, payloadClass);
        } catch (Exception e) {
            log.error("Failed to parse payload for class {}: {}", payloadClass.getSimpleName(), payloadJson, e);
            throw new RuntimeException("Failed to parse event payload", e);
        }
    }
}