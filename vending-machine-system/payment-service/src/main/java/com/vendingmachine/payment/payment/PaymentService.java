package com.vendingmachine.payment.payment;

import com.vendingmachine.common.event.PaymentEvent;
import com.vendingmachine.common.event.TransactionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentTransactionRepository transactionRepository;
    private final KafkaTemplate<String, PaymentEvent> kafkaTemplate;

    @Value("${payment.simulation.success-rate:0.95}")
    private double successRate;

    @Value("${spring.kafka.topic.payment-events:payment-events}")
    private String paymentEventsTopic;

    private final Random random = new Random();

    @Transactional
    public PaymentTransaction processPaymentForTransaction(TransactionEvent transactionEvent) {
        log.info("Processing payment for transaction: {} with amount {}", transactionEvent.getTransactionId(), transactionEvent.getTotalAmount());

        // Check if payment already exists for this transaction
        Optional<PaymentTransaction> existingPayment = transactionRepository.findByTransactionId(transactionEvent.getTransactionId());
        if (existingPayment.isPresent()) {
            log.warn("Payment already exists for transaction {}", transactionEvent.getTransactionId());
            return existingPayment.get();
        }

        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setTransactionId(transactionEvent.getTransactionId());
        transaction.setAmount(transactionEvent.getTotalAmount());
        transaction.setMethod(PaymentMethod.CREDIT_CARD); // Default to credit card for vending machines
        transaction.setStatus("PENDING");

        transaction = transactionRepository.save(transaction);

        boolean success = simulatePayment(PaymentMethod.CREDIT_CARD);

        if (success) {
            transaction.setStatus("SUCCESS");
            log.info("Payment successful for transaction {}", transactionEvent.getTransactionId());
        } else {
            transaction.setStatus("FAILED");
            log.warn("Payment failed for transaction {}", transactionEvent.getTransactionId());
        }

        transaction = transactionRepository.save(transaction);

        // Publish event
        publishPaymentEvent(transaction);

        return transaction;
    }

    private boolean simulatePayment(PaymentMethod method) {
        if (method == PaymentMethod.CASH) {
            // Cash is always successful for simulation
            return true;
        } else {
            // Card payments have configurable success rate
            return random.nextDouble() < successRate;
        }
    }

    private void publishPaymentEvent(PaymentTransaction transaction) {
        PaymentEvent event = new PaymentEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setTransactionId(transaction.getId());
        event.setAmount(transaction.getAmount());
        event.setMethod(transaction.getMethod().name());
        event.setStatus(transaction.getStatus());
        event.setTimestamp(System.currentTimeMillis());

        kafkaTemplate.send(paymentEventsTopic, event.getEventId(), event);
        log.info("Published payment event: {}", event);
    }

    public List<PaymentTransaction> getAllTransactions() {
        return transactionRepository.findAll();
    }
}