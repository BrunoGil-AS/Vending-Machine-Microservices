package com.vendingmachine.transaction.transaction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionItemDTO {

    private Long id;
    private Long productId;
    private Integer quantity;
    private BigDecimal price;
}