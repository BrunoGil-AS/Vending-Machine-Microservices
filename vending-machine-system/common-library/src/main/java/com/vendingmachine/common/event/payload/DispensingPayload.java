package com.vendingmachine.common.event.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Payload data for dispensing-related domain events
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DispensingPayload {
    
    private Long dispensingId;
    private Long transactionId;
    private Long productId;
    private Integer requestedQuantity;
    private Integer dispensedQuantity;
    private String status; // PENDING, COMPLETED, FAILED, PARTIAL
    private String failureReason;
    private List<DispensingItem> dispensedItems;
    private Long timestamp;
    
    /**
     * Represents an individual item that was dispensed
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DispensingItem {
        private String slotPosition;
        private Long productId;
        private Integer quantity;
        private String status; // SUCCESS, FAILED
    }
    
    /**
     * Factory method for successful dispensing payload
     */
    public static DispensingPayload forSuccess(Long dispensingId, Long transactionId, Long productId, 
                                             Integer quantity, List<DispensingItem> dispensedItems) {
        return DispensingPayload.builder()
            .dispensingId(dispensingId)
            .transactionId(transactionId)
            .productId(productId)
            .requestedQuantity(quantity)
            .dispensedQuantity(dispensedItems.stream().mapToInt(DispensingItem::getQuantity).sum())
            .status("COMPLETED")
            .dispensedItems(dispensedItems)
            .timestamp(System.currentTimeMillis())
            .build();
    }
    
    /**
     * Factory method for failed dispensing payload
     */
    public static DispensingPayload forFailure(Long dispensingId, Long transactionId, Long productId, 
                                             Integer quantity, String failureReason) {
        return DispensingPayload.builder()
            .dispensingId(dispensingId)
            .transactionId(transactionId)
            .productId(productId)
            .requestedQuantity(quantity)
            .dispensedQuantity(0)
            .status("FAILED")
            .failureReason(failureReason)
            .timestamp(System.currentTimeMillis())
            .build();
    }
    
    /**
     * Factory method for partial dispensing payload
     */
    public static DispensingPayload forPartial(Long dispensingId, Long transactionId, Long productId, 
                                             Integer requestedQuantity, List<DispensingItem> dispensedItems, 
                                             String reason) {
        return DispensingPayload.builder()
            .dispensingId(dispensingId)
            .transactionId(transactionId)
            .productId(productId)
            .requestedQuantity(requestedQuantity)
            .dispensedQuantity(dispensedItems.stream().mapToInt(DispensingItem::getQuantity).sum())
            .status("PARTIAL")
            .failureReason(reason)
            .dispensedItems(dispensedItems)
            .timestamp(System.currentTimeMillis())
            .build();
    }
}