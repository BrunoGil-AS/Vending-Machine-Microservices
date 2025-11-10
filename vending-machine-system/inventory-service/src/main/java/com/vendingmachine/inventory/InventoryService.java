package com.vendingmachine.inventory;

import com.vendingmachine.common.event.StockUpdateEvent;
import com.vendingmachine.common.event.LowStockAlertEvent;
import com.vendingmachine.common.aop.annotation.Auditable;
import com.vendingmachine.common.aop.annotation.ExecutionTime;
import com.vendingmachine.inventory.kafka.KafkaProducerService;
import com.vendingmachine.inventory.product.Product;
import com.vendingmachine.inventory.product.ProductRepository;
import com.vendingmachine.inventory.product.ProductUtils;
import com.vendingmachine.inventory.product.dto.PostProductDTO;
import com.vendingmachine.inventory.stock.Stock;
import com.vendingmachine.inventory.stock.StockRepository;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class InventoryService {

    private static final Logger logger = LoggerFactory.getLogger(InventoryService.class);

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private KafkaProducerService kafkaProducerService;

    public List<Product> getAllProducts() {
        logger.debug("Retrieving all products");
        List<Product> products = productRepository.findAll();
        logger.info("Retrieved {} products", products.size());
        return products;
    }

    public Optional<Product> getProductById(Long productId) {
        logger.debug("Retrieving product with ID: {}", productId);
        return productRepository.findById(productId);
    }

    public Optional<Stock> getStockByProductId(Long productId) {
        logger.debug("Retrieving stock for product ID: {}", productId);
        Optional<Stock> stock = stockRepository.findByProductId(productId);
        if (stock.isPresent()) {
            logger.debug("Stock found for product ID: {}, quantity: {}", productId, stock.get().getQuantity());
        } else {
            logger.warn("No stock found for product ID: {}", productId);
        }
        return stock;
    }

    public Product addProduct(PostProductDTO product) {
        logger.info("Adding new product: {}", product.getName());
        Product newProduct = ProductUtils.convertToEntity(product);
        Stock stock = Stock.builder()
                    .product(newProduct)
                    .quantity(product.getQuantity())
                    .minThreshold(product.getMinThreshold())
                    .build();
        newProduct.setStock(stock);
        productRepository.save(newProduct);
        stockRepository.save(stock);
        logger.info("Product added successfully with ID: {}, initial stock: {}", newProduct.getId(), product.getQuantity());

        // Publish stock update event for initial stock
        String status = "STOCK_INITIAL";
        if (stock.getQuantity() <= 0) {
            status = "OUT_OF_STOCK";
            logger.warn("Product ID: {} added with out of stock", newProduct.getId());
        } else if (stock.getQuantity() < stock.getMinThreshold()) {
            status = "LOW_STOCK";
            logger.warn("Product ID: {} added with low stock. Current: {}, Threshold: {}",
                       newProduct.getId(), stock.getQuantity(), stock.getMinThreshold());
        }

        StockUpdateEvent stockEvent = new StockUpdateEvent(
            UUID.randomUUID().toString(),
            newProduct.getId(),
            stock.getQuantity(),
            status,
            System.currentTimeMillis()
        );
        kafkaProducerService.send("stock-update-events", stockEvent);
        logger.info("Published initial stock update event: {}", stockEvent);

        // Publish low stock alert if applicable
        if (stock.getQuantity() < stock.getMinThreshold() && stock.getQuantity() > 0) {
            String alertType = "LOW_STOCK";
            LowStockAlertEvent alertEvent = new LowStockAlertEvent(
                UUID.randomUUID().toString(),
                newProduct.getId(),
                newProduct.getName(),
                stock.getQuantity(),
                stock.getMinThreshold(),
                alertType,
                System.currentTimeMillis()
            );
            kafkaProducerService.send("low-stock-alerts", alertEvent);
            logger.info("Published low stock alert event for new product: {}", alertEvent);
        } else if (stock.getQuantity() <= 0) {
            String alertType = "OUT_OF_STOCK";
            LowStockAlertEvent alertEvent = new LowStockAlertEvent(
                UUID.randomUUID().toString(),
                newProduct.getId(),
                newProduct.getName(),
                stock.getQuantity(),
                stock.getMinThreshold(),
                alertType,
                System.currentTimeMillis()
            );
            kafkaProducerService.send("low-stock-alerts", alertEvent);
            logger.warn("Published out of stock alert event for new product: {}", alertEvent);
        }

        return newProduct;
    }

    public Product updateProduct(Long productId, PostProductDTO productDTO) {
        logger.info("Updating product with ID: {}", productId);
        
        // Find existing product
        Product existingProduct = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + productId));
        
        // Update product fields
        existingProduct.setName(productDTO.getName());
        existingProduct.setPrice(productDTO.getPrice());
        existingProduct.setDescription(productDTO.getDescription());
        
        // Update associated stock
        Stock existingStock = stockRepository.findByProductId(productId)
                .orElseThrow(() -> new RuntimeException("Stock not found for product id: " + productId));
        
        int previousQuantity = existingStock.getQuantity();
        existingStock.setQuantity(productDTO.getQuantity());
        existingStock.setMinThreshold(productDTO.getMinThreshold());
        
        // Save both entities
        Product updatedProduct = productRepository.save(existingProduct);
        Stock updatedStock = stockRepository.save(existingStock);
        
        logger.info("Product updated successfully with ID: {}. Previous quantity: {}, New quantity: {}, Min threshold: {}",
                   productId, previousQuantity, updatedStock.getQuantity(), updatedStock.getMinThreshold());

        // Publish stock update event
        String status = "STOCK_UPDATED";
        if (updatedStock.getQuantity() <= 0) {
            status = "OUT_OF_STOCK";
            logger.warn("Product ID: {} is now out of stock", productId);
        } else if (updatedStock.getQuantity() < updatedStock.getMinThreshold()) {
            status = "LOW_STOCK";
            logger.warn("Product ID: {} is below minimum threshold. Current: {}, Threshold: {}",
                       productId, updatedStock.getQuantity(), updatedStock.getMinThreshold());
        }

        StockUpdateEvent stockEvent = new StockUpdateEvent(
            UUID.randomUUID().toString(),
            productId,
            updatedStock.getQuantity(),
            status,
            System.currentTimeMillis()
        );
        kafkaProducerService.send("stock-update-events", stockEvent);
        logger.info("Published stock update event: {}", stockEvent);

        // Publish low stock alert if applicable
        if (updatedStock.getQuantity() < updatedStock.getMinThreshold() && updatedStock.getQuantity() > 0) {
            String alertType = "LOW_STOCK";
            LowStockAlertEvent alertEvent = new LowStockAlertEvent(
                UUID.randomUUID().toString(),
                productId,
                updatedProduct.getName(),
                updatedStock.getQuantity(),
                updatedStock.getMinThreshold(),
                alertType,
                System.currentTimeMillis()
            );
            kafkaProducerService.send("low-stock-alerts", alertEvent);
            logger.info("Published low stock alert event: {}", alertEvent);
        } else if (updatedStock.getQuantity() <= 0) {
            String alertType = "OUT_OF_STOCK";
            LowStockAlertEvent alertEvent = new LowStockAlertEvent(
                UUID.randomUUID().toString(),
                productId,
                updatedProduct.getName(),
                updatedStock.getQuantity(),
                updatedStock.getMinThreshold(),
                alertType,
                System.currentTimeMillis()
            );
            kafkaProducerService.send("low-stock-alerts", alertEvent);
            logger.warn("Published out of stock alert event: {}", alertEvent);
        }

        return updatedProduct;
    }

    @Bulkhead(name = "stock-updates", fallbackMethod = "updateStockFallback", type = Bulkhead.Type.SEMAPHORE)
    @Auditable(operation = "Update Stock", entityType = "Stock", logParameters = true, logResult = true)
    @ExecutionTime(operation = "updateStock", warningThreshold = 800, detailed = true)
    public Stock updateStock(Long productId, Integer quantity) {
        logger.info("Updating stock for product ID: {}, quantity change: {}", productId, quantity);
        Stock existingStock = stockRepository.findByProductId(productId)
                .orElseThrow(() -> new RuntimeException("Stock not found for product id: " + productId));
        if (existingStock.getMinThreshold() == null) {
            existingStock.setMinThreshold(5); // Default value for existing stocks
        }
        int previousQuantity = existingStock.getQuantity();
        existingStock.setQuantity(existingStock.getQuantity() + quantity);
        Stock updatedStock = stockRepository.save(existingStock);

        logger.info("Stock updated for product ID: {}. Previous quantity: {}, New quantity: {}, Min threshold: {}",
                   productId, previousQuantity, updatedStock.getQuantity(), updatedStock.getMinThreshold());

        // Publish stock update event
        String status = "STOCK_UPDATED";
        if (updatedStock.getQuantity() <= 0) {
            status = "OUT_OF_STOCK";
            logger.warn("Product ID: {} is now out of stock", productId);
        } else if (updatedStock.getQuantity() < updatedStock.getMinThreshold()) {
            status = "LOW_STOCK";
            logger.warn("Product ID: {} is below minimum threshold. Current: {}, Threshold: {}",
                       productId, updatedStock.getQuantity(), updatedStock.getMinThreshold());
        }

        StockUpdateEvent stockEvent = new StockUpdateEvent(
            UUID.randomUUID().toString(),
            productId,
            updatedStock.getQuantity(),
            status,
            System.currentTimeMillis()
        );
        kafkaProducerService.send("stock-update-events", stockEvent);
        logger.info("Published stock update event: {}", stockEvent);

        // Publish low stock alert if applicable
        if (updatedStock.getQuantity() < updatedStock.getMinThreshold() && updatedStock.getQuantity() > 0) {
            String alertType = "LOW_STOCK";
            LowStockAlertEvent alertEvent = new LowStockAlertEvent(
                UUID.randomUUID().toString(),
                productId,
                existingStock.getProduct().getName(),
                updatedStock.getQuantity(),
                updatedStock.getMinThreshold(),
                alertType,
                System.currentTimeMillis()
            );
            kafkaProducerService.send("low-stock-alerts", alertEvent);
            logger.info("Published low stock alert event: {}", alertEvent);
        } else if (updatedStock.getQuantity() <= 0) {
            String alertType = "OUT_OF_STOCK";
            LowStockAlertEvent alertEvent = new LowStockAlertEvent(
                UUID.randomUUID().toString(),
                productId,
                existingStock.getProduct().getName(),
                updatedStock.getQuantity(),
                updatedStock.getMinThreshold(),
                alertType,
                System.currentTimeMillis()
            );
            kafkaProducerService.send("low-stock-alerts", alertEvent);
            logger.warn("Published out of stock alert event: {}", alertEvent);
        }

        return updatedStock;
    }

    public Stock updateStock(Long productId, Stock stock) {
        logger.info("Updating stock details for product ID: {}, new quantity: {}, new threshold: {}",
                   productId, stock.getQuantity(), stock.getMinThreshold());
        Stock existingStock = stockRepository.findByProductId(productId)
                .orElseThrow(() -> new RuntimeException("Stock not found for product id: " + productId));
        if (existingStock.getMinThreshold() == null) {
            existingStock.setMinThreshold(5); // Default value for existing stocks
        }
        int previousQuantity = existingStock.getQuantity();
        existingStock.setQuantity(stock.getQuantity());
        existingStock.setMinThreshold(stock.getMinThreshold());
        Stock updatedStock = stockRepository.save(existingStock);
        logger.info("Stock details updated for product ID: {}. Previous quantity: {}, New quantity: {}, Min threshold: {}",
                   productId, previousQuantity, updatedStock.getQuantity(), updatedStock.getMinThreshold());

        // Publish stock update event
        String status = "STOCK_UPDATED";
        if (updatedStock.getQuantity() <= 0) {
            status = "OUT_OF_STOCK";
            logger.warn("Product ID: {} is now out of stock", productId);
        } else if (updatedStock.getQuantity() < updatedStock.getMinThreshold()) {
            status = "LOW_STOCK";
            logger.warn("Product ID: {} is below minimum threshold. Current: {}, Threshold: {}",
                       productId, updatedStock.getQuantity(), updatedStock.getMinThreshold());
        }

        StockUpdateEvent stockEvent = new StockUpdateEvent(
            UUID.randomUUID().toString(),
            productId,
            updatedStock.getQuantity(),
            status,
            System.currentTimeMillis()
        );
        kafkaProducerService.send("stock-update-events", stockEvent);
        logger.info("Published stock update event: {}", stockEvent);

        // Publish low stock alert if applicable
        if (updatedStock.getQuantity() < updatedStock.getMinThreshold() && updatedStock.getQuantity() > 0) {
            String alertType = "LOW_STOCK";
            LowStockAlertEvent alertEvent = new LowStockAlertEvent(
                UUID.randomUUID().toString(),
                productId,
                existingStock.getProduct().getName(),
                updatedStock.getQuantity(),
                updatedStock.getMinThreshold(),
                alertType,
                System.currentTimeMillis()
            );
            kafkaProducerService.send("low-stock-alerts", alertEvent);
            logger.info("Published low stock alert event: {}", alertEvent);
        } else if (updatedStock.getQuantity() <= 0) {
            String alertType = "OUT_OF_STOCK";
            LowStockAlertEvent alertEvent = new LowStockAlertEvent(
                UUID.randomUUID().toString(),
                productId,
                existingStock.getProduct().getName(),
                updatedStock.getQuantity(),
                updatedStock.getMinThreshold(),
                alertType,
                System.currentTimeMillis()
            );
            kafkaProducerService.send("low-stock-alerts", alertEvent);
            logger.warn("Published out of stock alert event: {}", alertEvent);
        }

        return updatedStock;
    }

    @ExecutionTime(operation = "checkInventoryAvailability", warningThreshold = 500)
    public boolean checkInventoryAvailability(List<Map<String, Object>> items) {
        logger.debug("Checking inventory availability for {} items", items.size());

        for (Map<String, Object> item : items) {
            Long productId = ((Number) item.get("productId")).longValue();
            Integer quantity = ((Number) item.get("quantity")).intValue();

            logger.debug("Checking availability for product ID: {}, requested quantity: {}", productId, quantity);

            Optional<Stock> stockOpt = getStockByProductId(productId);
            if (stockOpt.isEmpty()) {
                logger.warn("Product ID: {} not found in inventory", productId);
                return false;
            }

            Stock stock = stockOpt.get();
            if (stock.getQuantity() < quantity) {
                logger.warn("Insufficient stock for product ID: {}. Available: {}, Requested: {}",
                           productId, stock.getQuantity(), quantity);
                return false;
            }
        }

        logger.info("All items are available in inventory");
        return true;
    }

    /**
     * Check availability for multiple products and return detailed information per product.
     * 
     * @param items List of items with productId and quantity
     * @return Map of productId to availability details (available, quantity, reason)
     */
    @Bulkhead(name = "inventory-checks", fallbackMethod = "checkMultipleAvailabilityFallback", type = Bulkhead.Type.SEMAPHORE)
    public Map<Long, Map<String, Object>> checkMultipleAvailability(List<Map<String, Object>> items) {
        logger.debug("Checking detailed availability for {} items", items.size());
        
        Map<Long, Map<String, Object>> results = new java.util.HashMap<>();
        
        for (Map<String, Object> item : items) {
            Long productId = ((Number) item.get("productId")).longValue();
            Integer requestedQuantity = ((Number) item.get("quantity")).intValue();
            
            logger.debug("Checking availability for product ID: {}, requested quantity: {}", 
                        productId, requestedQuantity);
            
            Optional<Stock> stockOpt = getStockByProductId(productId);
            
            if (stockOpt.isEmpty()) {
                logger.warn("Product ID: {} not found in inventory", productId);
                results.put(productId, Map.of(
                    "available", false,
                    "quantity", 0,
                    "reason", "Product not found"
                ));
                continue;
            }
            
            Stock stock = stockOpt.get();
            int availableQuantity = stock.getQuantity();
            
            if (availableQuantity >= requestedQuantity) {
                logger.debug("Product ID: {} has sufficient stock. Available: {}, Requested: {}", 
                            productId, availableQuantity, requestedQuantity);
                results.put(productId, Map.of(
                    "available", true,
                    "quantity", availableQuantity,
                    "reason", "In stock"
                ));
            } else {
                logger.warn("Product ID: {} has insufficient stock. Available: {}, Requested: {}", 
                           productId, availableQuantity, requestedQuantity);
                results.put(productId, Map.of(
                    "available", false,
                    "quantity", availableQuantity,
                    "reason", "Insufficient stock"
                ));
            }
        }
        
        logger.info("Availability check completed for {} items", items.size());
        return results;
    }

    public void deleteProduct(Long productId) {
        logger.info("Deleting product with ID: {}", productId);
        if (!productRepository.existsById(productId)) {
            logger.error("Product with ID: {} not found for deletion", productId);
            throw new RuntimeException("Product not found with id: " + productId);
        }
        Optional<Stock> stockOpt = stockRepository.findByProductId(productId);
        Stock stock = stockOpt.orElse(null);
        if (stock != null) {
            stockRepository.delete(stock);
            logger.debug("Stock for product ID: {} deleted", productId);
        } else {
            logger.warn("No stock found for product ID: {} during deletion", productId);
        }
        productRepository.deleteById(productId);
        logger.info("Product with ID: {} deleted successfully", productId);
    }

    // Fallback methods for Bulkhead pattern

    /**
     * Fallback method when inventory checks bulkhead is full
     */
    private Map<Long, Map<String, Object>> checkMultipleAvailabilityFallback(List<Map<String, Object>> items, Exception ex) {
        logger.error("Inventory checks bulkhead full for {} items. Error: {}", items.size(), ex.getMessage());
        logger.warn("Inventory service at capacity - marking all items as unavailable");
        
        Map<Long, Map<String, Object>> results = new java.util.HashMap<>();
        for (Map<String, Object> item : items) {
            Long productId = ((Number) item.get("productId")).longValue();
            results.put(productId, Map.of(
                "available", false,
                "reason", "Inventory service at capacity - please retry",
                "fallback", true,
                "requestedQuantity", item.get("quantity")
            ));
        }
        return results;
    }

    /**
     * Fallback method when stock updates bulkhead is full
     */
    private Stock updateStockFallback(Long productId, Integer quantity, Exception ex) {
        logger.error("Stock updates bulkhead full for product {}. Quantity: {}. Error: {}", 
                productId, quantity, ex.getMessage());
        logger.warn("Stock update service at capacity - rejecting stock update for product {}", productId);
        
        // Return existing stock without changes to indicate failure
        Optional<Stock> existingStock = stockRepository.findByProductId(productId);
        if (existingStock.isPresent()) {
            return existingStock.get();
        } else {
            // Create a placeholder stock to indicate the failure
            Stock failureStock = Stock.builder()
                .product(null) // Will be null to indicate failure
                .quantity(-1)  // Negative quantity to indicate failure
                .minThreshold(0)
                .build();
            return failureStock;
        }
    }
}
