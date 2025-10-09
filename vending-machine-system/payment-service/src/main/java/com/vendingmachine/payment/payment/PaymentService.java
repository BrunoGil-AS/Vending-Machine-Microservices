package com.vendingmachine.payment.payment;

import com.vendingmachine.common.event.PaymentEvent;
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
public class PaymentService {

    private final PaymentTransactionRepository transactionRepository;
    private final KafkaTemplate<String, PaymentEvent> kafkaTemplate;

    @Value("${payment.simulation.success-rate:0.95}")
    private double successRate;

    @Value("${spring.kafka.topic.payment-events:payment-events}")
    private String paymentEventsTopic;

    private final Random random = new Random();

    @Transactional
    public PaymentTransaction processPayment(Double amount, PaymentMethod method) {
        log.info("Processing payment: amount={}, method={}", amount, method);

        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setAmount(amount);
        transaction.setMethod(method);
        transaction.setStatus("PENDING");

        transaction = transactionRepository.save(transaction);

        boolean success = simulatePayment(method);

        if (success) {
            transaction.setStatus("SUCCESS");
            log.info("Payment successful for transaction {}", transaction.getId());
        } else {
            transaction.setStatus("FAILED");
            log.warn("Payment failed for transaction {}", transaction.getId());
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