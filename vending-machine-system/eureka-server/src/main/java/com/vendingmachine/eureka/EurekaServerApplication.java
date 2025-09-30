package com.vendingmachine.eureka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Eureka Server Application
 * 
 * Service Discovery and Registration server for the vending machine microservices.
 * Provides service registry, health monitoring, and load balancing metadata.
 * 
 * Port: 8761
 * Dashboard: http://localhost:8761
 */
@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
        System.out.println("===========================================");
        System.out.println("Eureka Server started successfully!");
        System.out.println("Dashboard: http://localhost:8761");
        System.out.println("===========================================");
    }
}