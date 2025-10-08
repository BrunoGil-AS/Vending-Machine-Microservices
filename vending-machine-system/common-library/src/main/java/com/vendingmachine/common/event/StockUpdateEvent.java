package com.vendingmachine.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StockUpdateEvent {
    private String eventId;
    private Long productId;
    private Integer quantity;
    private String status;
    private Long timestamp;
}
