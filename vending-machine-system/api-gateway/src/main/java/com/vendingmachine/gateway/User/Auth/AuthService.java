package com.vendingmachine.gateway.User.Auth;

import at.favre.lib.crypto.bcrypt.BCrypt;

import com.vendingmachine.gateway.User.AdminUser;
import com.vendingmachine.gateway.User.AdminUserRepository;
import com.vendingmachine.gateway.User.Login.DTO.*;
import com.vendingmachine.gateway.User.DTO.*;
import com.vendingmachine.gateway.User.JWT.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Reactive Authentication and User Management Service
 */
@Service
@RequiredArgsConstructor
@Log
public class AuthService {
    
    private final AdminUserRepository userRepository;
    private final JwtUtil jwtUtil;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    
    /**
     * Authenticate user and generate JWT token
     */
    public Mono<LoginResponse> login(LoginRequest request) {
        log.info(String.format("\nLogin attempt for user: %s \n", request.getUsername()));
        
        return userRepository.findByUsernameAndActiveTrue(request.getUsername())
                .switchIfEmpty(Mono.error(new RuntimeException("Invalid username or password")))
                .flatMap(user -> {
                    // Verify password
                    BCrypt.Result result = BCrypt.verifyer()
                            .verify(request.getPassword().toCharArray(), user.getPasswordHash());
                    
                    if (!result.verified) {
                        log.warning(String.format("\nFailed login attempt for user: %s \n", request.getUsername()));
                        return Mono.error(new RuntimeException("Invalid username or password"));
                    }
                    
                    // Generate JWT token
                    String token = jwtUtil.generateToken(user);

                    log.info(String.format("\nUser logged in successfully:\n %s\n", user.getUsername()));
                    log.info(String.format("\nGenerated JWT token for user:\n %s\n", user.getUsername()));
                    return Mono.just(LoginResponse.builder()
                            .token(token)
                            .username(user.getUsername())
                            .role(user.getRole())
                            .expiresIn(jwtUtil.getExpirationTime())
                            .build());
                });
    }
    
    /**
     * Create new admin user
     */
    public Mono<UserResponse> createUser(CreateUserRequest request) {
        log.info(String.format("\nCreating new user: %s \n", request.getUsername()));

        return userRepository.existsByUsername(request.getUsername())
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new RuntimeException("Username already exists"));
                    }
                    
                    // Validate role
                    try {
                        AdminUser.Role.valueOf(request.getRole());
                    } catch (IllegalArgumentException e) {
                        return Mono.error(new RuntimeException("Invalid role. Must be SUPER_ADMIN or ADMIN"));
                    }
                    
                    // Hash password
                    String passwordHash = BCrypt.withDefaults()
                            .hashToString(12, request.getPassword().toCharArray());
                    
                    AdminUser user = AdminUser.builder()
                            .username(request.getUsername())
                            .passwordHash(passwordHash)
                            .role(request.getRole())
                            .active(true)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
                    
                    return userRepository.save(user)
                            .doOnSuccess(u -> log.info(String.format("\nUser created successfully: %s \n", u.getUsername())))
                            .map(this::mapToUserResponse);
                });
    }
    
    /**
     * Update existing user
     */
    public Mono<UserResponse> updateUser(Long userId, UpdateUserRequest request) {
        log.info(String.format("\nUpdating user with ID: \n %d \n", userId));
        
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new RuntimeException("User not found")))
                .flatMap(user -> {
                    // Update password if provided
                    if (request.getPassword() != null && !request.getPassword().isEmpty()) {
                        String passwordHash = BCrypt.withDefaults()
                                .hashToString(12, request.getPassword().toCharArray());
                        user.setPasswordHash(passwordHash);
                    }
                    
                    // Update role if provided
                    if (request.getRole() != null) {
                        try {
                            AdminUser.Role.valueOf(request.getRole());
                            user.setRole(request.getRole());
                        } catch (IllegalArgumentException e) {
                            return Mono.error(new RuntimeException("Invalid role. Must be SUPER_ADMIN or ADMIN"));
                        }
                    }
                    
                    // Update active status if provided
                    if (request.getActive() != null) {
                        user.setActive(request.getActive());
                    }
                    
                    user.setUpdatedAt(LocalDateTime.now());
                    
                    return userRepository.save(user)
                            .doOnSuccess(u -> log.info(String.format("\nUser updated successfully: %s \n", u.getUsername())))
                            .map(this::mapToUserResponse);
                });
    }
    
    /**
     * Delete user
     */
    public Mono<Void> deleteUser(Long userId) {
        log.info(String.format("\nDeleting user with ID: %d", userId));
        
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new RuntimeException("User not found")))
                .flatMap(user -> userRepository.delete(user)
                        .doOnSuccess(v -> log.info(String.format("\nUser deleted successfully: %s \n", user.getUsername()))));
    }
    
    /**
     * Get user by ID
     */
    public Mono<UserResponse> getUserById(Long userId) {
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new RuntimeException("User not found")))
                .map(this::mapToUserResponse);
    }
    
    /**
     * Get all users
     */
    public Flux<UserResponse> getAllUsers() {
        return userRepository.findAll()
                .map(this::mapToUserResponse);
    }
    
    /**
     * Map AdminUser entity to UserResponse DTO
     */
    private UserResponse mapToUserResponse(AdminUser user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .active(user.getActive())
                .createdAt(user.getCreatedAt() != null ? user.getCreatedAt().format(FORMATTER) : null)
                .updatedAt(user.getUpdatedAt() != null ? user.getUpdatedAt().format(FORMATTER) : null)
                .build();
    }
}
