package com.vendingmachine.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TransactionEvent {
    private String eventId;
    private Long transactionId;
    private String status;
    private Double totalAmount;
    private Long timestamp;
}