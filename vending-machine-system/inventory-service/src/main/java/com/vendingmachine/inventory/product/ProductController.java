package com.vendingmachine.inventory.product;

import com.vendingmachine.inventory.InventoryService;
import com.vendingmachine.inventory.product.dto.PostProductDTO;
import com.vendingmachine.inventory.stock.Stock;
import com.vendingmachine.common.aop.annotation.Auditable;
import com.vendingmachine.common.aop.annotation.ExecutionTime;
import com.vendingmachine.common.util.CorrelationIdUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ProductController {

    private static final Logger logger = LoggerFactory.getLogger(ProductController.class);

    @Autowired
    private InventoryService inventoryService;



    @GetMapping("/inventory/products")
    @Auditable(operation = "GET_ALL_PRODUCTS", entityType = "Product")
    @ExecutionTime(operation = "Get All Products", warningThreshold = 1500)
    public List<Product> getAllProducts(
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
        CorrelationIdUtil.setCorrelationId(correlationId);
        try {
            logger.info("Received request to get all products");
            List<Product> products = inventoryService.getAllProducts();
            logger.info("Returning {} products", products.size());
            return products;
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }

    @GetMapping("/inventory/products/{productId}")
    @Auditable(operation = "GET_PRODUCT_BY_ID", entityType = "Product", logParameters = true)
    @ExecutionTime(operation = "Get Product by ID", warningThreshold = 1000)
    public ResponseEntity<Product> getProduct(
            @PathVariable Long productId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
        CorrelationIdUtil.setCorrelationId(correlationId);
        try {
            logger.info("Received request to get product with ID: {}", productId);
            Product product = inventoryService.getProductById(productId)
                    .orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + productId));
            logger.info("Returning product with ID: {}", productId);
            return ResponseEntity.ok(product);
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }

    @GetMapping("/admin/inventory/products/{productId}")
    @Auditable(operation = "ADMIN_GET_PRODUCT_BY_ID", entityType = "Product", logParameters = true)
    @ExecutionTime(operation = "Admin Get Product by ID", warningThreshold = 1000)
    public ResponseEntity<Product> getProductAdmin(
            @PathVariable Long productId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
        CorrelationIdUtil.setCorrelationId(correlationId);
        try {
            logger.info("Received admin request to get product with ID: {}", productId);
            Product product = inventoryService.getProductById(productId)
                    .orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + productId));
            logger.info("Returning product with ID: {} (admin endpoint)", productId);
            return ResponseEntity.ok(product);
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }

    @PostMapping("/inventory/check-availability")
    @Auditable(operation = "CHECK_AVAILABILITY", entityType = "Inventory", logParameters = true, logResult = true)
    @ExecutionTime(operation = "Check Availability", warningThreshold = 800, detailed = true)
    public ResponseEntity<Map<String, Boolean>> checkAvailability(
            @RequestBody List<Map<String, Object>> items,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
        CorrelationIdUtil.setCorrelationId(correlationId);
        try {
            logger.info("Received request to check availability for {} items", items.size());
            boolean available = inventoryService.checkInventoryAvailability(items);
            logger.info("Inventory availability check result: {}", available);
            return ResponseEntity.ok(Map.of("available", available));
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }

    /**
     * Check availability for multiple products with detailed information per product.
     * Returns a map of productId to availability details.
     * 
     * @param items List of items with productId and quantity
     * @return Map of productId to availability details
     */
    @PostMapping("/inventory/check-multiple")
    @Auditable(operation = "CHECK_MULTIPLE_AVAILABILITY", entityType = "Inventory", logParameters = true)
    @ExecutionTime(operation = "Check Multiple Availability", warningThreshold = 1000, detailed = true)
    public ResponseEntity<Map<Long, Map<String, Object>>> checkMultipleAvailability(
            @RequestBody List<Map<String, Object>> items,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
        CorrelationIdUtil.setCorrelationId(correlationId);
        try {
            logger.info("Received request to check multiple availability for {} items", items.size());
            Map<Long, Map<String, Object>> results = inventoryService.checkMultipleAvailability(items);
            logger.info("Multiple availability check completed for {} items", items.size());
            return ResponseEntity.ok(results);
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }

    @GetMapping("/inventory/availability/{productId}")
    @Auditable(operation = "GET_AVAILABILITY", entityType = "Stock", logParameters = true)
    @ExecutionTime(operation = "Get Stock Availability", warningThreshold = 800)
    public ResponseEntity<Stock> getAvailability(
            @PathVariable Long productId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
        CorrelationIdUtil.setCorrelationId(correlationId);
        try {
            logger.info("Received request to get availability for product ID: {}", productId);
            Stock stock = inventoryService.getStockByProductId(productId)
                    .orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + productId));
            logger.info("Returning stock information for product ID: {}", productId);
            return ResponseEntity.ok(stock);
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }

    @PostMapping("/admin/inventory/products")
    @Auditable(operation = "ADD_PRODUCT", entityType = "Product", logParameters = true, logResult = true)
    @ExecutionTime(operation = "Add Product", warningThreshold = 1500, detailed = true)
    public ResponseEntity<Product> addProduct(
            @RequestBody  PostProductDTO product,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
        CorrelationIdUtil.setCorrelationId(correlationId);
        try {
            logger.info("Received request to add new product: {}", product.getName());
            Product newProduct = inventoryService.addProduct(product);
            logger.info("Product added successfully with ID: {}", newProduct.getId());
            return ResponseEntity.status(201).body(newProduct);
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }

    @PutMapping("/admin/inventory/products/{productId}")
    @Auditable(operation = "UPDATE_PRODUCT", entityType = "Product", logParameters = true, logResult = true)
    @ExecutionTime(operation = "Update Product", warningThreshold = 1500, detailed = true)
    public Product updateProduct(
            @PathVariable Long productId, 
            @RequestBody PostProductDTO product,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
        CorrelationIdUtil.setCorrelationId(correlationId);
        try {
            logger.info("Received request to update product ID: {}", productId);
            Product updatedProduct = inventoryService.updateProduct(productId, product);
            logger.info("Product updated successfully with ID: {}", updatedProduct.getId());
            return updatedProduct;
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }

    @PutMapping("/admin/inventory/stock/{productId}")
    @Auditable(operation = "UPDATE_STOCK", entityType = "Stock", logParameters = true, logResult = true)
    @ExecutionTime(operation = "Update Stock", warningThreshold = 1200, detailed = true)
    public Stock updateStock(
            @PathVariable Long productId, 
            @RequestBody Stock stock,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
        CorrelationIdUtil.setCorrelationId(correlationId);
        try {
            logger.info("Received request to update stock for product ID: {}", productId);
            Stock updatedStock = inventoryService.updateStock(productId, stock);
            logger.info("Stock updated successfully for product ID: {}", productId);
            return updatedStock;
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }

    /**
     * Deduct stock quantity for a product (used by transaction service).
     * 
     * @param productId Product ID
     * @param request Map containing the quantity to deduct
     * @return Updated stock
     */
    @PutMapping("/inventory/products/{productId}/stock/deduct")
    @Auditable(operation = "DEDUCT_STOCK", entityType = "Stock", logParameters = true, logResult = true)
    @ExecutionTime(operation = "Deduct Stock", warningThreshold = 1000, detailed = true)
    public ResponseEntity<Stock> deductStock(
            @PathVariable Long productId, 
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
        CorrelationIdUtil.setCorrelationId(correlationId);
        try {
            Integer quantity = ((Number) request.get("quantity")).intValue();
            logger.info("Received request to deduct {} units from product ID: {}", quantity, productId);
            
            // Deduct stock (negative quantity)
            Stock updatedStock = inventoryService.updateStock(productId, -quantity);
            logger.info("Stock deducted successfully for product ID: {}. New quantity: {}", 
                       productId, updatedStock.getQuantity());
            return ResponseEntity.ok(updatedStock);
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }

    @DeleteMapping("/admin/inventory/products/{productId}")
    @Auditable(operation = "DELETE_PRODUCT", entityType = "Product", logParameters = true)
    @ExecutionTime(operation = "Delete Product", warningThreshold = 1200)
    public ResponseEntity<Void> deleteProduct(
            @PathVariable Long productId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
        CorrelationIdUtil.setCorrelationId(correlationId);
        try {
            logger.info("Received request to delete product ID: {}", productId);
            inventoryService.deleteProduct(productId);
            logger.info("Product deleted successfully with ID: {}", productId);
            return ResponseEntity.noContent().build();
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }


}
