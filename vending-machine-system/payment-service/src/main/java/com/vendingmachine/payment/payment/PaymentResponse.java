package com.vendingmachine.payment.payment;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

    private Long id;
    private Double amount;
    private PaymentMethod method;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}