package com.vendingmachine.common.event.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Payload data for payment-related domain events
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentPayload {
    
    private Long paymentId;
    private Long transactionId;
    private BigDecimal amount;
    private String paymentMethod; // CASH, CARD, MOBILE
    private String status; // PENDING, COMPLETED, FAILED
    private String gatewayTransactionId;
    private String failureReason;
    private Long timestamp;
    
    /**
     * Factory method for payment completed payload
     */
    public static PaymentPayload forCompleted(Long paymentId, Long transactionId, BigDecimal amount, 
                                            String paymentMethod, String gatewayTransactionId) {
        return PaymentPayload.builder()
            .paymentId(paymentId)
            .transactionId(transactionId)
            .amount(amount)
            .paymentMethod(paymentMethod)
            .status("COMPLETED")
            .gatewayTransactionId(gatewayTransactionId)
            .timestamp(System.currentTimeMillis())
            .build();
    }
    
    /**
     * Factory method for payment failed payload
     */
    public static PaymentPayload forFailed(Long paymentId, Long transactionId, BigDecimal amount, 
                                         String paymentMethod, String failureReason) {
        return PaymentPayload.builder()
            .paymentId(paymentId)
            .transactionId(transactionId)
            .amount(amount)
            .paymentMethod(paymentMethod)
            .status("FAILED")
            .failureReason(failureReason)
            .timestamp(System.currentTimeMillis())
            .build();
    }
}