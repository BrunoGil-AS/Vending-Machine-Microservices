package com.vendingmachine.transaction.transaction.dto;

import jakarta.validation.constraints.NotEmpty;
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

    // No customerId - anonymous vending machine purchases
}