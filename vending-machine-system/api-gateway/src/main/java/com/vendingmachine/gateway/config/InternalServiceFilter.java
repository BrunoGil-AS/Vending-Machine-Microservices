package com.vendingmachine.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Gateway filter that adds internal service headers to requests going to microservices
 * This allows services to identify requests coming from the authorized gateway
 * and differentiate between client requests and inter-service communication
 */
@Component
public class InternalServiceFilter implements GlobalFilter, Ordered {

    private static final String INTERNAL_SERVICE_HEADER = "X-Internal-Service";
    private static final String REQUEST_SOURCE_HEADER = "X-Request-Source";
    private static final String GATEWAY_IDENTIFIER = "api-gateway";

    @Value("${application.request.source.gateway:gateway}")
    private String REQUEST_SOURCE_GATEWAY;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Add internal service header to identify requests from gateway
        // Add request source header to identify this as a client request (not inter-service)
        ServerWebExchange modifiedExchange = exchange.mutate()
                .request(exchange.getRequest().mutate()
                        .header(INTERNAL_SERVICE_HEADER, GATEWAY_IDENTIFIER)
                        .header(REQUEST_SOURCE_HEADER, REQUEST_SOURCE_GATEWAY)
                        .build())
                .build();

        return chain.filter(modifiedExchange);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE; // Execute after other filters
    }
}