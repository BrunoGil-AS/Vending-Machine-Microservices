package com.vendingmachine.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Cloud Gateway Route Configuration
 */
@Configuration
public class GatewayConfig {
    
    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                // Ignore favicon requests
                .route("favicon", r -> r
                        .path("/favicon.ico")
                        .filters(f -> f.rewritePath("/favicon.ico", "/"))
                        .uri("no://op"))
                // Inventory Service Routes
                .route("inventory-service-public", r -> r
                        .path("/api/inventory/**")
                        .uri("lb://inventory-service"))
                
                .route("inventory-service-admin", r -> r
                        .path("/api/admin/inventory/**")
                        .uri("lb://inventory-service"))
                
                // Payment Service Routes
                .route("payment-service-public", r -> r
                        .path("/api/payment/**")
                        .uri("lb://payment-service"))
                
                .route("payment-service-admin", r -> r
                        .path("/api/admin/payment/**")
                        .uri("lb://payment-service"))
                
                // Transaction Service Routes
                .route("transaction-service-public", r -> r
                        .path("/api/transaction/**")
                        .uri("lb://transaction-service"))
                
                .route("transaction-service-admin", r -> r
                        .path("/api/admin/transaction/**")
                        .uri("lb://transaction-service"))
                
                // Dispensing Service Routes
                .route("dispensing-service-admin", r -> r
                        .path("/api/admin/dispensing/**")
                        .uri("lb://dispensing-service"))
                
                // Notification Service Routes
                .route("notification-service-admin", r -> r
                        .path("/api/admin/notification/**")
                        .uri("lb://notification-service"))
                
                .build();
    }
}
