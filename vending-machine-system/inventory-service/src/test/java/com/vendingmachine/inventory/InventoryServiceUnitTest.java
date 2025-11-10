package com.vendingmachine.inventory;

import com.vendingmachine.inventory.kafka.KafkaProducerService;
import com.vendingmachine.inventory.product.Product;
import com.vendingmachine.inventory.product.ProductRepository;
import com.vendingmachine.inventory.product.dto.PostProductDTO;
import com.vendingmachine.inventory.stock.Stock;
import com.vendingmachine.inventory.stock.StockRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.mockito.Mockito.*;

public class InventoryServiceUnitTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private StockRepository stockRepository;

    @Mock
    private KafkaProducerService kafkaProducerService;

    @InjectMocks
    private InventoryService inventoryService;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void addProduct_createsStockAndSavesBoth() {
        PostProductDTO dto = PostProductDTO.builder()
                .name("Juice")
                .price(2.0)
                .description("Orange")
                .quantity(12)
                .minThreshold(5)  // Add minThreshold to prevent null
                .build();

        // simulate repository behavior: when saving product assign an id
        doAnswer(invocation -> {
            Product arg = invocation.getArgument(0);
            arg.setId(100L);
            return arg;
        }).when(productRepository).save(any(Product.class));

        when(stockRepository.save(any(Stock.class))).thenAnswer(invocation -> {
            Stock s = invocation.getArgument(0);
            s.setId(50L);
            return s;
        });

        Product result = inventoryService.addProduct(dto);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(100L, result.getId());
        Assertions.assertNotNull(result.getStock());
        Assertions.assertEquals(12, result.getStock().getQuantity());
        Assertions.assertEquals(5, result.getStock().getMinThreshold());

        ArgumentCaptor<Stock> stockCaptor = ArgumentCaptor.forClass(Stock.class);
        verify(stockRepository, times(1)).save(stockCaptor.capture());
        Stock savedStock = stockCaptor.getValue();
        // the stock should reference the saved product
        Assertions.assertNotNull(savedStock.getProduct());
        Assertions.assertEquals(100L, savedStock.getProduct().getId());
        
        // Verify Kafka producer was called (only once for stock update event since quantity 20 is above threshold 5)
        verify(kafkaProducerService, times(1)).send(anyString(), any());
    }

    @Test
    void updateStock_updatesStockForProduct() {
        Product p = Product.builder().id(200L).name("Snack").price(1.0).description("Chips").build();
        Stock existingStock = Stock.builder().id(1L).product(p).quantity(10).minThreshold(2).build();

        when(stockRepository.findByProductId(200L)).thenReturn(Optional.of(existingStock));

        Stock toSave = Stock.builder().quantity(77).minThreshold(5).build();
        when(stockRepository.save(any(Stock.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Stock updated = inventoryService.updateStock(200L, toSave);

        Assertions.assertNotNull(updated);
        Assertions.assertEquals(77, updated.getQuantity());
        Assertions.assertEquals(5, updated.getMinThreshold());
        Assertions.assertEquals(p, updated.getProduct());
        
        // Verify Kafka producer was called
        verify(kafkaProducerService, times(1)).send(anyString(), any());
    }
}
