package com.vendingmachine.transaction.transaction.dto;

import com.vendingmachine.transaction.transaction.TransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionDTO {

    private Long id;
    // No customerId - anonymous transactions
    private List<TransactionItemDTO> items;
    private BigDecimal totalAmount;
    private TransactionStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}