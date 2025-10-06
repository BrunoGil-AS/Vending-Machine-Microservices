package com.vendingmachine.inventory.product.dto;

import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class ProductDTO {
    private Long id;
    private String name;
    private Double price;
    private String description;
    private Integer quantity; // From Stock entity

    
}

