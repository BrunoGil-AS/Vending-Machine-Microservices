package com.vendingmachine.inventory;

import com.vendingmachine.inventory.product.Product;
import com.vendingmachine.inventory.product.ProductRepository;
import com.vendingmachine.inventory.product.dto.PostProductDTO;
import com.vendingmachine.inventory.stock.Stock;
import com.vendingmachine.inventory.stock.StockRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Optional;

@DataJpaTest
public class InventoryServiceTests {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockRepository stockRepository;

    @Test
    void whenSaveProduct_thenStockCanBeCreatedAndLinked() {
        PostProductDTO dto = PostProductDTO.builder()
                .name("Coke")
                .price(1.5)
                .description("Soda")
                .quantity(20)
                .build();

        // Convert to entity similarly to ProductUtils
        Product p = Product.builder()
                .name(dto.getName())
                .price(dto.getPrice())
                .description(dto.getDescription())
                .build();

        // save product first
        Product saved = productRepository.save(p);

        // create stock and link
        Stock stock = Stock.builder()
                .product(saved)
                .quantity(dto.getQuantity())
                .minThreshold(5)
                .build();

        Stock savedStock = stockRepository.save(stock);

        // assign bi-directional relation and save product again
        saved.setStock(savedStock);
        productRepository.save(saved);

        Optional<Stock> byProduct = stockRepository.findByProductId(saved.getId());
        Assertions.assertTrue(byProduct.isPresent());
        Assertions.assertEquals(20, byProduct.get().getQuantity());
        Assertions.assertEquals(saved.getId(), byProduct.get().getProduct().getId());
    }
}
