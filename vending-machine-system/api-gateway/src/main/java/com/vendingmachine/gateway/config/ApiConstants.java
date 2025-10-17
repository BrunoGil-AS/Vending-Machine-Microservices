package com.vendingmachine.gateway.config;

import java.util.List;

/**
 * API Gateway Constants
 * Centralizes all path configurations for security and routing
 */
public class ApiConstants {

    // Public paths that don't require authentication
    public static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/login",
            "/actuator/health",
            "/api/inventory/products",
            "/api/inventory/availability/**",
            "/api/transaction/purchase",
            "/api/transactions/status/**",
            "/eureka"
    );

    // Admin paths that require ADMIN or SUPER_ADMIN role
    public static final List<String> ADMIN_PATHS = List.of(
            "/api/admin/**",
            "/actuator/**"
    );

    // Specific admin inventory paths
    public static final List<String> ADMIN_INVENTORY_PATHS = List.of(
            "/api/admin/inventory/**"
    );

    // Admin roles
    public static final List<String> ADMIN_ROLES = List.of("ADMIN", "SUPER_ADMIN");

    private ApiConstants() {
        // Utility class
    }
}