package com.vendingmachine.payment.payment;

import com.vendingmachine.common.event.PaymentEvent;
import com.vendingmachine.common.event.TransactionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import static com.vendingmachine.payment.payment.SimulationConfig.SimulationConstants.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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

    @Value(PAYMENT_SIMULATION_SUCCESS_RATE)
    private double successRate;

    @Value(PAYMENT_SIMULATION_ENABLED_DEFAULT)
    private boolean simulationEnabled;

    @Value(SPRING_KAFKA_TOPIC_PAYMENT_EVENTS_DEFAULT)
    private String paymentEventsTopic;

    private final Random random = new Random();

    @Transactional
    public PaymentTransaction processPaymentForTransaction(TransactionEvent transactionEvent, PaymentRequest paymentRequest) {
        return processPaymentForTransaction(transactionEvent, paymentRequest, false);
    }
    
    @Transactional
    public PaymentTransaction processPaymentForTransaction(TransactionEvent transactionEvent, PaymentRequest paymentRequest, boolean publishEvent) {
        log.info("Processing payment for transaction: {} with amount {} and method {}",
                transactionEvent.getTransactionId(), transactionEvent.getTotalAmount(), paymentRequest.getPaymentMethod());

        // Check if payment already exists for this transaction
        Optional<PaymentTransaction> existingPayment = transactionRepository.findByTransactionId(transactionEvent.getTransactionId());
        if (existingPayment.isPresent()) {
            log.warn("Payment already exists for transaction {}", transactionEvent.getTransactionId());
            return existingPayment.get();
        }

        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setTransactionId(transactionEvent.getTransactionId());
        transaction.setAmount(transactionEvent.getTotalAmount());
        transaction.setMethod(paymentRequest.getPaymentMethod());
        transaction.setStatus("PENDING");

        transaction = transactionRepository.save(transaction);

        boolean success = simulatePayment(paymentRequest);

        if (success) {
            transaction.setStatus("SUCCESS");
            log.info("Payment successful for transaction {}", transactionEvent.getTransactionId());
        } else {
            transaction.setStatus("FAILED");
            log.warn("Payment failed for transaction {}", transactionEvent.getTransactionId());
        }

        transaction = transactionRepository.save(transaction);

        // Only publish event when processing from Kafka (async flow)
        if (publishEvent) {
            publishPaymentEvent(transaction);
        }

        return transaction;
    }

    @Transactional
    public PaymentTransaction processPaymentForTransaction(TransactionEvent transactionEvent) {
        // Backward compatibility method for Kafka events - defaults to credit card and publishes events
        PaymentRequest defaultRequest = new PaymentRequest();
        defaultRequest.setTransactionId(transactionEvent.getTransactionId());
        defaultRequest.setPaymentMethod(PaymentMethod.CREDIT_CARD);
        defaultRequest.setAmount(BigDecimal.valueOf(transactionEvent.getTotalAmount()));
        return processPaymentForTransaction(transactionEvent, defaultRequest, true); // Enable event publishing for async flow
    }

    private boolean simulatePayment(PaymentRequest paymentRequest) {
        PaymentMethod method = paymentRequest.getPaymentMethod();

        if (method == PaymentMethod.CASH) {
            // Cash payments - validate that paid amount covers the transaction amount
            BigDecimal paidAmount = paymentRequest.getPaidAmount();
            BigDecimal transactionAmount = paymentRequest.getAmount();
            return paidAmount != null && paidAmount.compareTo(transactionAmount) >= 0;
        } else {
            // Card payments have configurable success rate
            // RANDOM: if simulationEnabled is true, use random success rate
            if (simulationEnabled) {
                return random.nextDouble() < successRate;
            } else {
                // When simulation is disabled, force deterministic success for card payments
                return true;
            }
        }
    }

    private void publishPaymentEvent(PaymentTransaction transaction) {
        PaymentEvent event = new PaymentEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setTransactionId(transaction.getTransactionId());
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