package com.vendingmachine.inventory;

import com.vendingmachine.common.event.StockUpdateEvent;
import com.vendingmachine.common.event.LowStockAlertEvent;
import com.vendingmachine.inventory.kafka.KafkaProducerService;
import com.vendingmachine.inventory.product.Product;
import com.vendingmachine.inventory.product.ProductRepository;
import com.vendingmachine.inventory.product.ProductUtils;
import com.vendingmachine.inventory.product.dto.PostProductDTO;
import com.vendingmachine.inventory.stock.Stock;
import com.vendingmachine.inventory.stock.StockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class InventoryService {

    private static final Logger logger = LoggerFactory.getLogger(InventoryService.class);// change to notation @Log

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
                    .minThreshold(10)
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

    public Stock updateStock(Long productId, Integer quantity) {
        logger.info("Updating stock for product ID: {}, quantity change: {}", productId, quantity);
        Stock existingStock = stockRepository.findByProductId(productId)
                .orElseThrow(() -> new RuntimeException("Stock not found for product id: " + productId));
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
}
