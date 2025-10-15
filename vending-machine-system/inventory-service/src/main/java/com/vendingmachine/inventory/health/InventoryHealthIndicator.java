package com.vendingmachine.inventory.health;

import com.vendingmachine.inventory.stock.StockRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class InventoryHealthIndicator implements HealthIndicator {

    @Autowired
    private StockRepository stockRepository;

    @Override
    public Health health() {
        try {
            // Check database connectivity
            long totalProducts = stockRepository.count();

            // Check for low stock items (less than 5 units)
            long lowStockCount = stockRepository.countByQuantityLessThan(5);

            if (totalProducts == 0) {
                return Health.down()
                    .withDetail("message", "No products found in inventory")
                    .build();
            }

            Health.Builder health = Health.up()
                .withDetail("totalProducts", totalProducts)
                .withDetail("lowStockItems", lowStockCount);

            if (lowStockCount > 0) {
                health.withDetail("warning", "Low stock items detected: " + lowStockCount);
            }

            return health.build();

        } catch (Exception e) {
            return Health.down()
                .withDetail("error", "Database connection failed")
                .withDetail("message", e.getMessage())
                .build();
        }
    }
}