package com.vendingmachine.payment.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vendingmachine.common.aop.annotation.Auditable;
import com.vendingmachine.common.aop.annotation.ExecutionTime;
import com.vendingmachine.common.event.DomainEvent;
import com.vendingmachine.common.event.TransactionEvent;
import com.vendingmachine.common.util.CorrelationIdUtil;
import com.vendingmachine.payment.payment.PaymentService;
import com.vendingmachine.payment.util.ProcessedEventRepository;
import com.vendingmachine.payment.util.ProcessedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UnifiedEventConsumer {

    private final PaymentService paymentService;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "vending-machine-domain-events", groupId = "payment-service-unified-group",
                   containerFactory = "unifiedEventKafkaListenerContainerFactory")
    @Transactional
    @Auditable(operation = "CONSUME_UNIFIED_EVENT", entityType = "DomainEvent", logParameters = true)
    @ExecutionTime(operation = "CONSUME_UNIFIED_EVENT", warningThreshold = 2000)
    public void consumeUnifiedEvent(@Payload DomainEvent event,
                                   @Header(value = "X-Correlation-ID", required = false) String correlationId) {
        try {
            if (correlationId != null) {
                CorrelationIdUtil.setCorrelationId(correlationId);
            }
            
            log.info("Received unified event: {} from source: {} with type: {}",
                    event.getEventId(), event.getSource(), event.getEventType());

            // Check for duplicate events
            if (processedEventRepository.existsByEventIdAndEventType(event.getEventId(), event.getEventType())) {
                log.debug("Event already processed: {}", event.getEventId());
                return;
            }

            try {
                // Only process TRANSACTION events from transaction service
                if ("TRANSACTION".equals(event.getEventType()) && "transaction-service".equals(event.getSource())) {
                    TransactionEvent transactionEvent = objectMapper.convertValue(event.getPayload(), TransactionEvent.class);
                    
                    if ("STARTED".equals(transactionEvent.getStatus())) {
                        log.info("Processing payment for transaction: {}", transactionEvent.getTransactionId());
                        paymentService.processPaymentForTransaction(transactionEvent);
                        log.info("Payment processing initiated for transaction {}", transactionEvent.getTransactionId());
                    } else {
                        log.debug("Ignoring transaction event with status: {}", transactionEvent.getStatus());
                    }
                } else {
                    log.debug("Ignoring event type: {} from source: {}", event.getEventType(), event.getSource());
                }

                // Mark event as processed
                ProcessedEvent processedEvent = ProcessedEvent.builder()
                    .eventId(event.getEventId())
                    .eventType(event.getEventType())
                    .source(event.getSource())
                    .build();
                processedEventRepository.save(processedEvent);
                
            } catch (Exception e) {
                log.error("Failed to process unified event: {}", event.getEventId(), e);
                throw new RuntimeException("Failed to process unified event", e);
            }
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }
}