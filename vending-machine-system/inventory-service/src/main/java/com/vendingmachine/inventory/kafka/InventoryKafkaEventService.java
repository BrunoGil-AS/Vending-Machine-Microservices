package com.vendingmachine.inventory.kafka;

import com.vendingmachine.common.event.DomainEvent;
import com.vendingmachine.common.event.payload.InventoryPayload;
import com.vendingmachine.common.kafka.UnifiedEventPublisher;
import com.vendingmachine.common.aop.annotation.Auditable;
import com.vendingmachine.common.aop.annotation.ExecutionTime;
import com.vendingmachine.common.util.CorrelationIdUtil;
import com.vendingmachine.inventory.stock.Stock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryKafkaEventService {

    private final UnifiedEventPublisher unifiedEventPublisher;

    /**
     * Publishes stock update events with complete data to unified domain events topic
     */
    @Auditable(operation = "PUBLISH_STOCK_UPDATE_EVENT", entityType = "StockUpdateEvent", logParameters = true)
    @ExecutionTime(operation = "Publish Stock Update Event", warningThreshold = 1000, detailed = true)
    public void publishStockUpdateEventWithCompleteData(Stock stock, String eventType) {
        try {
            // Create complete payload with stock data
            InventoryPayload payload = InventoryPayload.builder()
                .productId(stock.getProduct().getId())
                .productName(stock.getProduct().getName())
                .currentStock(stock.getQuantity())
                .alertThreshold(stock.getMinThreshold())
                .changeType(eventType)
                .timestamp(System.currentTimeMillis())
                .build();

            // Create unified domain event
            DomainEvent domainEvent = DomainEvent.builder()
                .eventId("inv-" + eventType.toLowerCase() + "-" + stock.getProduct().getId() + "-" + System.currentTimeMillis())
                .eventType("INVENTORY_" + eventType)
                .aggregateId(stock.getProduct().getId().toString())
                .aggregateType("PRODUCT")
                .source("inventory-service")
                .correlationId(CorrelationIdUtil.getCorrelationId())
                .timestamp(System.currentTimeMillis())
                .payload(serializePayload(payload))
                .version("1.0")
                .build();

            // Publish to unified topic only
            unifiedEventPublisher.publishEvent(domainEvent);
            
            log.info("Successfully published inventory event to unified topic: {} for product {}", 
                domainEvent.getEventId(), stock.getProduct().getId());

        } catch (Exception e) {
            log.error("Error creating unified inventory event for product {}: {}", 
                stock.getProduct().getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to publish unified inventory event", e);
        }
    }

    /**
     * Publishes low stock alert with complete data to unified domain events topic
     */
    @Auditable(operation = "PUBLISH_LOW_STOCK_ALERT", entityType = "LowStockAlert", logParameters = true)
    @ExecutionTime(operation = "Publish Low Stock Alert", warningThreshold = 1000, detailed = true)
    public void publishLowStockAlertWithCompleteData(Stock stock, String alertType) {
        try {
            // Create complete payload with stock data
            InventoryPayload payload = InventoryPayload.builder()
                .productId(stock.getProduct().getId())
                .productName(stock.getProduct().getName())
                .currentStock(stock.getQuantity())
                .alertThreshold(stock.getMinThreshold())
                .changeType("LOW_STOCK_ALERT")
                .reason("Stock level below threshold: " + alertType)
                .timestamp(System.currentTimeMillis())
                .build();

            // Create unified domain event
            DomainEvent domainEvent = DomainEvent.builder()
                .eventId("low-stock-" + alertType.toLowerCase() + "-" + stock.getProduct().getId() + "-" + System.currentTimeMillis())
                .eventType("INVENTORY_LOW_STOCK")
                .aggregateId(stock.getProduct().getId().toString())
                .aggregateType("PRODUCT")
                .source("inventory-service")
                .correlationId(CorrelationIdUtil.getCorrelationId())
                .timestamp(System.currentTimeMillis())
                .payload(serializePayload(payload))
                .version("1.0")
                .build();

            // Publish to unified topic only
            unifiedEventPublisher.publishEvent(domainEvent);
            
            log.info("Successfully published low stock alert to unified topic: {} for product {}", 
                domainEvent.getEventId(), stock.getProduct().getId());

        } catch (Exception e) {
            log.error("Error creating unified low stock alert for product {}: {}", 
                stock.getProduct().getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to publish unified low stock alert", e);
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