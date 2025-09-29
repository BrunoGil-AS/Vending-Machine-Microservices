package com.vendingmachine.dispensing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class DispensingServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(DispensingServiceApplication.class, args);
    }
}