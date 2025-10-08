package com.vendingmachine.gateway;

import com.vendingmachine.gateway.User.AdminUser;
import com.vendingmachine.gateway.User.AdminUserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

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
@Slf4j
public class ApiGatewayApplication {

    @Autowired
    private AdminUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;
    
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
        System.out.println("===========================================");
        System.out.println("API Gateway started successfully!");
        System.out.println("Running on: http://localhost:8080");
        System.out.println("===========================================");
    }

    @Bean
    public CommandLineRunner initializeAdminUser() {
        return args -> {
            userRepository.findByUsername("hardcoded-admin")
                .doOnNext(user -> log.info("Default admin user already exists"))
                .switchIfEmpty(createDefaultAdminUser())
                .subscribe();
        };
    }

    private reactor.core.publisher.Mono<AdminUser> createDefaultAdminUser() {
        AdminUser adminUser = AdminUser.builder()
                .username("hardcoded-admin")
                .passwordHash(passwordEncoder.encode("password123"))
                .role("SUPER_ADMIN")
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return userRepository.save(adminUser)
                .doOnSuccess(user -> log.info("Created default admin user: {}", user.getUsername()));
    }
}
