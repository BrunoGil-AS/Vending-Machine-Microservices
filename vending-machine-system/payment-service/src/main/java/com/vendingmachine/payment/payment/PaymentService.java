package com.vendingmachine.payment.payment;

import com.vendingmachine.common.aop.annotation.Auditable;
import com.vendingmachine.common.aop.annotation.ExecutionTime;
import com.vendingmachine.common.event.PaymentEvent;
import com.vendingmachine.common.event.TransactionEvent;
import com.vendingmachine.common.util.CorrelationIdUtil;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import static com.vendingmachine.payment.payment.SimulationConfig.SimulationConstants.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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
    @Bulkhead(name = "payment-processing", fallbackMethod = "processPaymentFallback", type = Bulkhead.Type.SEMAPHORE)
    @Auditable(operation = "PROCESS_PAYMENT_FOR_TRANSACTION", entityType = "Payment", logParameters = true)
    @ExecutionTime(operation = "PROCESS_PAYMENT_FOR_TRANSACTION", warningThreshold = 1000, detailed = true)
    public PaymentTransaction processPaymentForTransaction(TransactionEvent transactionEvent, PaymentRequest paymentRequest) {
        return processPaymentForTransaction(transactionEvent, paymentRequest, false);
    }
    
    @Transactional
    @Bulkhead(name = "payment-processing", fallbackMethod = "processPaymentWithEventFallback", type = Bulkhead.Type.SEMAPHORE)
    @Auditable(operation = "PROCESS_PAYMENT_WITH_EVENT", entityType = "Payment", logParameters = true)
    @ExecutionTime(operation = "PROCESS_PAYMENT_WITH_EVENT", warningThreshold = 1200, detailed = true)
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
    @Bulkhead(name = "kafka-processing", fallbackMethod = "processPaymentFromKafkaFallback", type = Bulkhead.Type.SEMAPHORE)
    @Auditable(operation = "PROCESS_PAYMENT_FROM_KAFKA", entityType = "Payment", logParameters = true)
    @ExecutionTime(operation = "PROCESS_PAYMENT_FROM_KAFKA", warningThreshold = 1200, detailed = true)
    public PaymentTransaction processPaymentForTransaction(TransactionEvent transactionEvent) {
        // Backward compatibility method for Kafka events - defaults to credit card and publishes events
        PaymentRequest defaultRequest = new PaymentRequest();
        defaultRequest.setTransactionId(transactionEvent.getTransactionId());
        defaultRequest.setPaymentMethod(PaymentMethod.CREDIT_CARD);
        defaultRequest.setAmount(BigDecimal.valueOf(transactionEvent.getTotalAmount()));
        return processPaymentForTransaction(transactionEvent, defaultRequest, true); // Enable event publishing for async flow
    }

    @ExecutionTime(operation = "SIMULATE_PAYMENT", warningThreshold = 300)
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

    @Auditable(operation = "PUBLISH_PAYMENT_EVENT", entityType = "PaymentEvent", logParameters = true)
    @ExecutionTime(operation = "PUBLISH_PAYMENT_EVENT", warningThreshold = 1000)
    private void publishPaymentEvent(PaymentTransaction transaction) {
        PaymentEvent event = new PaymentEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setTransactionId(transaction.getTransactionId());
        event.setAmount(transaction.getAmount());
        event.setMethod(transaction.getMethod().name());
        event.setStatus(transaction.getStatus());
        event.setTimestamp(System.currentTimeMillis());

        Message<PaymentEvent> message = MessageBuilder
            .withPayload(event)
            .setHeader("X-Correlation-ID", CorrelationIdUtil.getCorrelationId())
            .build();

        kafkaTemplate.send(paymentEventsTopic, event.getEventId(), message.getPayload());
        log.info("Published payment event: {}", event);
    }

    @ExecutionTime(operation = "GET_ALL_PAYMENT_TRANSACTIONS", warningThreshold = 800)
    public List<PaymentTransaction> getAllTransactions() {
        return transactionRepository.findAll();
    }

    @ExecutionTime(operation = "GET_PAYMENT_STATUS_FOR_TRANSACTION", warningThreshold = 500)
    public Map<String, Object> getPaymentStatusForTransaction(String transactionId) {
        log.debug("Checking payment status for transaction: {}", transactionId);
        
        Optional<PaymentTransaction> paymentOpt = transactionRepository.findByTransactionId(Long.valueOf(transactionId));
        
        if (paymentOpt.isEmpty()) {
            log.warn("No payment record found for transaction: {}", transactionId);
            return Map.of(
                "exists", false,
                "status", "NOT_FOUND",
                "transactionId", transactionId
            );
        }
        
        PaymentTransaction payment = paymentOpt.get();
        
        Map<String, Object> status = Map.of(
            "exists", true,
            "status", payment.getStatus(),
            "success", "SUCCESS".equals(payment.getStatus()),
            "transactionId", transactionId,
            "amount", payment.getAmount(),
            "method", payment.getMethod().name(),
            "createdAt", payment.getCreatedAt(),
            "updatedAt", payment.getUpdatedAt()
        );
        
        log.debug("Payment status for transaction {}: {}", transactionId, payment.getStatus());
        return status;
    }

    // Fallback methods for Bulkhead pattern
    
    /**
     * Fallback method when payment processing bulkhead is full
     */
    private PaymentTransaction processPaymentFallback(TransactionEvent transactionEvent, PaymentRequest paymentRequest, Exception ex) {
        log.error("Payment processing bulkhead full for transaction: {}. Error: {}", 
                transactionEvent.getTransactionId(), ex.getMessage());
        log.warn("Payment service at capacity - rejecting transaction {}", transactionEvent.getTransactionId());
        
        PaymentTransaction failedTransaction = new PaymentTransaction();
        failedTransaction.setTransactionId(transactionEvent.getTransactionId());
        failedTransaction.setAmount(transactionEvent.getTotalAmount());
        failedTransaction.setMethod(paymentRequest.getPaymentMethod());
        failedTransaction.setStatus("FAILED_CAPACITY");
        
        // Save the failed transaction for tracking
        return transactionRepository.save(failedTransaction);
    }

    /**
     * Fallback method when payment processing with event bulkhead is full
     */
    private PaymentTransaction processPaymentWithEventFallback(TransactionEvent transactionEvent, PaymentRequest paymentRequest, boolean publishEvent, Exception ex) {
        log.error("Payment processing with event bulkhead full for transaction: {}. Error: {}", 
                transactionEvent.getTransactionId(), ex.getMessage());
        return processPaymentFallback(transactionEvent, paymentRequest, ex);
    }

    /**
     * Fallback method when Kafka processing bulkhead is full
     */
    private PaymentTransaction processPaymentFromKafkaFallback(TransactionEvent transactionEvent, Exception ex) {
        log.error("Kafka payment processing bulkhead full for transaction: {}. Error: {}", 
                transactionEvent.getTransactionId(), ex.getMessage());
        log.warn("Payment service Kafka processing at capacity - rejecting transaction {}", 
                transactionEvent.getTransactionId());
        
        PaymentTransaction failedTransaction = new PaymentTransaction();
        failedTransaction.setTransactionId(transactionEvent.getTransactionId());
        failedTransaction.setAmount(transactionEvent.getTotalAmount());
        failedTransaction.setMethod(PaymentMethod.CREDIT_CARD); // Default method
        failedTransaction.setStatus("FAILED_CAPACITY_KAFKA");
        
        // Save the failed transaction for tracking and manual processing
        return transactionRepository.save(failedTransaction);
    }
}