package com.vendingmachine.transaction.transaction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentInfo {

    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;

    // Fields for card payments
    @NotBlank(message = "Card number is required for card payments", groups = CardValidation.class)
    private String cardNumber;

    @NotBlank(message = "Card holder name is required for card payments", groups = CardValidation.class)
    private String cardHolderName;

    @NotBlank(message = "Expiry date is required for card payments", groups = CardValidation.class)
    private String expiryDate; // Format: MM/YY

    // Fields for cash payments
    private BigDecimal paidAmount; // Amount inserted for cash payments

    private BigDecimal changeAmount; // Calculated change for cash payments

    // Validation groups
    public interface CardValidation {}
}