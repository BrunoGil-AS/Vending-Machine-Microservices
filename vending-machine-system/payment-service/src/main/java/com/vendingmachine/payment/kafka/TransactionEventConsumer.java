package com.vendingmachine.payment.kafka;

import com.vendingmachine.common.aop.annotation.Auditable;
import com.vendingmachine.common.aop.annotation.ExecutionTime;
import com.vendingmachine.common.event.TransactionEvent;
import com.vendingmachine.common.util.CorrelationIdUtil;
import com.vendingmachine.payment.payment.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("disabled-transaction-event-consumer")
@RequiredArgsConstructor
@Slf4j
public class TransactionEventConsumer {

    private final PaymentService paymentService;

    // @KafkaListener(topics = "transaction-events", groupId = "payment-service-group",
    //                containerFactory = "transactionEventKafkaListenerContainerFactory")
    @Transactional
    @Auditable(operation = "CONSUME_TRANSACTION_EVENT", entityType = "TransactionEvent", logParameters = true)
    @ExecutionTime(operation = "CONSUME_TRANSACTION_EVENT", warningThreshold = 2000)
    public void consumeTransactionEvent(@Payload TransactionEvent event,
                                       @Header(value = "X-Correlation-ID", required = false) String correlationId) {
        try {
            if (correlationId != null) {
                CorrelationIdUtil.setCorrelationId(correlationId);
            }
            
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
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }
}