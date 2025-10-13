package com.vendingmachine.payment.kafka;

import com.vendingmachine.common.event.TransactionEvent;
import com.vendingmachine.payment.payment.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionEventConsumer {

    private final PaymentService paymentService;

    @KafkaListener(topics = "transaction-events", groupId = "payment-service-group",
                   containerFactory = "transactionEventKafkaListenerContainerFactory")
    @Transactional
    public void consumeTransactionEvent(TransactionEvent event) {
        log.info("Received transaction event: {} for transaction {} with status {}",
                event.getEventId(), event.getTransactionId(), event.getStatus());

        try {
            if ("STARTED".equals(event.getStatus())) {
                // Process payment for the transaction
                paymentService.processPaymentForTransaction(event);
                log.info("Payment processing initiated for transaction {}", event.getTransactionId());
            } else {
                log.debug("Ignoring transaction event with status: {}", event.getStatus());
            }
        } catch (Exception e) {
            log.error("Failed to process transaction event: {}", event.getEventId(), e);
            throw new RuntimeException("Failed to process transaction event", e);
        }
    }
}