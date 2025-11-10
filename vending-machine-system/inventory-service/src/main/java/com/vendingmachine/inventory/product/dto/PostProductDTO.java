package com.vendingmachine.inventory.product.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostProductDTO {
    private String name;
    private Double price;
    private String description;
    private Integer quantity; // Initial quantity for stock
    private Integer minThreshold; // Minimum threshold for stock alerts
}
