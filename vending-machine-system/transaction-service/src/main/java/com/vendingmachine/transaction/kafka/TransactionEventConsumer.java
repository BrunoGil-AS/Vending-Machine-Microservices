package com.vendingmachine.transaction.kafka;

import com.vendingmachine.common.event.DispensingEvent;
import com.vendingmachine.common.event.PaymentEvent;
import com.vendingmachine.transaction.transaction.ProcessedEvent;
import com.vendingmachine.transaction.transaction.ProcessedEventRepository;
import com.vendingmachine.transaction.transaction.Transaction;
import com.vendingmachine.transaction.transaction.TransactionRepository;
import com.vendingmachine.transaction.transaction.TransactionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionEventConsumer {

    private final TransactionRepository transactionRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final KafkaEventService kafkaEventService;

    @KafkaListener(topics = "payment-events", groupId = "transaction-service-group",
                   containerFactory = "paymentEventKafkaListenerContainerFactory")
    @Transactional
    public void consumePaymentEvent(PaymentEvent event) {
        log.info("Received payment event: {} for transaction {}", event.getEventId(), event.getTransactionId());

        // Check for duplicate event processing
        if (processedEventRepository.existsByEventIdAndEventType(event.getEventId(), "PAYMENT_EVENT")) {
            log.warn("Payment event {} already processed, skipping", event.getEventId());
            return;
        }

        try {
            Optional<Transaction> transactionOpt = transactionRepository.findById(event.getTransactionId());
            if (transactionOpt.isEmpty()) {
                log.error("Transaction {} not found for payment event {}", event.getTransactionId(), event.getEventId());
                return;
            }

            Transaction transaction = transactionOpt.get();

            // Update transaction status based on payment result
            if ("SUCCESS".equals(event.getStatus())) {
                // Payment successful - move to processing state
                transaction.setStatus(TransactionStatus.PROCESSING);
                log.info("Payment successful for transaction {}, moving to PROCESSING", transaction.getId());
            } else if ("FAILED".equals(event.getStatus())) {
                // Payment failed - cancel transaction
                transaction.setStatus(TransactionStatus.CANCELLED);
                log.info("Payment failed for transaction {}, cancelling transaction", transaction.getId());
            }

            transactionRepository.save(transaction);

            // Mark event as processed
            ProcessedEvent processedEvent = ProcessedEvent.builder()
                    .eventId(event.getEventId())
                    .eventType("PAYMENT_EVENT")
                    .transactionId(event.getTransactionId())
                    .processedAt(LocalDateTime.now())
                    .build();
            processedEventRepository.save(processedEvent);

            log.info("Successfully processed payment event: {}", event.getEventId());

        } catch (Exception e) {
            log.error("Failed to process payment event: {}", event.getEventId(), e);
            throw new RuntimeException("Failed to process payment event", e);
        }
    }

    @KafkaListener(topics = "dispensing-events", groupId = "transaction-service-group",
                   containerFactory = "dispensingEventKafkaListenerContainerFactory")
    @Transactional
    public void consumeDispensingEvent(DispensingEvent event) {
        log.info("Received dispensing event: {} for transaction {} product {} status {}",
                event.getEventId(), event.getTransactionId(), event.getProductId(), event.getStatus());

        // Check for duplicate event processing
        if (processedEventRepository.existsByEventIdAndEventType(event.getEventId(), "DISPENSING_EVENT")) {
            log.warn("Dispensing event {} already processed, skipping", event.getEventId());
            return;
        }

        try {
            Optional<Transaction> transactionOpt = transactionRepository.findById(event.getTransactionId());
            if (transactionOpt.isEmpty()) {
                log.error("Transaction {} not found for dispensing event {}", event.getTransactionId(), event.getEventId());
                return;
            }

            Transaction transaction = transactionOpt.get();

            // Only process if transaction is in PROCESSING state
            if (transaction.getStatus() != TransactionStatus.PROCESSING) {
                log.warn("Transaction {} is not in PROCESSING state (current: {}), ignoring dispensing event",
                        event.getTransactionId(), transaction.getStatus());
                return;
            }

            // If dispensing failed, mark transaction as failed
            if ("FAILED".equals(event.getStatus())) {
                transaction.setStatus(TransactionStatus.FAILED);
                transactionRepository.save(transaction);
                log.error("Dispensing failed for transaction {}, marking as FAILED", event.getTransactionId());

                // Publish transaction failed event
                com.vendingmachine.common.event.TransactionEvent transactionEvent =
                    new com.vendingmachine.common.event.TransactionEvent(
                        "txn-failed-" + transaction.getId() + "-" + System.currentTimeMillis(),
                        transaction.getId(),
                        "FAILED",
                        transaction.getTotalAmount().doubleValue(),
                        System.currentTimeMillis()
                    );
                kafkaEventService.publishTransactionEvent(transactionEvent);
            } else {
                // Check if all items in the transaction have been successfully dispensed
                boolean allItemsDispensed = checkAllItemsDispensed(transaction);

                if (allItemsDispensed) {
                    transaction.setStatus(TransactionStatus.COMPLETED);
                    transactionRepository.save(transaction);
                    log.info("All items dispensed successfully for transaction {}, marking as COMPLETED", event.getTransactionId());

                    // Publish transaction completed event
                    com.vendingmachine.common.event.TransactionEvent transactionEvent =
                        new com.vendingmachine.common.event.TransactionEvent(
                            "txn-completed-" + transaction.getId() + "-" + System.currentTimeMillis(),
                            transaction.getId(),
                            "COMPLETED",
                            transaction.getTotalAmount().doubleValue(),
                            System.currentTimeMillis()
                        );
                    kafkaEventService.publishTransactionEvent(transactionEvent);
                } else {
                    log.debug("Transaction {} still has pending items to dispense", event.getTransactionId());
                }
            }

            // Mark event as processed
            ProcessedEvent processedEvent = ProcessedEvent.builder()
                    .eventId(event.getEventId())
                    .eventType("DISPENSING_EVENT")
                    .transactionId(event.getTransactionId())
                    .processedAt(LocalDateTime.now())
                    .build();
            processedEventRepository.save(processedEvent);

            log.info("Successfully processed dispensing event: {}", event.getEventId());

        } catch (Exception e) {
            log.error("Failed to process dispensing event: {}", event.getEventId(), e);
            throw new RuntimeException("Failed to process dispensing event", e);
        }
    }

    private boolean checkAllItemsDispensed(Transaction transaction) {
        // This is a simplified check - in a real system, we'd query the dispensing service
        // or maintain a dispensing status in the transaction service
        // For now, we'll assume that if we receive a dispensing event for any item,
        // and the transaction is still processing, we consider it complete
        // This should be improved with proper dispensing status tracking
        return true; // Simplified - assume complete after first successful dispensing event
    }
}