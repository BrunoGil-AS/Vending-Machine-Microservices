package com.vendingmachine.dispensing.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vendingmachine.common.aop.annotation.Auditable;
import com.vendingmachine.common.aop.annotation.ExecutionTime;
import com.vendingmachine.common.event.DomainEvent;
import com.vendingmachine.common.event.TransactionEvent;
import com.vendingmachine.common.util.CorrelationIdUtil;
import com.vendingmachine.dispensing.dispensing.DispensingItem;
import com.vendingmachine.dispensing.dispensing.DispensingService;
import com.vendingmachine.dispensing.util.ProcessedEventRepository;
import com.vendingmachine.dispensing.util.ProcessedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UnifiedEventConsumer {

    private final DispensingService dispensingService;
    private final ProcessedEventRepository processedEventRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${services.transaction.url:http://localhost:8083}")
    private String transactionServiceUrl;

    @KafkaListener(topics = "vending-machine-domain-events", groupId = "dispensing-service-unified-group", containerFactory = "unifiedEventKafkaListenerContainerFactory")
    @Transactional
    @Auditable(operation = "CONSUME_UNIFIED_EVENT", entityType = "DomainEvent", logParameters = true)
    @ExecutionTime(operation = "CONSUME_UNIFIED_EVENT", warningThreshold = 2500)
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
                log.debug("\n\nProcessing unified event: {}\n\n", event);

                // Only process TRANSACTION events from transaction service
                if ("TRANSACTION_PROCESSING".equals(event.getEventType())
                        && "transaction-service".equals(event.getSource())) {
                    TransactionEvent transactionEvent = objectMapper.readValue((String) event.getPayload(),
                            TransactionEvent.class);

                    if ("PROCESSING".equals(transactionEvent.getStatus())) {
                        // Transaction is ready for dispensing - get transaction items
                        List<DispensingItem> items = getTransactionItems(transactionEvent.getTransactionId());
                        if (!items.isEmpty()) {
                            dispensingService.dispenseProductsForTransaction(transactionEvent.getTransactionId(),
                                    items);
                            log.info("Dispensing initiated for transaction {}", transactionEvent.getTransactionId());
                        } else {
                            log.warn("No items found for transaction {}", transactionEvent.getTransactionId());
                        }
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

    @ExecutionTime(operation = "GET_TRANSACTION_ITEMS", warningThreshold = 800)
    private List<DispensingItem> getTransactionItems(Long transactionId) {
        try {
            // Use internal endpoint for inter-service communication
            String url = transactionServiceUrl + "/api/internal/transaction/" + transactionId + "/items";
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Service", "dispensing-service");
            HttpEntity<?> entity = new HttpEntity<>(headers);

            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, new ParameterizedTypeReference<List<Map<String, Object>>>() {
                    });

            List<Map<String, Object>> items = response.getBody();
            if (items != null && !items.isEmpty()) {
                return items.stream()
                        .map(item -> new DispensingItem(
                                ((Number) item.get("productId")).longValue(),
                                ((Number) item.get("quantity")).intValue()))
                        .collect(Collectors.toList());
            }
            return List.of();
        } catch (Exception e) {
            log.error("Failed to get transaction items for transaction {}", transactionId, e);
            return List.of();
        }
    }
}