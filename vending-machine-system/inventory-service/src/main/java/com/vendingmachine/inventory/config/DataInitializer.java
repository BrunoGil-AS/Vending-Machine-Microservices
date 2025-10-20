package com.vendingmachine.inventory.config;

import com.vendingmachine.common.event.StockUpdateEvent;
import com.vendingmachine.inventory.kafka.KafkaProducerService;
import com.vendingmachine.inventory.product.Product;
import com.vendingmachine.inventory.product.ProductRepository;
import com.vendingmachine.inventory.product.dto.PostProductDTO;
import com.vendingmachine.inventory.stock.Stock;
import com.vendingmachine.inventory.stock.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
            List<PostProductDTO> initialProducts = getInitialProducts();
            int loaded = 0;

            for (PostProductDTO data : initialProducts) {
                Product product = Product.builder()
                        .name(data.getName())
                        .price(data.getPrice())
                        .description(data.getDescription())
                        .build();

                product = productRepository.save(product);

                Stock stock = Stock.builder()
                        .product(product)
                        .quantity(data.getQuantity())
                        .minThreshold(data.getMinThreshold())
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

    private List<PostProductDTO> getInitialProducts() {
        List<PostProductDTO> products = new ArrayList<>();

        products.add(PostProductDTO.builder()
                .name("Coca Cola")
                .price(1.50)
                .description("Refreshing cola beverage 330ml")
                .quantity(15)
                .minThreshold(5)
                .build());
        products.add(PostProductDTO.builder()
                .name("Pepsi")
                .price(1.50)
                .description("Classic cola drink 330ml")
                .quantity(12)
                .minThreshold(5)
                .build());
        products.add(PostProductDTO.builder()
                .name("Sprite")
                .price(1.50)
                .description("Lemon-lime flavored soda 330ml")
                .quantity(18)
                .minThreshold(5)
                .build());
        products.add(PostProductDTO.builder()
                .name("Water")
                .price(1.00)
                .description("Pure mineral water 500ml")
                .quantity(20)
                .minThreshold(5)
                .build());
        products.add(PostProductDTO.builder()
                .name("Orange Juice")
                .price(2.00)
                .description("Fresh orange juice 250ml")
                .quantity(10)
                .minThreshold(5)
                .build());
        products.add(PostProductDTO.builder()
                .name("Coffee")
                .price(2.50)
                .description("Hot brewed coffee 200ml")
                .quantity(8)
                .minThreshold(5)
                .build());
        products.add(PostProductDTO.builder()
                .name("Green Tea")
                .price(1.75)
                .description("Unsweetened green tea 330ml")
                .quantity(14)
                .minThreshold(5)
                .build());
        products.add(PostProductDTO.builder()
                .name("Chocolate Bar")
                .price(1.25)
                .description("Milk chocolate bar 50g")
                .quantity(25)
                .minThreshold(5)
                .build());
        products.add(PostProductDTO.builder()
                .name("Chips")
                .price(1.50)
                .description("Crispy potato chips 100g")
                .quantity(16)
                .minThreshold(5)
                .build());
        products.add(PostProductDTO.builder()
                .name("Energy Drink")
                .price(2.75)
                .description("Energy boost drink 250ml")
                .quantity(11)
                .minThreshold(5)
                .build());

        return products;
    }
}
