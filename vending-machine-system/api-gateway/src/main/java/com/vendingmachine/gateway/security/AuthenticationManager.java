package com.vendingmachine.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.io.Decoders;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
@Log
public class AuthenticationManager implements ReactiveAuthenticationManager {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        String authToken = authentication.getCredentials().toString();
        
        try {

            // decode the secret key from Base64 first
            byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
            SecretKey key = Keys.hmacShaKeyFor(keyBytes);

            Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(authToken)
                .getPayload();
            
            String role = claims.get("role", String.class);
            log.info(String.format("\nToken valid. User: %s, Role: %s\n", claims.getSubject(), role));
            
            List<SimpleGrantedAuthority> authorities = Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_" + role)
            );
            
            Authentication auth = new UsernamePasswordAuthenticationToken(
                claims.getSubject(),
                null,
                authorities
            );
            log.info(String.format("\nCreated authentication for user:  %s\n", claims.getSubject()));
            
            return Mono.just(auth);
        } catch (Exception e) {
            log.warning(String.format("\nAuthentication failed: %s\n", e.getMessage()));
            e.printStackTrace();
            return Mono.empty();
        }
    }
}