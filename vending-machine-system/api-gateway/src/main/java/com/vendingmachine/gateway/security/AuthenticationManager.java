package com.vendingmachine.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AuthenticationManager implements org.springframework.security.authentication.ReactiveAuthenticationManager {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        String authToken = authentication.getCredentials().toString();
        
        try {
            System.out.println("Attempting to authenticate token: " + authToken.substring(0, Math.min(10, authToken.length())) + "...");
            System.out.println("Using secret key: " + jwtSecret.substring(0, Math.min(10, jwtSecret.length())) + "...");
            
            Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(authToken)
                .getPayload();
            
            String role = claims.get("role", String.class);
            System.out.println("Found role in token: " + role);
            
            List<SimpleGrantedAuthority> authorities = Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_" + role)
            );
            
            Authentication auth = new UsernamePasswordAuthenticationToken(
                claims.getSubject(),
                null,
                authorities
            );
            System.out.println("Created authentication with authorities: " + auth.getAuthorities());
            
            return Mono.just(auth);
        } catch (Exception e) {
            System.out.println("Authentication failed: " + e.getMessage());
            e.printStackTrace();
            return Mono.empty();
        }
    }
}