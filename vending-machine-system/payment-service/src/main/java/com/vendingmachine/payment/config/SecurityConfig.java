package com.vendingmachine.payment.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import static com.vendingmachine.payment.config.AccessConstants.*;

import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

/**
 * Security Configuration for Payment Service
 * Only allows access from API Gateway and authorized internal services
 * Implements header-based filtering to differentiate between client requests and inter-service communication
 */
@Configuration
@EnableWebSecurity
@Slf4j
public class SecurityConfig {

    @Value("${application.gateway.identifier:api-gateway}")
    private String GATEWAY_IDENTIFIER;

    @Value("${application.request.source.internal:internal}")
    private String REQUEST_SOURCE_INTERNAL;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/api/admin/**").access((authentication, request) -> {
                            String remoteAddr = request.getRequest().getRemoteAddr();
                            
                            // Allow inter-service communication (requests with X-Request-Source: internal)
                            String requestSource = request.getRequest().getHeader(REQUEST_SOURCE_HEADER);
                            if (REQUEST_SOURCE_INTERNAL.equals(requestSource) && LOCAL_IP_MAP.get(remoteAddr) != null) {
                                log.debug("Inter-service request allowed to admin endpoint from: {}", remoteAddr);
                                return new AuthorizationDecision(true);
                            }
                            
                            // Check for internal service header (from gateway)
                            String internalService = request.getRequest().getHeader(INTERNAL_SERVICE_HEADER);
                            if (!GATEWAY_IDENTIFIER.equals(internalService)) {
                                log.warn("Request to admin endpoint without valid internal service header");
                                return new AuthorizationDecision(false);
                            }
                            
                            // Check for admin role (for client requests through gateway)
                            String userRole = request.getRequest().getHeader("X-User-Role");
                            log.debug("Admin endpoint accessed with role: {}", userRole);
                            
                            if (ADMIN_ROLE.equals(userRole) || SUPER_ADMIN_ROLE.equals(userRole)) {
                                return new AuthorizationDecision(true);
                            }
                            
                            log.warn("Access denied - insufficient privileges. Role: {}", userRole);
                            return new AuthorizationDecision(false);
                        })
                        .anyRequest().access((authentication, request) -> {
                            String remoteAddr = request.getRequest().getRemoteAddr();
                            log.info("Request received from: {}", remoteAddr);
                            log.info("Request made to: {}", request.getRequest().getRequestURI());
                            
                            // Allow requests with internal service header (from gateway)
                            String internalService = request.getRequest().getHeader(INTERNAL_SERVICE_HEADER);
                            if (GATEWAY_IDENTIFIER.equals(internalService)) {
                                return new AuthorizationDecision(true);
                            }
                            
                            // Allow inter-service communication (requests with X-Request-Source: internal)
                            String requestSource = request.getRequest().getHeader(REQUEST_SOURCE_HEADER);
                            if (REQUEST_SOURCE_INTERNAL.equals(requestSource) && LOCAL_IP_MAP.get(remoteAddr) != null) {
                                log.debug("Inter-service request allowed from: {}", remoteAddr);
                                return new AuthorizationDecision(true);
                            }
                            
                            // Deny external access
                            log.warn("External access denied from: {}", remoteAddr);
                            return new AuthorizationDecision(false);
                        })
                )
                .exceptionHandling(ex -> ex
                        .accessDeniedPage("/access-denied")
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Arrays.asList("http://localhost:*", "http://127.0.0.1:*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}