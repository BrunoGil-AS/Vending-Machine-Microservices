package com.vendingmachine.inventory;

import com.vendingmachine.common.aop.annotation.Auditable;
import com.vendingmachine.common.aop.annotation.ExecutionTime;
import com.vendingmachine.inventory.kafka.KafkaProducerService;
import com.vendingmachine.inventory.kafka.InventoryKafkaEventService;
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

@SuppressWarnings("unused")
@Service
public class InventoryService {

    private static final Logger logger = LoggerFactory.getLogger(InventoryService.class);

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private KafkaProducerService kafkaProducerService;

    @Autowired
    private InventoryKafkaEventService inventoryKafkaEventService;

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

        // Set default minThreshold if not provided
        Integer minThreshold = product.getMinThreshold();
        if (minThreshold == null) {
            minThreshold = 5; // Default minimum threshold
        }

        Stock stock = Stock.builder()
                .product(newProduct)
                .quantity(product.getQuantity())
                .minThreshold(minThreshold)
                .build();
        newProduct.setStock(stock);
        productRepository.save(newProduct);
        stockRepository.save(stock);
        logger.info("Product added successfully with ID: {}, initial stock: {}", newProduct.getId(),
                product.getQuantity());

        // Log stock status after product creation
        if (stock.getQuantity() <= 0) {
            logger.warn("Product ID: {} added with out of stock", newProduct.getId());
        } else if (stock.getQuantity() < stock.getMinThreshold()) {
            logger.warn("Product ID: {} added with low stock. Current: {}, Threshold: {}",
                    newProduct.getId(), stock.getQuantity(), stock.getMinThreshold());
        }

        // Publish stock update event for initial stock with complete payload
        inventoryKafkaEventService.publishStockUpdateEventWithCompleteData(stock, "INITIAL_STOCK");
        logger.info("Published initial stock update event with complete data for product: {}", newProduct.getId());

        // Publish low stock alert if applicable
        if (stock.getQuantity() < stock.getMinThreshold() && stock.getQuantity() > 0) {
            inventoryKafkaEventService.publishLowStockAlertWithCompleteData(stock, "LOW_STOCK");
            logger.info("Published low stock alert event with complete data for new product: {}", newProduct.getId());
        } else if (stock.getQuantity() <= 0) {
            inventoryKafkaEventService.publishLowStockAlertWithCompleteData(stock, "OUT_OF_STOCK");
            logger.warn("Published out of stock alert event with complete data for new product: {}",
                    newProduct.getId());
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

        // Set default minThreshold if not provided
        Integer minThreshold = productDTO.getMinThreshold();
        if (minThreshold == null) {
            minThreshold = existingStock.getMinThreshold() != null ? existingStock.getMinThreshold() : 5;
        }
        existingStock.setMinThreshold(minThreshold);

        // Save both entities
        Product updatedProduct = productRepository.save(existingProduct);
        Stock updatedStock = stockRepository.save(existingStock);

        logger.info(
                "Product updated successfully with ID: {}. Previous quantity: {}, New quantity: {}, Min threshold: {}",
                productId, previousQuantity, updatedStock.getQuantity(), updatedStock.getMinThreshold());

        // Determine change type and quantity changed
        String changeType;
        if (updatedStock.getQuantity() > previousQuantity) {
            changeType = "INCREASE";
        } else if (updatedStock.getQuantity() < previousQuantity) {
            changeType = "DECREASE";
        } else {
            changeType = "NO_CHANGE";
        }

        // Publish stock update event with complete payload
        inventoryKafkaEventService.publishStockUpdateEventWithCompleteData(updatedStock, changeType);
        logger.info("Published stock update event with complete data for product: {}", productId);

        // Publish low stock alert if applicable
        if (updatedStock.getQuantity() < updatedStock.getMinThreshold() && updatedStock.getQuantity() > 0) {
            inventoryKafkaEventService.publishLowStockAlertWithCompleteData(updatedStock, "LOW_STOCK");
            logger.info("Published low stock alert event with complete data for product: {}", productId);
        } else if (updatedStock.getQuantity() <= 0) {
            inventoryKafkaEventService.publishLowStockAlertWithCompleteData(updatedStock, "OUT_OF_STOCK");
            logger.warn("Published out of stock alert event with complete data for product: {}", productId);
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

        // Determine change type and quantity changed
        String changeType;
        if (updatedStock.getQuantity() > previousQuantity) {
            changeType = "INCREASE";
        } else if (updatedStock.getQuantity() < previousQuantity) {
            changeType = "DECREASE";
        } else {
            changeType = "NO_CHANGE";
        }

        // Publish stock update event with complete payload
        inventoryKafkaEventService.publishStockUpdateEventWithCompleteData(updatedStock, changeType);
        logger.info("Published stock update event with complete data for product: {}", productId);

        // Publish low stock alert if applicable
        if (updatedStock.getQuantity() < updatedStock.getMinThreshold() && updatedStock.getQuantity() > 0) {
            inventoryKafkaEventService.publishLowStockAlertWithCompleteData(updatedStock, "LOW_STOCK");
            logger.info("Published low stock alert event with complete data for product: {}", productId);
        } else if (updatedStock.getQuantity() <= 0) {
            inventoryKafkaEventService.publishLowStockAlertWithCompleteData(updatedStock, "OUT_OF_STOCK");
            logger.warn("Published out of stock alert event with complete data for product: {}", productId);
        }

        return updatedStock;
    }

    public Stock updateStock(Long productId, Stock stock) {
        logger.info("Updating stock using Stock object for product ID: {}", productId);
        Stock existingStock = stockRepository.findByProductId(productId)
                .orElseThrow(() -> new RuntimeException("Stock not found for product id: " + productId));

        int previousQuantity = existingStock.getQuantity();
        existingStock.setQuantity(stock.getQuantity());
        existingStock.setMinThreshold(
                stock.getMinThreshold() != null ? stock.getMinThreshold() : existingStock.getMinThreshold());

        Stock updatedStock = stockRepository.save(existingStock);

        logger.info("Stock updated using Stock object for product ID: {}. Previous: {}, New: {}, Min threshold: {}",
                productId, previousQuantity, updatedStock.getQuantity(), updatedStock.getMinThreshold());

        // Determine change type and quantity changed
        String changeType;
        if (updatedStock.getQuantity() > previousQuantity) {
            changeType = "INCREASE";
        } else if (updatedStock.getQuantity() < previousQuantity) {
            changeType = "DECREASE";
        } else {
            changeType = "NO_CHANGE";
        }

        // Publish stock update event with complete payload
        inventoryKafkaEventService.publishStockUpdateEventWithCompleteData(updatedStock, changeType);
        logger.info("Published stock update event with complete data for product: {}", productId);

        // Publish low stock alert if applicable
        if (updatedStock.getQuantity() < updatedStock.getMinThreshold() && updatedStock.getQuantity() > 0) {
            inventoryKafkaEventService.publishLowStockAlertWithCompleteData(updatedStock, "LOW_STOCK");
            logger.info("Published low stock alert event with complete data for product: {}", productId);
        } else if (updatedStock.getQuantity() <= 0) {
            inventoryKafkaEventService.publishLowStockAlertWithCompleteData(updatedStock, "OUT_OF_STOCK");
            logger.warn("Published out of stock alert event with complete data for product: {}", productId);
        }

        return updatedStock;
    }

    @ExecutionTime(operation = "checkInventoryAvailability", warningThreshold = 500)
    public boolean checkInventoryAvailability(List<Map<String, Object>> items) {
        logger.debug("Checking inventory availability for {} items", items.size());

        for (Map<String, Object> item : items) {
            Long productId = ((Number) item.get("productId")).longValue();
            Integer requestedQuantity = (Integer) item.get("quantity");

            Optional<Stock> stock = stockRepository.findByProductId(productId);

            if (stock.isEmpty() || stock.get().getQuantity() < requestedQuantity) {
                logger.warn("Insufficient stock for product ID: {}. Requested: {}, Available: {}",
                        productId, requestedQuantity, stock.map(Stock::getQuantity).orElse(0));
                return false;
            }
        }

        logger.debug("All items are available in inventory");
        return true;
    }

    /**
     * Check availability for multiple products and return detailed information per
     * product.
     * 
     * @param items List of items with productId and quantity
     * @return Map of productId to availability details (available, quantity,
     *         reason)
     */
    @Bulkhead(name = "inventory-checks", fallbackMethod = "checkMultipleAvailabilityFallback", type = Bulkhead.Type.SEMAPHORE)
    public Map<Long, Map<String, Object>> checkMultipleAvailability(List<Map<String, Object>> items) {
        logger.debug("Checking multiple availability for {} items", items.size());

        Map<Long, Map<String, Object>> results = new java.util.HashMap<>();

        for (Map<String, Object> item : items) {
            Long productId = ((Number) item.get("productId")).longValue();
            Integer requestedQuantity = (Integer) item.get("quantity");

            Optional<Stock> stockOpt = stockRepository.findByProductId(productId);
            Map<String, Object> availability = new java.util.HashMap<>();

            if (stockOpt.isEmpty()) {
                availability.put("available", false);
                availability.put("quantity", 0);
                availability.put("reason", "Product not found");
            } else {
                Stock stock = stockOpt.get();
                boolean available = stock.getQuantity() >= requestedQuantity;
                availability.put("available", available);
                availability.put("quantity", stock.getQuantity());
                availability.put("reason", available ? "Available" : "Insufficient stock");
            }

            results.put(productId, availability);

            logger.debug("Availability check for product {}: {}", productId, availability);
        }

        return results;
    }

    public void deleteProduct(Long productId) {
        logger.info("Deleting product with ID: {}", productId);

        // Check if product exists
        if (!productRepository.existsById(productId)) {
            throw new RuntimeException("Product not found with id: " + productId);
        }

        productRepository.deleteById(productId);
        logger.info("Product deleted successfully with ID: {}", productId);
    }

    // Fallback methods for Bulkhead pattern

    /**
     * Fallback method when inventory checks bulkhead is full
     */
    private Map<Long, Map<String, Object>> checkMultipleAvailabilityFallback(List<Map<String, Object>> items,
            Exception ex) {
        logger.error("Inventory checks bulkhead full for {} items. Error: {}", items.size(), ex.getMessage());
        logger.warn("Inventory service availability checks at capacity - providing fallback response");

        Map<Long, Map<String, Object>> fallbackResults = new java.util.HashMap<>();
        for (Map<String, Object> item : items) {
            Long productId = ((Number) item.get("productId")).longValue();
            Map<String, Object> fallback = Map.of(
                    "available", false,
                    "quantity", 0,
                    "reason", "Service temporarily unavailable");
            fallbackResults.put(productId, fallback);
        }

        return fallbackResults;
    }

    /**
     * Fallback method when stock updates bulkhead is full
     */
    private Stock updateStockFallback(Long productId, Integer quantity, Exception ex) {
        logger.error("Stock updates bulkhead full for product: {}. Error: {}", productId, ex.getMessage());
        logger.warn("Inventory service stock updates at capacity - rejecting update for product {}", productId);

        // Return existing stock without updates when bulkhead is full
        Optional<Stock> existingStock = stockRepository.findByProductId(productId);
        if (existingStock.isPresent()) {
            return existingStock.get();
        } else {
            throw new RuntimeException("Product " + productId + " not found and service at capacity");
        }
    }
}
