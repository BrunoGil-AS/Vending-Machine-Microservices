package com.vendingmachine.inventory.product;

import com.vendingmachine.inventory.product.dto.PostProductDTO;
import com.vendingmachine.inventory.product.dto.ProductDTO;

import lombok.experimental.UtilityClass;

@UtilityClass

public class ProductUtils {
    public static ProductDTO convertToDto(Product product) {
        Integer quantity = (product.getStock() != null) ? product.getStock().getQuantity() : 0;
        return ProductDTO.builder()
                .id(product.getId())
                .name(product.getName())
                .price(product.getPrice())
                .description(product.getDescription())
                .quantity(quantity)
                .build();
    }

    public static Product convertToEntity(PostProductDTO product) {
        return Product.builder()
                .name(product.getName())
                .price(product.getPrice())
                .description(product.getDescription())
                .build();
    }
    public static Product convertToEntity(ProductDTO product) {
        return Product.builder()
                .id(product.getId())
                .name(product.getName())
                .price(product.getPrice())
                .description(product.getDescription())
                .build();
    }

    
}
