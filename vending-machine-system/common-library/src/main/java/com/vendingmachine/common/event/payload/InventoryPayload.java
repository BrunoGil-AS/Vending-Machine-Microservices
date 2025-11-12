package com.vendingmachine.common.event.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload data for inventory-related domain events
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryPayload {
    
    private Long productId;
    private String productName;
    private Integer previousStock;
    private Integer currentStock;
    private Integer quantityChanged;
    private String changeType; // PURCHASE, RESTOCK, ADJUSTMENT
    private String reason;
    private Long relatedTransactionId;
    private Integer alertThreshold;
    private Long timestamp;
    
    /**
     * Factory method for stock update due to purchase
     */
    public static InventoryPayload forPurchase(Long productId, Integer previousStock, Integer currentStock, 
                                             Integer quantityPurchased, Long transactionId) {
        return InventoryPayload.builder()
            .productId(productId)
            .previousStock(previousStock)
            .currentStock(currentStock)
            .quantityChanged(-quantityPurchased)
            .changeType("PURCHASE")
            .relatedTransactionId(transactionId)
            .timestamp(System.currentTimeMillis())
            .build();
    }
    
    /**
     * Factory method for stock update due to restocking
     */
    public static InventoryPayload forRestock(Long productId, String productName, Integer previousStock, 
                                            Integer currentStock, Integer quantityAdded) {
        return InventoryPayload.builder()
            .productId(productId)
            .productName(productName)
            .previousStock(previousStock)
            .currentStock(currentStock)
            .quantityChanged(quantityAdded)
            .changeType("RESTOCK")
            .reason("Admin restock operation")
            .timestamp(System.currentTimeMillis())
            .build();
    }
    
    /**
     * Factory method for low stock alert
     */
    public static InventoryPayload forLowStockAlert(Long productId, String productName, Integer currentStock, 
                                                  Integer alertThreshold) {
        return InventoryPayload.builder()
            .productId(productId)
            .productName(productName)
            .currentStock(currentStock)
            .alertThreshold(alertThreshold)
            .changeType("ALERT")
            .reason("Stock level below threshold")
            .timestamp(System.currentTimeMillis())
            .build();
    }
}