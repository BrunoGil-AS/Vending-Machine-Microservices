package com.vendingmachine.inventory.config;

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
import static com.vendingmachine.inventory.config.AccessConstants.*;

import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

/**
 * Security Configuration for Inventory Service
 * Only allows access from API Gateway and authorized internal services
 */
@Configuration
@EnableWebSecurity
@Slf4j
public class SecurityConfig {

    @Value(value = "${application.gateway.identifier}")
    private String GATEWAY_IDENTIFIER;

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
                            // Check for internal service header (from gateway)
                            log.info("internal header {}:{}", INTERNAL_SERVICE_HEADER, GATEWAY_IDENTIFIER);
                            String internalService = request.getRequest().getHeader(INTERNAL_SERVICE_HEADER);
                            String remoteAddr = request.getRequest().getRemoteAddr();
                            if (!GATEWAY_IDENTIFIER.equals(internalService)) {
                                log.warn("Request to admin endpoint without valid internal service header");
                                return new AuthorizationDecision(false);
                            }
                            // Allow localhost for development and inter-service communication
                            // TODO: add a Client header type to filter request from clients.
                            if (LOCAL_IP_MAP.get(remoteAddr) != null) {
                                return new AuthorizationDecision(true);
                            }

                            // Check for admin role
                            String userRole = request.getRequest().getHeader("X-User-Role");
                            log.debug("Admin endpoint accessed with role: {}", userRole);

                            if (ADMIN_ROLE.equals(userRole) || SUPER_ADMIN_ROLE.equals(userRole)) {
                                return new AuthorizationDecision(true);
                            }

                            log.warn("Access denied - insufficient privileges. Role: {}", userRole);
                            return new AuthorizationDecision(false);
                        })
                        .anyRequest().access((authentication, request) -> {
                            // request received from:
                            String remoteAddr = request.getRequest().getRemoteAddr();
                            log.info("internal header {}:{}", INTERNAL_SERVICE_HEADER, GATEWAY_IDENTIFIER);
                            log.info("Request received from: {}", remoteAddr);
                            log.info("Request made to: {}", request.getRequest().getRequestURI());
                            // Allow requests with internal service header (from gateway)
                            String internalService = request.getRequest().getHeader(INTERNAL_SERVICE_HEADER);
                            if (GATEWAY_IDENTIFIER.equals(internalService)) {
                                return new AuthorizationDecision(true);
                            }
                            // Allow localhost for development and inter-service communication
                            // TODO: add a Client header type to filter request from clients.
                            if (LOCAL_IP_MAP.get(remoteAddr) != null) {
                                return new AuthorizationDecision(true);
                            }
                            // Deny external access
                            return new AuthorizationDecision(false);
                        }))
                .exceptionHandling(ex -> ex
                        .accessDeniedPage("/access-denied"));

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