package com.vendingmachine.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LowStockAlertEvent {
    private String eventId;
    private Long productId;
    private String productName;
    private Integer currentQuantity;
    private Integer minThreshold;
    private String alertType;
    private Long timestamp;
}