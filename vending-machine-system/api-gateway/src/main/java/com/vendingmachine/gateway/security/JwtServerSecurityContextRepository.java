package com.vendingmachine.gateway.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Log
public class JwtServerSecurityContextRepository implements ServerSecurityContextRepository {
    
    private final AuthenticationManager authenticationManager;

    @Override
    public Mono<Void> save(ServerWebExchange exchange, SecurityContext context) {
        return Mono.empty();
    }

    @Override
    public Mono<SecurityContext> load(ServerWebExchange exchange) {
        String authHeader = exchange.getRequest()
            .getHeaders()
            .getFirst(HttpHeaders.AUTHORIZATION);

        log.info(String.format("\nAuthorization header: %s\n", authHeader));

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String authToken = authHeader.substring(7);
            log.info(String.format("\nExtracted token: %s\n", authToken));
            
            Authentication auth = new UsernamePasswordAuthenticationToken(authToken, authToken);
            return this.authenticationManager.authenticate(auth)
                .doOnNext(authentication -> log.info(String.format("\nAuthentication successful with authorities: %s\n", authentication.getAuthorities())))
                .doOnError(error -> log.warning(String.format("\nAuthentication failed: %s\n", error.getMessage())))
                .map(SecurityContextImpl::new);
        }
        
        log.info("\nNo valid Authorization header found\n");
        return Mono.empty();
    }
}