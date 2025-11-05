package com.vendingmachine.notification.config;

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

import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

/**
 * Security Configuration for Notification Service
 * Only allows access from API Gateway and authorized internal services
 * Implements role-based authorization for admin endpoints
 */
@Configuration
@EnableWebSecurity
@Slf4j
public class SecurityConfig {

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
                            String internalService = request.getRequest().getHeader("X-Internal-Service");
                            if (!"api-gateway".equals(internalService)) {
                                log.warn("Request to admin endpoint without valid internal service header");
                                return new AuthorizationDecision(false);
                            }
                            
                            // Check for admin role
                            String userRole = request.getRequest().getHeader("X-User-Role");
                            log.info("Admin endpoint accessed with role: {}", userRole);
                            
                            if ("ADMIN".equals(userRole) || "SUPER_ADMIN".equals(userRole)) {
                                return new AuthorizationDecision(true);
                            }
                            
                            log.warn("Access denied - insufficient privileges. Role: {}", userRole);
                            return new AuthorizationDecision(false);
                        })
                        .anyRequest().access((authentication, request) -> {
                            log.info("Request received from: {}", request.getRequest().getRemoteAddr());
                            log.info("Request made to: {}", request.getRequest().getRequestURI());
                            
                            // Allow requests with internal service header (from gateway)
                            String internalService = request.getRequest().getHeader("X-Internal-Service");
                            if ("api-gateway".equals(internalService)) {
                                return new AuthorizationDecision(true);
                            }
                            
                            // Allow localhost for development and inter-service communication
                            String remoteAddr = request.getRequest().getRemoteAddr();
                            if ("127.0.0.1".equals(remoteAddr) || "0:0:0:0:0:0:0:1".equals(remoteAddr) || "localhost".equals(remoteAddr)) {
                                return new AuthorizationDecision(true);
                            }
                            
                            // Deny external access
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
