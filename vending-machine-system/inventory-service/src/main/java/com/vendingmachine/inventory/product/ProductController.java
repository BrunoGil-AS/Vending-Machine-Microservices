package com.vendingmachine.inventory.product;

import com.vendingmachine.inventory.InventoryService;
import com.vendingmachine.inventory.product.dto.PostProductDTO;
import com.vendingmachine.inventory.stock.Stock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ProductController {

    private static final Logger logger = LoggerFactory.getLogger(ProductController.class);

    @Autowired
    private InventoryService inventoryService;



    @GetMapping("/inventory/products")
    public List<Product> getAllProducts() {
        logger.info("Received request to get all products");
        List<Product> products = inventoryService.getAllProducts();
        logger.info("Returning {} products", products.size());
        return products;
    }

    @GetMapping("/inventory/availability/{productId}")
    public ResponseEntity<Stock> getAvailability(@PathVariable Long productId) {
        logger.info("Received request to get availability for product ID: {}", productId);
        Stock stock = inventoryService.getStockByProductId(productId)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + productId));
        logger.info("Returning stock information for product ID: {}", productId);
        return ResponseEntity.ok(stock);
    }

    @PostMapping("/admin/inventory/products")
    public Product addProduct(@RequestBody  PostProductDTO product) {
        logger.info("Received request to add new product: {}", product.getName());
        Product newProduct = inventoryService.addProduct(product);
        logger.info("Product added successfully with ID: {}", newProduct.getId());
        return newProduct;
    }

    @PutMapping("/admin/inventory/stock/{productId}")
    public Stock updateStock(@PathVariable Long productId, @RequestBody Stock stock) {
        logger.info("Received request to update stock for product ID: {}", productId);
        Stock updatedStock = inventoryService.updateStock(productId, stock);
        logger.info("Stock updated successfully for product ID: {}", productId);
        return updatedStock;
    }


}
