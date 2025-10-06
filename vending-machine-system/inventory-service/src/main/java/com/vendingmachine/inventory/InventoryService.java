package com.vendingmachine.inventory;

import com.vendingmachine.inventory.product.Product;
import com.vendingmachine.inventory.product.ProductRepository;
import com.vendingmachine.inventory.product.ProductUtils;
import com.vendingmachine.inventory.product.dto.PostProductDTO;
import com.vendingmachine.inventory.stock.Stock;
import com.vendingmachine.inventory.stock.StockRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class InventoryService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockRepository stockRepository;

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public Optional<Stock> getStockByProductId(Long productId) {
        return stockRepository.findByProductId(productId);
    }

    public Product addProduct(PostProductDTO product) {
        Product newProduct = ProductUtils.convertToEntity(product);
        Stock stock = Stock.builder()
                    .product(newProduct)
                    .quantity(product.getQuantity())
                    .minThreshold(10)
                    .build();
        newProduct.setStock(stock);
        productRepository.save(newProduct);
        stockRepository.save(stock);
        return newProduct;
    }

    public Stock updateStock(Long productId, Stock stock) {
        Product product = productRepository.findById(productId).orElseThrow(() -> new RuntimeException("Product not found"));
        stock.setProduct(product);
        return stockRepository.save(stock);
    }
}
