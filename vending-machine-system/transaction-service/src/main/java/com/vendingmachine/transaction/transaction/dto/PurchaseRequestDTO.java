package com.vendingmachine.transaction.transaction.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseRequestDTO {

    @NotEmpty(message = "Items cannot be empty")
    private List<PurchaseItemDTO> items;

    @NotNull(message = "Payment information is required")
    @Valid
    private PaymentInfo paymentInfo;

    // No customerId - anonymous vending machine purchases
}