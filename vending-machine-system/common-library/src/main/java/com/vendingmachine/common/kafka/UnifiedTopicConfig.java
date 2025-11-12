package com.vendingmachine.common.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Unified Kafka Topic Configuration for Phase 2 Optimization
 * 
 * Consolidates all business events into a single partitioned topic for:
 * - Reduced operational complexity (5 topics → 1 topic)
 * - Better event ordering and correlation
 * - Simplified monitoring and debugging
 * - Improved performance through reduced broker overhead
 * 
 * Topic Strategy:
 * - Single topic: vending-machine-domain-events
 * - 3 partitions for load distribution
 * - Event routing by event type headers
 * - Unified DLQ for failed events
 */
@Configuration
public class UnifiedTopicConfig {

    public static final String UNIFIED_DOMAIN_EVENTS_TOPIC = "vending-machine-domain-events";
    public static final String UNIFIED_DLQ_TOPIC = "vending-machine-domain-events-dlq";
    
    // Event Type Headers for routing
    public static final String EVENT_TYPE_HEADER = "eventType";
    public static final String EVENT_SOURCE_HEADER = "eventSource";
    public static final String CORRELATION_ID_HEADER = "correlationId";
    
    // Event Types
    public static final String TRANSACTION_EVENT_TYPE = "TRANSACTION";
    public static final String PAYMENT_EVENT_TYPE = "PAYMENT";
    public static final String DISPENSING_EVENT_TYPE = "DISPENSING";
    public static final String INVENTORY_EVENT_TYPE = "INVENTORY";
    public static final String NOTIFICATION_EVENT_TYPE = "NOTIFICATION";
    
    // Service Sources
    public static final String TRANSACTION_SERVICE = "transaction-service";
    public static final String PAYMENT_SERVICE = "payment-service";
    public static final String DISPENSING_SERVICE = "dispensing-service";
    public static final String INVENTORY_SERVICE = "inventory-service";
    public static final String NOTIFICATION_SERVICE = "notification-service";

    /**
     * Main unified topic for all domain events
     * 3 partitions for load balancing while maintaining event ordering within partition
     */
    @Bean
    public NewTopic unifiedDomainEventsTopic() {
        return TopicBuilder.name(UNIFIED_DOMAIN_EVENTS_TOPIC)
                .partitions(3)  // Increased for better load distribution
                .replicas(1)    // Single replica for local development
                .config("cleanup.policy", "delete")
                .config("retention.ms", "604800000") // 7 days retention
                .config("segment.ms", "86400000")    // 24 hours per segment
                .build();
    }

    /**
     * Unified Dead Letter Queue for failed events
     * All failed events from any service go here for debugging
     */
    @Bean
    public NewTopic unifiedDlqTopic() {
        return TopicBuilder.name(UNIFIED_DLQ_TOPIC)
                .partitions(1)  // Single partition for DLQ to maintain error ordering
                .replicas(1)
                .config("cleanup.policy", "delete")
                .config("retention.ms", "2592000000") // 30 days retention for debugging
                .build();
    }
}