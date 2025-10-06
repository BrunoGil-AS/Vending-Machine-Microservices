package com.vendingmachine.inventory.product;

import com.vendingmachine.inventory.InventoryService;
import com.vendingmachine.inventory.product.dto.PostProductDTO;
import com.vendingmachine.inventory.stock.Stock;
import com.vendingmachine.inventory.stock.StockRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private StockRepository stockRepository;

    @InjectMocks
    private InventoryService inventoryService;

    @Test
    void getAllProducts_ShouldReturnListOfProducts() {
        // Arrange
        Product product1 = Product.builder()
                .id(1L)
                .name("Coca Cola")
                .price(1.50)
                .description("Refresco")
                .build();
        Product product2 = Product.builder()
                .id(2L)
                .name("Doritos")
                .price(1.25)
                .description("Snack")
                .build();

        when(productRepository.findAll()).thenReturn(Arrays.asList(product1, product2));

        // Act
        List<Product> result = inventoryService.getAllProducts();

        // Assert
        assertEquals(2, result.size());
        verify(productRepository, times(1)).findAll();
    }

    @Test
    void getStockByProductId_WhenExists_ShouldReturnStock() {
        // Arrange
        Long productId = 1L;
        Stock expectedStock = Stock.builder()
                .id(1L)
                .quantity(10)
                .minThreshold(5)
                .build();

        when(stockRepository.findByProductId(productId)).thenReturn(Optional.of(expectedStock));

        // Act
        Optional<Stock> result = inventoryService.getStockByProductId(productId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(expectedStock, result.get());
        verify(stockRepository, times(1)).findByProductId(productId);
    }

    @Test
    void addProduct_ShouldCreateProductAndStock() {
        // Arrange
        PostProductDTO dto = PostProductDTO.builder()
                .name("Pepsi")
                .price(1.50)
                .description("Refresco")
                .quantity(20)
                .build();

        Product savedProduct = Product.builder()
                .id(1L)
                .name(dto.getName())
                .price(dto.getPrice())
                .description(dto.getDescription())
                .build();

        Stock savedStock = Stock.builder()
                .id(1L)
                .product(savedProduct)
                .quantity(dto.getQuantity())
                .minThreshold(10)
                .build();

        savedProduct.setStock(savedStock);

        when(productRepository.save(any(Product.class))).thenReturn(savedProduct);
        when(stockRepository.save(any(Stock.class))).thenReturn(savedStock);

        // Act
        Product result = inventoryService.addProduct(dto);

        // Assert
        assertNotNull(result);
        assertEquals(dto.getName(), result.getName());
        assertEquals(dto.getPrice(), result.getPrice());
        assertEquals(dto.getDescription(), result.getDescription());
        assertNotNull(result.getStock());
        assertEquals(dto.getQuantity(), result.getStock().getQuantity());
        assertEquals(10, result.getStock().getMinThreshold());

        verify(productRepository, times(1)).save(any(Product.class));
        verify(stockRepository, times(1)).save(any(Stock.class));
    }

    @Test
    void updateStock_WhenProductExists_ShouldUpdateStock() {
        // Arrange
        Long productId = 1L;
        Product existingProduct = Product.builder()
                .id(productId)
                .name("Existing Product")
                .build();

        Stock newStock = Stock.builder()
                .quantity(15)
                .minThreshold(5)
                .build();

        when(productRepository.findById(productId)).thenReturn(Optional.of(existingProduct));
        when(stockRepository.save(any(Stock.class))).thenAnswer(i -> i.getArguments()[0]);

        // Act
        Stock result = inventoryService.updateStock(productId, newStock);

        // Assert
        assertNotNull(result);
        assertEquals(15, result.getQuantity());
        assertEquals(5, result.getMinThreshold());
        assertEquals(existingProduct, result.getProduct());

        verify(productRepository, times(1)).findById(productId);
        verify(stockRepository, times(1)).save(any(Stock.class));
    }

    @Test
    void updateStock_WhenProductNotExists_ShouldThrowException() {
        // Arrange
        Long productId = 999L;
        Stock newStock = Stock.builder()
                .quantity(15)
                .minThreshold(5)
                .build();

        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            inventoryService.updateStock(productId, newStock);
        });

        verify(productRepository, times(1)).findById(productId);
        verify(stockRepository, never()).save(any(Stock.class));
    }
}