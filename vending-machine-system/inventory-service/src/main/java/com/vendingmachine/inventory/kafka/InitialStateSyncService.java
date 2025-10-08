package com.vendingmachine.inventory.kafka;

import com.vendingmachine.inventory.InventoryService;
import com.vendingmachine.inventory.product.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;

@Service
public class InitialStateSyncService {

    private static final Logger logger = LoggerFactory.getLogger(InitialStateSyncService.class);

    @Autowired
    private InventoryService inventoryService;

    @PostConstruct
    public void performInitialSync() {
        logger.info("Performing initial state synchronization...");

        try {
            // Get all current products and their stock levels
            List<Product> products = inventoryService.getAllProducts();

            logger.info("Found {} products for initial sync", products.size());

            // Log current state for monitoring purposes
            for (Product product : products) {
                if (product.getStock() != null) {
                    logger.info("Product {} (ID: {}) - Current stock: {}, Min threshold: {}",
                               product.getName(), product.getId(),
                               product.getStock().getQuantity(),
                               product.getStock().getMinThreshold());
                }
            }

            logger.info("Initial state synchronization completed successfully");

        } catch (Exception e) {
            logger.error("Failed to perform initial state synchronization: {}", e.getMessage(), e);
            // Don't throw exception to avoid preventing service startup
        }
    }
}