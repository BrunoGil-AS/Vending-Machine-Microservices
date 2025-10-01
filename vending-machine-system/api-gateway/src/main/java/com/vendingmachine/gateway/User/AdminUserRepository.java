package com.vendingmachine.gateway.User;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * Reactive Repository for AdminUser entity using R2DBC
 */
@Repository
public interface AdminUserRepository extends R2dbcRepository<AdminUser, Long> {
    
    /**
     * Find user by username
     */
    Mono<AdminUser> findByUsername(String username);
    
    /**
     * Check if username exists
     */
    @Query("SELECT COUNT(*) > 0 FROM admin_users WHERE username = :username")
    Mono<Boolean> existsByUsername(String username);
    
    /**
     * Find active user by username
     */
    @Query("SELECT * FROM admin_users WHERE username = :username AND active = true")
    Mono<AdminUser> findByUsernameAndActiveTrue(String username);
}