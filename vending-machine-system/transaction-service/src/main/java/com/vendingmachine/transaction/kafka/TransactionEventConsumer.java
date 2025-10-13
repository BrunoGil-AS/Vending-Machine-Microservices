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
        log.info("Received dispensing event: {} for product {}", event.getEventId(), event.getProductId());

        // Check for duplicate event processing
        if (processedEventRepository.existsByEventIdAndEventType(event.getEventId(), "DISPENSING_EVENT")) {
            log.warn("Dispensing event {} already processed, skipping", event.getEventId());
            return;
        }

        try {
            // Find transactions that contain this product
            // Note: DispensingEvent doesn't have transactionId, so we need to find by product
            // This is a simplified approach - in production, dispensing events should include transactionId
            Optional<Transaction> transactionOpt = findTransactionByProductId(event.getProductId());
            if (transactionOpt.isEmpty()) {
                log.warn("No active transaction found for product {} in dispensing event {}", event.getProductId(), event.getEventId());
                return;
            }

            Transaction transaction = transactionOpt.get();

            // For now, assume dispensing success and complete the transaction
            // In a real system, we'd check if all items were dispensed
            transaction.setStatus(TransactionStatus.COMPLETED);
            transactionRepository.save(transaction);

            // Publish transaction completed event
            com.vendingmachine.common.event.TransactionEvent transactionEvent =
                new com.vendingmachine.common.event.TransactionEvent(
                    "txn-" + transaction.getId() + "-" + System.currentTimeMillis(),
                    transaction.getId(),
                    "COMPLETED",
                    transaction.getTotalAmount().doubleValue(),
                    System.currentTimeMillis()
                );
            kafkaEventService.publishTransactionEvent(transactionEvent);

            // Mark event as processed
            ProcessedEvent processedEvent = ProcessedEvent.builder()
                    .eventId(event.getEventId())
                    .eventType("DISPENSING_EVENT")
                    .transactionId(transaction.getId())
                    .processedAt(LocalDateTime.now())
                    .build();
            processedEventRepository.save(processedEvent);

            log.info("Successfully processed dispensing event: {} for transaction {}", event.getEventId(), transaction.getId());

        } catch (Exception e) {
            log.error("Failed to process dispensing event: {}", event.getEventId(), e);
            throw new RuntimeException("Failed to process dispensing event", e);
        }
    }

    private Optional<Transaction> findTransactionByProductId(Long productId) {
        // Find the most recent processing transaction that contains this product
        // This is a simplified implementation - in production, dispensing events should include transactionId
        return transactionRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(t -> t.getStatus() == TransactionStatus.PROCESSING)
                .filter(t -> t.getItems().stream().anyMatch(item -> item.getProductId().equals(productId)))
                .findFirst();
    }
}