package com.vendingmachine.inventory.config;

import com.vendingmachine.inventory.kafka.KafkaProducerService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaTemplate;

import static org.mockito.Mockito.*;

/**
 * Test Kafka Configuration for Inventory Service
 * Provides a mock KafkaProducerService for tests
 */
@TestConfiguration
public class TestKafkaConfig {

    @Bean
    @Primary
    public KafkaProducerService mockKafkaProducerService() {
        KafkaProducerService mock = mock(KafkaProducerService.class);
        doNothing().when(mock).send(anyString(), any());
        return mock;
    }
    
    @Bean
    @Primary
    @SuppressWarnings("unchecked")
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return mock(KafkaTemplate.class);
    }
    
    @Bean
    @Primary
    public MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }
}