package com.vendingmachine.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymentEvent {
    private String eventId;
    private Long transactionId;
    private Double amount;
    private String method;
    private String status;
    private Long timestamp;
}