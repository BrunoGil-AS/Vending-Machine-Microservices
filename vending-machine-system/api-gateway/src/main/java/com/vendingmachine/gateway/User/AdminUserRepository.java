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
     * Check if username exists - Returns count as Long, converted to Boolean in service
     */
    @Query("SELECT COUNT(*) FROM admin_users WHERE username = :username")
    Mono<Long> countByUsername(String username);
    
    /**
     * Find active user by username
     */
    @Query("SELECT * FROM admin_users WHERE username = :username AND active = 1")
    Mono<AdminUser> findByUsernameAndActiveTrue(String username);
}