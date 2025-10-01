package com.vendingmachine.gateway.User.JWT;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * JWT Authentication Filter
 * Validates JWT tokens for protected routes
 */
@Component
@Slf4j
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {
    
    @Autowired
    private JwtUtil jwtUtil;
    
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/login",
            "/actuator",
            "/eureka"
    );
    
    private static final List<String> ADMIN_PATHS = List.of(
            "/api/admin"
    );
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        
        log.debug("Processing request: {} {}", request.getMethod(), path);
        
        // Skip authentication for public paths
        if (isPublicPath(path)) {
            log.debug("Public path accessed: {}", path);
            return chain.filter(exchange);
        }
        
        // Extract token from Authorization header
        String authHeader = request.getHeaders().getFirst("Authorization");
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Missing or invalid Authorization header for path: {}", path);
            return onError(exchange, "Missing or invalid Authorization header", HttpStatus.UNAUTHORIZED);
        }
        
        String token = authHeader.substring(7);
        
        // Validate token
        if (!jwtUtil.validateToken(token)) {
            log.warn("Invalid JWT token for path: {}", path);
            return onError(exchange, "Invalid or expired token", HttpStatus.UNAUTHORIZED);
        }
        
        // Extract user information
        String username = jwtUtil.getUsernameFromToken(token);
        String role = jwtUtil.getRoleFromToken(token);
        
        log.debug("Authenticated user: {} with role: {}", username, role);
        
        // Check admin access
        if (isAdminPath(path) && !isAdmin(role)) {
            log.warn("Access denied for user {} to admin path: {}", username, path);
            return onError(exchange, "Access denied. Admin privileges required", HttpStatus.FORBIDDEN);
        }
        
        // Add user context to request headers for downstream services
        ServerHttpRequest modifiedRequest = request.mutate()
                .header("X-User-Username", username)
                .header("X-User-Role", role)
                .build();
        
        log.debug("User context added to request headers");
        
        return chain.filter(exchange.mutate().request(modifiedRequest).build());
    }
    
    /**
     * Check if path is public (no authentication required)
     */
    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }
    
    /**
     * Check if path requires admin access
     */
    private boolean isAdminPath(String path) {
        return ADMIN_PATHS.stream().anyMatch(path::startsWith);
    }
    
    /**
     * Check if role has admin privileges
     */
    private boolean isAdmin(String role) {
        return "SUPER_ADMIN".equals(role) || "ADMIN".equals(role);
    }
    
    /**
     * Handle authentication errors
     */
    private Mono<Void> onError(ServerWebExchange exchange, String message, HttpStatus status) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        log.error("Authentication error: {}", message);
        return response.setComplete();
    }
    
    @Override
    public int getOrder() {
        return -1; // High priority
    }
}
