package com.vendingmachine.gateway.User.Auth;


import com.vendingmachine.gateway.User.DTO.*;
import com.vendingmachine.gateway.User.Login.DTO.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * Reactive Authentication and User Management REST Controller
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {
    
    private final AuthService authService;
    
    /**
     * Login endpoint - Public access
     */
    @PostMapping("/login")
    public Mono<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login request received for user: {}", request.getUsername());
        
        return authService.login(request)
                .map(response -> createSuccessResponse(response, "Login successful"))
                .onErrorResume(e -> {
                    log.error("Login failed: {}", e.getMessage());
                    return Mono.just(createErrorResponse(e.getMessage()));
                });
    }
    
    /**
     * Create new user - Admin only
     */
    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Map<String, Object>> createUser(@Valid @RequestBody CreateUserRequest request) {
        log.info("Create user request received: {}", request.getUsername());
        
        return authService.createUser(request)
                .map(response -> createSuccessResponse(response, "User created successfully"))
                .onErrorResume(e -> {
                    log.error("User creation failed: {}", e.getMessage());
                    return Mono.just(createErrorResponse(e.getMessage()));
                });
    }
    
    /**
     * Update user - Admin only
     */
    @PutMapping("/users/{userId}")
    public Mono<Map<String, Object>> updateUser(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserRequest request) {
        log.info("Update user request received for ID: {}", userId);
        
        return authService.updateUser(userId, request)
                .map(response -> createSuccessResponse(response, "User updated successfully"))
                .onErrorResume(e -> {
                    log.error("User update failed: {}", e.getMessage());
                    return Mono.just(createErrorResponse(e.getMessage()));
                });
    }
    
    /**
     * Delete user - Admin only
     */
    @DeleteMapping("/users/{userId}")
    public Mono<Map<String, Object>> deleteUser(@PathVariable Long userId) {
        log.info("Delete user request received for ID: {}", userId);
        
        return authService.deleteUser(userId)
                .then(Mono.just(createSuccessResponse(null, "User deleted successfully")))
                .onErrorResume(e -> {
                    log.error("User deletion failed: {}", e.getMessage());
                    return Mono.just(createErrorResponse(e.getMessage()));
                });
    }
    
    /**
     * Get user by ID - Admin only
     */
    @GetMapping("/users/{userId}")
    public Mono<Map<String, Object>> getUserById(@PathVariable Long userId) {
        return authService.getUserById(userId)
                .map(response -> createSuccessResponse(response, "User retrieved successfully"))
                .onErrorResume(e -> {
                    log.error("Get user failed: {}", e.getMessage());
                    return Mono.just(createErrorResponse(e.getMessage()));
                });
    }
    
    /**
     * Get all users - Admin only
     */
    @GetMapping("/users")
    public Mono<Map<String, Object>> getAllUsers() {
        return authService.getAllUsers()
                .collectList()
                .map(users -> createSuccessResponse(users, "Users retrieved successfully"))
                .onErrorResume(e -> {
                    log.error("Get all users failed: {}", e.getMessage());
                    return Mono.just(createErrorResponse(e.getMessage()));
                });
    }
    
    /**
     * Create success response
     */
    private Map<String, Object> createSuccessResponse(Object data, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", message);
        response.put("data", data);
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }
    
    /**
     * Create error response
     */
    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }
}
