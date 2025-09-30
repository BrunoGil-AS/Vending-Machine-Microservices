package com.vendingmachine.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * Config Server Application
 * 
 * Provides centralized configuration management for all microservices
 * using local file-based configuration storage.
 * 
 * Port: 8888
 */
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
        System.out.println("===========================================");
        System.out.println("Config Server started successfully!");
        System.out.println("Running on: http://localhost:8888");
        System.out.println("===========================================");
    }
}
