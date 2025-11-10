package com.vendingmachine.inventory.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic stockUpdateEventsTopic() {
        return TopicBuilder.name("stock-update-events")
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic lowStockAlertsTopic() {
        return TopicBuilder.name("low-stock-alerts")
                .partitions(1)
                .replicas(1)
                .build();
    }

    // Dead Letter Queue Topics
    @Bean
    public NewTopic inventoryEventsDlqTopic() {
        return TopicBuilder.name("inventory-events-dlq")
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic transactionEventsDlqTopic() {
        return TopicBuilder.name("transaction-events-dlq")
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic paymentEventsDlqTopic() {
        return TopicBuilder.name("payment-events-dlq")
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic dispensingEventsDlqTopic() {
        return TopicBuilder.name("dispensing-events-dlq")
                .partitions(1)
                .replicas(1)
                .build();
    }
}
