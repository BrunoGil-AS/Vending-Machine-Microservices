package com.vendingmachine.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DispensingEvent {
    private String eventId;
    private Long transactionId;
    private Long productId;
    private Integer quantity;
    private String status; // SUCCESS, FAILED
    private Long timestamp;
}
