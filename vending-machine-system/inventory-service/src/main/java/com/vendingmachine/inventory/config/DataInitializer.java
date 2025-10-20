package com.vendingmachine.inventory.config;

import com.vendingmachine.common.event.StockUpdateEvent;
import com.vendingmachine.inventory.kafka.KafkaProducerService;
import com.vendingmachine.inventory.product.Product;
import com.vendingmachine.inventory.product.ProductRepository;
import com.vendingmachine.inventory.stock.Stock;
import com.vendingmachine.inventory.stock.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
@Profile({"default", "develop"})
public class DataInitializer implements CommandLineRunner {

    private final ProductRepository productRepository;
    private final StockRepository stockRepository;
    private final KafkaProducerService kafkaProducerService;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Checking if initial data needs to be loaded...");

        // Check if products already exist
        long productCount = productRepository.count();
        if (productCount > 0) {
            log.info("Database already contains {} products. Skipping initial data load.", productCount);
            return;
        }

        log.info("Database is empty. Loading initial product data...");

        try {
            List<ProductData> initialProducts = getInitialProducts();
            int loaded = 0;

            for (ProductData data : initialProducts) {
                Product product = Product.builder()
                        .name(data.name)
                        .price(data.price.doubleValue())
                        .description(data.description)
                        .build();

                product = productRepository.save(product);

                Stock stock = Stock.builder()
                        .product(product)
                        .quantity(data.stockQuantity)
                        .build();

                stockRepository.save(stock);
                loaded++;

                // Publish stock update event to Kafka
                StockUpdateEvent stockEvent = new StockUpdateEvent(
                        UUID.randomUUID().toString(),
                        product.getId(),
                        stock.getQuantity(),
                        "INITIAL_LOAD",
                        System.currentTimeMillis()
                );
                kafkaProducerService.send("stock-update-events", stockEvent);

                log.debug("Loaded product: {} with stock: {} and published stock event", 
                         product.getName(), stock.getQuantity());
            }

            log.info("Successfully loaded {} products with initial stock and published stock events", loaded);

        } catch (Exception e) {
            log.error("Failed to load initial data", e);
            throw new RuntimeException("Failed to initialize database with sample data", e);
        }
    }

    private List<ProductData> getInitialProducts() {
        List<ProductData> products = new ArrayList<>();

        products.add(new ProductData("Coca Cola", new BigDecimal("1.50"), "Refreshing cola beverage 330ml", 15));
        products.add(new ProductData("Pepsi", new BigDecimal("1.50"), "Classic cola drink 330ml", 12));
        products.add(new ProductData("Sprite", new BigDecimal("1.50"), "Lemon-lime flavored soda 330ml", 18));
        products.add(new ProductData("Water", new BigDecimal("1.00"), "Pure mineral water 500ml", 20));
        products.add(new ProductData("Orange Juice", new BigDecimal("2.00"), "Fresh orange juice 250ml", 10));
        products.add(new ProductData("Coffee", new BigDecimal("2.50"), "Hot brewed coffee 200ml", 8));
        products.add(new ProductData("Green Tea", new BigDecimal("1.75"), "Unsweetened green tea 330ml", 14));
        products.add(new ProductData("Chocolate Bar", new BigDecimal("1.25"), "Milk chocolate bar 50g", 25));
        products.add(new ProductData("Chips", new BigDecimal("1.50"), "Crispy potato chips 100g", 16));
        products.add(new ProductData("Energy Drink", new BigDecimal("2.75"), "Energy boost drink 250ml", 11));

        return products;
    }

    private static class ProductData {
        String name;
        BigDecimal price;
        String description;
        int stockQuantity;

        ProductData(String name, BigDecimal price, String description, int stockQuantity) {
            this.name = name;
            this.price = price;
            this.description = description;
            this.stockQuantity = stockQuantity;
        }
    }
}
