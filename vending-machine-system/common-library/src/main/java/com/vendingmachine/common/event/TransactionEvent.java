package com.vendingmachine.common.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransactionEvent {
    private String eventId;
    private Long transactionId;
    private String status;
    private Double totalAmount;
    private Long timestamp;
}