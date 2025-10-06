package com.vendingmachine.inventory.product;

import com.vendingmachine.inventory.InventoryService;
import com.vendingmachine.inventory.product.dto.PostProductDTO;
import com.vendingmachine.inventory.stock.Stock;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ProductController {

    @Autowired
    private InventoryService inventoryService;



    @GetMapping("/inventory/products")
    public List<Product> getAllProducts() {
        return inventoryService.getAllProducts();
    }

    @GetMapping("/inventory/availability/{productId}")
    public ResponseEntity<Stock> getAvailability(@PathVariable Long productId) {
        Stock stock = inventoryService.getStockByProductId(productId)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + productId));
        return ResponseEntity.ok(stock);
    }

    @PostMapping("/admin/inventory/products")
    public Product addProduct(@RequestBody  PostProductDTO product) {
        return inventoryService.addProduct(product);
    }

    @PutMapping("/admin/inventory/stock/{productId}")
    public Stock updateStock(@PathVariable Long productId, @RequestBody Stock stock) {
        return inventoryService.updateStock(productId, stock);
    }


}
