package com.vendingmachine.dispensing.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for RestTemplate used in inter-service communication.
 * Automatically adds internal service headers to all outgoing requests.
 */
@Configuration
public class RestTemplateConfig {

    @Value("${application.request.source.internal:internal}")
    private String REQUEST_SOURCE_INTERNAL;

    @Bean
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        
        // Add interceptor to include internal service header
        List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
        interceptors.add((request, body, execution) -> {
            request.getHeaders().add("X-Request-Source", REQUEST_SOURCE_INTERNAL);
            return execution.execute(request, body);
        });
        
        restTemplate.setInterceptors(interceptors);
        return restTemplate;
    }
}
 