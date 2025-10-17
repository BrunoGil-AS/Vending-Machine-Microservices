package com.vendingmachine.dispensing.kafka;

import com.vendingmachine.common.event.TransactionEvent;
import com.vendingmachine.dispensing.dispensing.DispensingItem;
import com.vendingmachine.dispensing.dispensing.DispensingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionEventConsumer {

    private final DispensingService dispensingService;
    private final RestTemplate restTemplate;

    @Value("${services.transaction.url:http://localhost:8083}")
    private String transactionServiceUrl;

    @KafkaListener(topics = "transaction-events", groupId = "dispensing-service-group",
                   containerFactory = "transactionEventKafkaListenerContainerFactory")
    @Transactional
    public void consumeTransactionEvent(TransactionEvent event) {
        log.info("Received transaction event: {} for transaction {} with status {}",
                event.getEventId(), event.getTransactionId(), event.getStatus());

        try {
            if ("PROCESSING".equals(event.getStatus())) {
                // Transaction is ready for dispensing - get transaction items
                List<DispensingItem> items = getTransactionItems(event.getTransactionId());
                if (!items.isEmpty()) {
                    dispensingService.dispenseProductsForTransaction(event.getTransactionId(), items);
                    log.info("Dispensing initiated for transaction {}", event.getTransactionId());
                } else {
                    log.warn("No items found for transaction {}", event.getTransactionId());
                }
            } else {
                log.debug("Ignoring transaction event with status: {}", event.getStatus());
            }
        } catch (Exception e) {
            log.error("Failed to process transaction event: {}", event.getEventId(), e);
            throw new RuntimeException("Failed to process transaction event", e);
        }
    }

    private List<DispensingItem> getTransactionItems(Long transactionId) {
        try {
            String url = transactionServiceUrl + "/api/admin/transaction/" + transactionId + "/items";
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Service", "dispensing-service");
            HttpEntity<?> entity = new HttpEntity<>(headers);

            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, new ParameterizedTypeReference<List<Map<String, Object>>>() {});

            List<Map<String, Object>> items = response.getBody();
            if (items != null && !items.isEmpty()) {
                return items.stream()
                    .map(item -> new DispensingItem(
                        ((Number) item.get("productId")).longValue(),
                        ((Number) item.get("quantity")).intValue()
                    ))
                    .collect(Collectors.toList());
            }
            return List.of();
        } catch (Exception e) {
            log.error("Failed to get transaction items for transaction {}", transactionId, e);
            return List.of();
        }
    }
}