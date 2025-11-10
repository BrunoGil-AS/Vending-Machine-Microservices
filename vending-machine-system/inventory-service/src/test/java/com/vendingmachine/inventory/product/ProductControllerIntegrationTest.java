package com.vendingmachine.inventory.product;

import com.vendingmachine.inventory.InventoryServiceApplication;
import com.vendingmachine.inventory.config.TestKafkaConfig;
import com.vendingmachine.inventory.product.dto.PostProductDTO;
import com.vendingmachine.inventory.stock.Stock;
import com.vendingmachine.inventory.stock.StockRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration tests for the Product Controller endpoints.
 * Tests the interaction between Product and Stock entities through REST API endpoints.
 *
 * Test coverage includes:
 * - Product creation with initial stock
 * - Stock availability checking
 * - Stock updates
 *
 * The test class uses:
 * - TestRestTemplate for making HTTP requests
 * - ProductRepository for product data access
 * - StockRepository for stock data access
 * 
 * Each test performs cleanup before execution by removing all existing products and stocks.
 *
 * @see com.vendingmachine.inventory.product.Product
 * @see com.vendingmachine.inventory.product.Stock
 * @see com.vendingmachine.inventory.product.ProductController
 */
@SpringBootTest(classes = InventoryServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestKafkaConfig.class})
@ActiveProfiles("test")
public class ProductControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockRepository stockRepository;

    private String baseUrl() {
        return "/api";
    }

    @BeforeEach
    void cleanup() {
        stockRepository.deleteAll();
        productRepository.deleteAll();
    }

    @Test
    void whenPostProduct_thenProductAndStockCreated_andAvailabilityEndpointReturnsStock() {
        PostProductDTO dto = PostProductDTO.builder()
                .name("Pepsi")
                .price(1.25)
                .description("Soda")
                .quantity(15)
                .minThreshold(5)  // Add minThreshold to prevent null issues
                .build();

        // Create headers for admin access
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Service", "TEST_GATEWAY_ID");
        headers.set("X-User-Role", "ADMIN");
        headers.set("X-User-Id", "1");
        headers.set("X-Username", "testuser");
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<PostProductDTO> entity = new HttpEntity<>(dto, headers);

        ResponseEntity<Product> postResponse;
        Product created;
        
        try {
            postResponse = restTemplate.postForEntity(
                    baseUrl() + "/admin/inventory/products", 
                    entity, 
                    Product.class);
        } catch (Exception e) {
            // Try to get the actual response as String to see what we're getting
            ResponseEntity<String> errorResponse = restTemplate.postForEntity(
                    baseUrl() + "/admin/inventory/products", 
                    entity, 
                    String.class);
            
            System.out.println("Error response status: " + errorResponse.getStatusCode());
            System.out.println("Error response body: " + errorResponse.getBody());
            throw e;
        }

        Assertions.assertEquals(201, postResponse.getStatusCode().value());
        created = postResponse.getBody();
        Assertions.assertNotNull(created);
        Assertions.assertNotNull(created.getId());
        Assertions.assertEquals(dto.getName(), created.getName());
        Assertions.assertEquals(dto.getPrice(), created.getPrice());
        Assertions.assertEquals(dto.getDescription(), created.getDescription());

        // Check stock created
        Stock stock = stockRepository.findByProductId(created.getId()).orElse(null);
        Assertions.assertNotNull(stock);
        Assertions.assertEquals(15, stock.getQuantity());
        Assertions.assertEquals(5, stock.getMinThreshold());
        Assertions.assertEquals(created.getId(), stock.getProduct().getId());

        // availability endpoint
        HttpHeaders getHeaders = new HttpHeaders();
        getHeaders.set("X-Internal-Service", "TEST_GATEWAY_ID");
        getHeaders.set("X-User-Id", "1");
        getHeaders.set("X-User-Role", "ADMIN");
        getHeaders.set("X-Username", "testuser");
        HttpEntity<String> getEntity = new HttpEntity<>(getHeaders);
        
        ResponseEntity<Stock> avail = restTemplate.exchange(
                baseUrl() + "/inventory/availability/" + created.getId(),
                HttpMethod.GET,
                getEntity,
                Stock.class);
        
        Assertions.assertEquals(200, avail.getStatusCode().value());
        Stock returnedStock = avail.getBody();
        Assertions.assertNotNull(returnedStock);
        Assertions.assertEquals(15, returnedStock.getQuantity());
        Assertions.assertEquals(stock.getId(), returnedStock.getId());
        Assertions.assertEquals(created.getId(), returnedStock.getProduct().getId());
    }

    @Test
    void whenPutUpdateStock_thenStockUpdated() {
        // create product and stock directly
        Product p = Product.builder()
                .name("Water")
                .price(0.9)
                .description("Bottle")
                .build();
        Product saved = productRepository.save(p);
        
        Stock s = Stock.builder()
                .product(saved)
                .quantity(5)
                .minThreshold(1)
                .build();
        s = stockRepository.save(s);
        
        saved.setStock(s);
        productRepository.save(saved);

        // update stock via controller
        Stock update = Stock.builder()
                .quantity(30)
                .minThreshold(2)
                .build();
        
        // Create headers for admin access
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Service", "TEST_GATEWAY_ID");
        headers.set("X-User-Role", "ADMIN");
        headers.set("X-User-Id", "1");
        headers.set("X-Username", "testuser");
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<Stock> entity = new HttpEntity<>(update, headers);
        ResponseEntity<Stock> resp = restTemplate.exchange(
                baseUrl() + "/admin/inventory/stock/" + saved.getId(),
                HttpMethod.PUT,
                entity,
                Stock.class);
        
        Assertions.assertEquals(200, resp.getStatusCode().value());
        Stock updated = resp.getBody();
        Assertions.assertNotNull(updated);
        Assertions.assertEquals(30, updated.getQuantity());
        Assertions.assertEquals(2, updated.getMinThreshold());
        Assertions.assertEquals(saved.getId(), updated.getProduct().getId());
        
        // Verify in database
        Stock verifyStock = stockRepository.findByProductId(saved.getId()).orElse(null);
        Assertions.assertNotNull(verifyStock);
        Assertions.assertEquals(30, verifyStock.getQuantity());
        Assertions.assertEquals(2, verifyStock.getMinThreshold());
    }
}
