package com.vendingmachine.dispensing.dispensing;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DispensingItem {
    private Long productId;
    private Integer quantity;
}