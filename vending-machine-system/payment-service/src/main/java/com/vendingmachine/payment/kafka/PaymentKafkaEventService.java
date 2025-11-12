package com.vendingmachine.payment.kafka;

import com.vendingmachine.common.event.DomainEvent;
import com.vendingmachine.common.event.payload.PaymentPayload;
import com.vendingmachine.common.kafka.UnifiedEventPublisher;
import com.vendingmachine.common.aop.annotation.Auditable;
import com.vendingmachine.common.aop.annotation.ExecutionTime;
import com.vendingmachine.common.util.CorrelationIdUtil;
import com.vendingmachine.payment.payment.PaymentTransaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentKafkaEventService {

    private final UnifiedEventPublisher unifiedEventPublisher;

    /**
     * Publishes payment events with complete data to unified domain events topic
     */
    @Auditable(operation = "PUBLISH_PAYMENT_EVENT", entityType = "PaymentEvent", logParameters = true)
    @ExecutionTime(operation = "Publish Payment Event", warningThreshold = 1000, detailed = true)
    public void publishPaymentEventWithCompleteData(PaymentTransaction payment, String eventStatus) {
        try {
            // Create complete payload with payment data
            PaymentPayload payload = PaymentPayload.builder()
                .paymentId(payment.getId())
                .transactionId(payment.getTransactionId())
                .amount(BigDecimal.valueOf(payment.getAmount()))
                .paymentMethod(payment.getMethod().name()) // Convert enum to string
                .status(eventStatus)
                .gatewayTransactionId("gateway-" + payment.getId()) // Simulated gateway ID
                .failureReason(eventStatus.equals("FAILED") ? "Payment processing failed" : null)
                .timestamp(System.currentTimeMillis())
                .build();

            // Create unified domain event
            DomainEvent domainEvent = DomainEvent.builder()
                .eventId("pay-" + eventStatus.toLowerCase() + "-" + payment.getId() + "-" + System.currentTimeMillis())
                .eventType("PAYMENT_" + eventStatus)
                .aggregateId(payment.getId().toString())
                .aggregateType("PAYMENT")
                .source("payment-service")
                .correlationId(CorrelationIdUtil.getCorrelationId())
                .timestamp(System.currentTimeMillis())
                .payload(serializePayload(payload))
                .version("1.0")
                .build();

            // Publish to unified topic only
            unifiedEventPublisher.publishEvent(domainEvent);
            
            log.info("Successfully published payment event to unified topic: {} for payment {}", 
                domainEvent.getEventId(), payment.getId());

        } catch (Exception e) {
            log.error("Error creating unified payment event for payment {}: {}", 
                payment.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to publish unified payment event", e);
        }
    }

    /**
     * Serialize payload to JSON
     */
    private String serializePayload(Object payload) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload);
        } catch (Exception e) {
            log.error("Failed to serialize payload", e);
            return "{}";
        }
    }
}