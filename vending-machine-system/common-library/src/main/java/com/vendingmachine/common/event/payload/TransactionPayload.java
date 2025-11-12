package com.vendingmachine.common.event.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Payload data for transaction-related domain events
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionPayload {
    
    private Long transactionId;
    private Long userId;
    private Long productId;
    private Integer quantity;
    private BigDecimal totalAmount;
    private String status;
    private String paymentMethod;
    private String reason; // For failures or status changes
    private Long timestamp;
    
    /**
     * Factory method for transaction created payload
     */
    public static TransactionPayload forCreated(Long transactionId, Long userId, Long productId, 
                                              Integer quantity, BigDecimal totalAmount, String paymentMethod) {
        return TransactionPayload.builder()
            .transactionId(transactionId)
            .userId(userId)
            .productId(productId)
            .quantity(quantity)
            .totalAmount(totalAmount)
            .paymentMethod(paymentMethod)
            .status("CREATED")
            .timestamp(System.currentTimeMillis())
            .build();
    }
    
    /**
     * Factory method for transaction status update payload
     */
    public static TransactionPayload forStatusUpdate(Long transactionId, String status, String reason) {
        return TransactionPayload.builder()
            .transactionId(transactionId)
            .status(status)
            .reason(reason)
            .timestamp(System.currentTimeMillis())
            .build();
    }
}