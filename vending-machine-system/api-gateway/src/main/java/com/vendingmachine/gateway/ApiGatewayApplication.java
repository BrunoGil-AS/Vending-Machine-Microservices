package com.vendingmachine.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * API Gateway Application
 * 
 * Entry point with JWT authentication and routing to microservices.
 * Handles authentication, authorization, and request routing.
 * 
 * Port: 8080
 */
@SpringBootApplication
@EnableDiscoveryClient
public class ApiGatewayApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
        System.out.println("===========================================");
        System.out.println("API Gateway started successfully!");
        System.out.println("Running on: http://localhost:8080");
        System.out.println("===========================================");
    }
}
