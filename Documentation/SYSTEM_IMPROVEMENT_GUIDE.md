# System Improvement Guide: Fault Tolerance, Kafka Optimization & Refund Strategies

## Table of Contents

1. [Fault Tolerance Enhancement](#1-fault-tolerance-enhancement)
2. [Kafka Layer Optimization](#2-kafka-layer-optimization)
3. [Compensatory Actions & Refund Implementation](#3-compensatory-actions--refund-implementation)
4. [Implementation Roadmap](#4-implementation-roadmap---revised-order)

---

## 1. Fault Tolerance Enhancement

### Current State Analysis

Your system has basic fault tolerance mechanisms but lacks comprehensive resilience patterns:

- ✅ Health checks via Spring Actuator
- ✅ Basic event deduplication with `ProcessedEvent`
- ✅ Simple compensation logic in `TransactionService`
- ❌ No circuit breaker implementation
- ❌ Limited retry mechanisms
- ❌ No fallback strategies for service failures
- ❌ Missing distributed transaction coordination

### 1.1 Circuit Breaker Pattern

**Strategy**: Implement Resilience4j circuit breakers for all synchronous service-to-service calls.

#### Implementation Steps

**Step 1: Add Dependencies:**

```xml
<!-- Add to parent pom.xml -->
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
    <version>2.1.0</version>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

**Step 2: Configure Circuit Breaker:**

```properties
# application.properties for transaction-service
resilience4j.circuitbreaker.instances.inventory-service.registerHealthIndicator=true
resilience4j.circuitbreaker.instances.inventory-service.slidingWindowSize=10
resilience4j.circuitbreaker.instances.inventory-service.minimumNumberOfCalls=5
resilience4j.circuitbreaker.instances.inventory-service.permittedNumberOfCallsInHalfOpenState=3
resilience4j.circuitbreaker.instances.inventory-service.automaticTransitionFromOpenToHalfOpenEnabled=true
resilience4j.circuitbreaker.instances.inventory-service.waitDurationInOpenState=10s
resilience4j.circuitbreaker.instances.inventory-service.failureRateThreshold=50
resilience4j.circuitbreaker.instances.inventory-service.slowCallRateThreshold=100
resilience4j.circuitbreaker.instances.inventory-service.slowCallDurationThreshold=2s

# Similar configuration for payment-service and dispensing-service
resilience4j.circuitbreaker.instances.payment-service.registerHealthIndicator=true
resilience4j.circuitbreaker.instances.payment-service.slidingWindowSize=10
resilience4j.circuitbreaker.instances.payment-service.waitDurationInOpenState=15s
resilience4j.circuitbreaker.instances.payment-service.failureRateThreshold=50

resilience4j.circuitbreaker.instances.dispensing-service.registerHealthIndicator=true
resilience4j.circuitbreaker.instances.dispensing-service.slidingWindowSize=10
resilience4j.circuitbreaker.instances.dispensing-service.waitDurationInOpenState=10s
resilience4j.circuitbreaker.instances.dispensing-service.failureRateThreshold=50
```

**Step 3: Apply to Service Calls:**

```java
// transaction-service/src/main/java/com/vendingmachine/transaction/client/InventoryServiceClient.java
package com.vendingmachine.transaction.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryServiceClient {

    private final RestTemplate restTemplate;

    @CircuitBreaker(name = "inventory-service", fallbackMethod = "checkAvailabilityFallback")
    @Retry(name = "inventory-service")
    public boolean checkAvailability(Long productId, Integer quantity) {
        String url = "http://inventory-service/api/inventory/check";
        // Implementation
        return true;
    }

    // Fallback method
    private boolean checkAvailabilityFallback(Long productId, Integer quantity, Exception e) {
        log.error("Circuit breaker activated for inventory check. Product: {}, Error: {}",
                  productId, e.getMessage());

        // Option 1: Return false to prevent transaction
        // return false;

        // Option 2: Check local cache (implement cache strategy)
        return checkLocalCache(productId, quantity);
    }

    private boolean checkLocalCache(Long productId, Integer quantity) {
        // Implement caching strategy with Redis or Caffeine
        log.warn("Using fallback cache for product availability check");
        return false; // Conservative approach: deny if service unavailable
    }
}
```

### 1.2 Retry Mechanism with Backoff

**Strategy**: Implement intelligent retry with exponential backoff for transient failures.

```properties
# Retry configuration
resilience4j.retry.instances.inventory-service.maxAttempts=3
resilience4j.retry.instances.inventory-service.waitDuration=500ms
resilience4j.retry.instances.inventory-service.enableExponentialBackoff=true
resilience4j.retry.instances.inventory-service.exponentialBackoffMultiplier=2
resilience4j.retry.instances.inventory-service.retryExceptions=java.net.ConnectException,org.springframework.web.client.ResourceAccessException

resilience4j.retry.instances.payment-service.maxAttempts=3
resilience4j.retry.instances.payment-service.waitDuration=1000ms
resilience4j.retry.instances.payment-service.enableExponentialBackoff=true
resilience4j.retry.instances.payment-service.exponentialBackoffMultiplier=2

resilience4j.retry.instances.dispensing-service.maxAttempts=2
resilience4j.retry.instances.dispensing-service.waitDuration=1000ms
```

### 1.3 Bulkhead Pattern for Resource Isolation

**Strategy**: Prevent cascading failures by isolating thread pools for different services.

```properties
# Bulkhead configuration
resilience4j.bulkhead.instances.inventory-service.maxConcurrentCalls=10
resilience4j.bulkhead.instances.inventory-service.maxWaitDuration=500ms

resilience4j.bulkhead.instances.payment-service.maxConcurrentCalls=20
resilience4j.bulkhead.instances.payment-service.maxWaitDuration=1000ms

resilience4j.bulkhead.instances.dispensing-service.maxConcurrentCalls=5
resilience4j.bulkhead.instances.dispensing-service.maxWaitDuration=2000ms
```

```java
// Apply bulkhead to critical operations
@Bulkhead(name = "payment-service", fallbackMethod = "processPaymentFallback")
@CircuitBreaker(name = "payment-service", fallbackMethod = "processPaymentFallback")
@Retry(name = "payment-service")
public PaymentResponse processPayment(PaymentRequest request) {
    // Payment processing logic
}
```

### 1.4 Database Resilience

**Strategy**: Implement connection pooling optimization and read replicas for high availability.

```properties
# HikariCP configuration for all services
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=30000
spring.datasource.hikari.idle-timeout=600000
spring.datasource.hikari.max-lifetime=1800000
spring.datasource.hikari.leak-detection-threshold=60000

# Connection validation
spring.datasource.hikari.connection-test-query=SELECT 1
spring.datasource.hikari.validation-timeout=5000
```

**Advanced Strategy**: Read/Write Splitting

```java
// Configuration for read replicas (future enhancement)
@Configuration
public class DataSourceRoutingConfiguration {

    @Bean
    @Primary
    public DataSource routingDataSource(
            @Qualifier("writeDataSource") DataSource writeDataSource,
            @Qualifier("readDataSource") DataSource readDataSource) {

        ReplicationRoutingDataSource routingDataSource = new ReplicationRoutingDataSource();

        Map<Object, Object> dataSourceMap = new HashMap<>();
        dataSourceMap.put(DataSourceType.WRITE, writeDataSource);
        dataSourceMap.put(DataSourceType.READ, readDataSource);

        routingDataSource.setTargetDataSources(dataSourceMap);
        routingDataSource.setDefaultTargetDataSource(writeDataSource);

        return routingDataSource;
    }
}
```

### 1.5 Kafka Consumer Resilience

**Strategy**: Implement robust error handling and dead letter queues (DLQ).

```java
// kafka/KafkaErrorHandler.java
package com.vendingmachine.transaction.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ErrorHandler;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class KafkaErrorHandler implements ErrorHandler {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public KafkaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void handle(Exception thrownException, ConsumerRecord<?, ?> record) {
        log.error("Error processing Kafka record: topic={}, partition={}, offset={}, key={}",
                  record.topic(), record.partition(), record.offset(), record.key(), thrownException);

        // Send to DLQ after max retries
        sendToDeadLetterQueue(record, thrownException);
    }

    private void sendToDeadLetterQueue(ConsumerRecord<?, ?> record, Exception exception) {
        String dlqTopic = record.topic() + ".DLQ";

        try {
            kafkaTemplate.send(dlqTopic, record.key().toString(), record.value());
            log.info("Sent failed record to DLQ: {}", dlqTopic);
        } catch (Exception e) {
            log.error("Failed to send record to DLQ: {}", dlqTopic, e);
            // Persist to database as last resort
            persistFailedEvent(record, exception);
        }
    }

    private void persistFailedEvent(ConsumerRecord<?, ?> record, Exception exception) {
        // Save to failed_events table for manual processing
        log.error("Persisting failed event to database for manual intervention");
    }
}
```

**Consumer Configuration with Retry:**

```java
// KafkaConsumerConfig.java enhancement
@Bean
public ConcurrentKafkaListenerContainerFactory<String, PaymentEvent> paymentEventKafkaListenerContainerFactory() {
    ConcurrentKafkaListenerContainerFactory<String, PaymentEvent> factory =
        new ConcurrentKafkaListenerContainerFactory<>();

    factory.setConsumerFactory(paymentEventConsumerFactory());
    factory.setConcurrency(3); // 3 consumer threads

    // Enable batch processing with error handling
    factory.setBatchListener(false);

    // Retry configuration
    factory.setCommonErrorHandler(new DefaultErrorHandler(
        new FixedBackOff(1000L, 3L) // 3 retries with 1 second delay
    ));

    // Manual acknowledgment mode for better control
    factory.getContainerProperties().setAckMode(AckMode.MANUAL);

    return factory;
}
```

### 1.6 Service Health Monitoring

**Strategy**: Implement comprehensive health checks and alerting.

```java
// health/CustomHealthIndicator.java
package com.vendingmachine.transaction.health;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DatabaseHealthIndicator implements HealthIndicator {

    private final TransactionRepository transactionRepository;

    @Override
    public Health health() {
        try {
            // Check database connectivity
            long pendingTransactions = transactionRepository.countByStatus(TransactionStatus.PROCESSING);

            if (pendingTransactions > 100) {
                return Health.down()
                    .withDetail("pendingTransactions", pendingTransactions)
                    .withDetail("reason", "Too many pending transactions")
                    .build();
            }

            return Health.up()
                .withDetail("pendingTransactions", pendingTransactions)
                .build();

        } catch (Exception e) {
            return Health.down()
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
```

---

## 2. Kafka Layer Optimization

### **Current State Analysis**

Your current Kafka architecture has excessive event layers:

- 5 different Kafka topics for inter-service communication
- Multiple event transformations between services
- Potential for message duplication and complexity

### 2.1 Event Consolidation Strategy

**Problem**: Too many specialized topics create operational overhead and complexity.

**Current Architecture**:

```plaintext
transaction-events → Transaction lifecycle
payment-events → Payment processing
inventory-events → Stock updates
dispensing-events → Hardware operations
notification-events → System alerts
```

**Proposed Optimized Architecture**:

#### Option A: Domain-Centric Topics (Recommended)

```plaintext
vending-machine-domain-events → All business domain events
vending-machine-notification-events → Cross-cutting notifications
vending-machine-dlq → Dead letter queue
```

**Benefits**:

- Single topic for entire transaction flow
- Event ordering guaranteed per partition key
- Easier monitoring and troubleshooting
- Reduced Kafka broker overhead

#### Option B: Transaction-Centric with Event Types

```plaintext
vending-machine-transactions → Transaction-related events
vending-machine-inventory → Inventory-related events
vending-machine-notifications → Notification events
```

**Implementation**:

```java
// common-library/src/main/java/com/vendingmachine/common/event/DomainEvent.java
package com.vendingmachine.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DomainEvent {
    private String eventId;
    private String eventType; // TRANSACTION_CREATED, PAYMENT_COMPLETED, ITEM_DISPENSED, etc.
    private String aggregateId; // transactionId
    private String aggregateType; // TRANSACTION, PAYMENT, INVENTORY
    private Long timestamp;
    private String payload; // JSON string of specific event data
    private Map<String, String> metadata;

    // Factory methods for different event types
    public static DomainEvent transactionCreated(Long transactionId, String payload) {
        return DomainEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .eventType("TRANSACTION_CREATED")
            .aggregateId(transactionId.toString())
            .aggregateType("TRANSACTION")
            .timestamp(System.currentTimeMillis())
            .payload(payload)
            .build();
    }

    public static DomainEvent paymentCompleted(Long transactionId, String payload) {
        return DomainEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .eventType("PAYMENT_COMPLETED")
            .aggregateId(transactionId.toString())
            .aggregateType("PAYMENT")
            .timestamp(System.currentTimeMillis())
            .payload(payload)
            .build();
    }

    // Add more factory methods as needed
}
```

**Unified Topic Configuration**:

```java
// config/KafkaTopicConfig.java
package com.vendingmachine.transaction.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic domainEventsTopic() {
        return TopicBuilder.name("vending-machine-domain-events")
                .partitions(3) // Partition by transaction ID for ordering
                .replicas(1) // Increase for production
                .config("retention.ms", "604800000") // 7 days retention
                .config("max.message.bytes", "1048576") // 1MB max message
                .build();
    }

    @Bean
    public NewTopic deadLetterQueueTopic() {
        return TopicBuilder.name("vending-machine-dlq")
                .partitions(1)
                .replicas(1)
                .config("retention.ms", "2592000000") // 30 days retention
                .build();
    }
}
```

**Unified Consumer with Event Type Filtering**:

```java
// kafka/DomainEventConsumer.java
package com.vendingmachine.transaction.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vendingmachine.common.event.DomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DomainEventConsumer {

    private final ObjectMapper objectMapper;
    private final PaymentEventHandler paymentEventHandler;
    private final DispensingEventHandler dispensingEventHandler;

    @KafkaListener(
        topics = "vending-machine-domain-events",
        groupId = "transaction-service-group",
        containerFactory = "domainEventKafkaListenerContainerFactory"
    )
    public void consumeDomainEvent(DomainEvent event) {
        log.info("Received domain event: type={}, id={}", event.getEventType(), event.getEventId());

        try {
            // Route based on event type
            switch (event.getEventType()) {
                case "PAYMENT_COMPLETED":
                case "PAYMENT_FAILED":
                    paymentEventHandler.handle(event);
                    break;

                case "ITEM_DISPENSED":
                case "DISPENSING_FAILED":
                    dispensingEventHandler.handle(event);
                    break;

                case "STOCK_UPDATED":
                    // Handle if needed by transaction service
                    break;

                default:
                    log.debug("Event type {} not handled by transaction service", event.getEventType());
            }

        } catch (Exception e) {
            log.error("Failed to process domain event: {}", event.getEventId(), e);
            throw e; // Let error handler manage retry/DLQ
        }
    }
}
```

### 2.2 Direct Service Communication for Real-Time Operations

**Strategy**: Use synchronous HTTP calls for critical real-time operations instead of Kafka.

**Current Flow (Too Many Kafka Hops)**:

```plaintext
Transaction Service → Kafka → Payment Service → Kafka → Transaction Service → Kafka → Dispensing Service
```

**Optimized Flow**:

```plaintext
Transaction Service → HTTP → Payment Service (sync)
                   → HTTP → Dispensing Service (sync)
                   → Kafka → Notification Service (async)
                   → Kafka → Inventory Service (async for stock updates)
```

**Decision Matrix**:

| Operation                    | Communication Type | Reason                              |
| ---------------------------- | ------------------ | ----------------------------------- |
| Check inventory availability | **Sync HTTP**      | Need immediate response             |
| Process payment              | **Sync HTTP**      | Transaction consistency required    |
| Dispense item                | **Sync HTTP**      | Immediate feedback needed           |
| Update stock levels          | **Async Kafka**    | Eventually consistent is acceptable |
| Send notifications           | **Async Kafka**    | Non-blocking, fire-and-forget       |
| Generate analytics           | **Async Kafka**    | Background processing               |

**Implementation**:

```java
// transaction/TransactionOrchestrator.java
package com.vendingmachine.transaction.orchestrator;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionOrchestrator {

    private final InventoryClient inventoryClient;
    private final PaymentClient paymentClient;
    private final DispensingClient dispensingClient;
    private final KafkaEventPublisher eventPublisher;
    private final TransactionRepository transactionRepository;

    @Transactional
    public TransactionResult executeTransaction(TransactionRequest request) {
        Transaction transaction = createTransaction(request);

        try {
            // Step 1: Check inventory (Synchronous)
            boolean available = checkInventorySync(request.getProductId(), request.getQuantity());
            if (!available) {
                transaction.setStatus(TransactionStatus.CANCELLED);
                transactionRepository.save(transaction);
                return TransactionResult.failure("Product unavailable");
            }

            // Step 2: Process payment (Synchronous)
            PaymentResponse paymentResponse = processPaymentSync(transaction);
            if (!paymentResponse.isSuccess()) {
                transaction.setStatus(TransactionStatus.CANCELLED);
                transactionRepository.save(transaction);
                return TransactionResult.failure("Payment failed");
            }

            transaction.setStatus(TransactionStatus.PROCESSING);
            transactionRepository.save(transaction);

            // Step 3: Dispense item (Synchronous)
            DispensingResponse dispensingResponse = dispenseItemSync(transaction);
            if (!dispensingResponse.isSuccess()) {
                // Compensate: refund payment
                compensatePayment(transaction);
                transaction.setStatus(TransactionStatus.FAILED);
                transactionRepository.save(transaction);
                return TransactionResult.failure("Dispensing failed, payment refunded");
            }

            // Success!
            transaction.setStatus(TransactionStatus.COMPLETED);
            transactionRepository.save(transaction);

            // Step 4: Publish async events for non-critical operations
            publishAsyncEvents(transaction);

            return TransactionResult.success(transaction);

        } catch (Exception e) {
            log.error("Transaction failed: {}", transaction.getId(), e);
            handleTransactionFailure(transaction);
            return TransactionResult.failure("Transaction failed: " + e.getMessage());
        }
    }

    @CircuitBreaker(name = "inventory-service", fallbackMethod = "inventoryFallback")
    private boolean checkInventorySync(Long productId, Integer quantity) {
        return inventoryClient.checkAvailability(productId, quantity);
    }

    @CircuitBreaker(name = "payment-service", fallbackMethod = "paymentFallback")
    private PaymentResponse processPaymentSync(Transaction transaction) {
        return paymentClient.processPayment(PaymentRequest.from(transaction));
    }

    @CircuitBreaker(name = "dispensing-service", fallbackMethod = "dispensingFallback")
    private DispensingResponse dispenseItemSync(Transaction transaction) {
        return dispensingClient.dispenseItem(DispensingRequest.from(transaction));
    }

    private void publishAsyncEvents(Transaction transaction) {
        // Publish to unified domain events topic
        eventPublisher.publishTransactionCompleted(transaction);
        eventPublisher.publishInventoryUpdate(transaction);
        eventPublisher.publishNotification(transaction);
    }
}
```

### 2.3 Event Deduplication Optimization

**Strategy**: Improve deduplication mechanism with distributed caching.

```java
// kafka/EventDeduplicationService.java
package com.vendingmachine.transaction.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventDeduplicationService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ProcessedEventRepository processedEventRepository;

    private static final String EVENT_CACHE_PREFIX = "processed:event:";
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    public boolean isEventProcessed(String eventId, String eventType) {
        String cacheKey = EVENT_CACHE_PREFIX + eventType + ":" + eventId;

        // Check Redis cache first (fast)
        Boolean cached = redisTemplate.hasKey(cacheKey);
        if (Boolean.TRUE.equals(cached)) {
            log.debug("Event {} already processed (found in cache)", eventId);
            return true;
        }

        // Check database (slower fallback)
        boolean processed = processedEventRepository.existsByEventIdAndEventType(eventId, eventType);

        if (processed) {
            // Update cache for future checks
            redisTemplate.opsForValue().set(cacheKey, "1", CACHE_TTL);
        }

        return processed;
    }

    public void markEventAsProcessed(String eventId, String eventType, Long transactionId) {
        String cacheKey = EVENT_CACHE_PREFIX + eventType + ":" + eventId;

        // Save to database
        ProcessedEvent processedEvent = ProcessedEvent.builder()
            .eventId(eventId)
            .eventType(eventType)
            .transactionId(transactionId)
            .processedAt(LocalDateTime.now())
            .build();
        processedEventRepository.save(processedEvent);

        // Update cache
        redisTemplate.opsForValue().set(cacheKey, "1", CACHE_TTL);

        log.debug("Marked event {} as processed", eventId);
    }
}
```

---

## 3. Compensatory Actions & Refund Implementation

### _Current State Analysis_

Your current refund mechanism is basic:

- Simple REST call to payment service
- No tracking of refund state
- No partial refund support
- Missing idempotency guarantees
- No audit trail for refunds

### 3.1 Saga Pattern for Distributed Transactions

**Strategy**: Implement Choreography-based Saga for complex transaction flows with compensation.

```java
// saga/TransactionSaga.java
package com.vendingmachine.transaction.saga;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class TransactionSaga {
    private Long transactionId;
    private SagaStatus status;
    private List<SagaStep> completedSteps;
    private List<SagaStep> compensatedSteps;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    public enum SagaStatus {
        STARTED,
        IN_PROGRESS,
        COMPLETED,
        COMPENSATING,
        COMPENSATED,
        FAILED
    }

    public static class SagaStep {
        private String stepName;
        private String serviceInvolved;
        private LocalDateTime executedAt;
        private String compensationAction;
        private boolean compensated;
    }
}
```

**Saga Orchestrator**:

```java
// saga/SagaOrchestrator.java
package com.vendingmachine.transaction.saga;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SagaOrchestrator {

    private final SagaRepository sagaRepository;
    private final InventoryCompensator inventoryCompensator;
    private final PaymentCompensator paymentCompensator;
    private final DispensingCompensator dispensingCompensator;

    @Transactional
    public void executeTransactionSaga(Long transactionId) {
        TransactionSaga saga = TransactionSaga.builder()
            .transactionId(transactionId)
            .status(TransactionSaga.SagaStatus.STARTED)
            .completedSteps(new ArrayList<>())
            .compensatedSteps(new ArrayList<>())
            .startedAt(LocalDateTime.now())
            .build();

        try {
            // Execute saga steps
            executeInventoryReservation(saga);
            executePaymentProcessing(saga);
            executeItemDispensing(saga);

            // Mark saga as completed
            saga.setStatus(TransactionSaga.SagaStatus.COMPLETED);
            saga.setCompletedAt(LocalDateTime.now());
            sagaRepository.save(saga);

        } catch (SagaException e) {
            log.error("Saga failed for transaction {}, starting compensation", transactionId, e);
            compensateSaga(saga);
        }
    }

    @Transactional
    public void compensateSaga(TransactionSaga saga) {
        saga.setStatus(TransactionSaga.SagaStatus.COMPENSATING);
        sagaRepository.save(saga);

        // Compensate in reverse order
        List<TransactionSaga.SagaStep> steps = new ArrayList<>(saga.getCompletedSteps());
        Collections.reverse(steps);

        for (TransactionSaga.SagaStep step : steps) {
            try {
                compensateStep(step);
                step.setCompensated(true);
                saga.getCompensatedSteps().add(step);

            } catch (Exception e) {
                log.error("Failed to compensate step: {}", step.getStepName(), e);
                // Continue compensating other steps
            }
        }

        saga.setStatus(TransactionSaga.SagaStatus.COMPENSATED);
        saga.setCompletedAt(LocalDateTime.now());
        sagaRepository.save(saga);
    }

    private void compensateStep(TransactionSaga.SagaStep step) {
        switch (step.getStepName()) {
            case "INVENTORY_RESERVATION":
                inventoryCompensator.releaseReservation(step);
                break;
            case "PAYMENT_PROCESSING":
                paymentCompensator.refundPayment(step);
                break;
            case "ITEM_DISPENSING":
                dispensingCompensator.cancelDispensing(step);
                break;
            default:
                log.warn("Unknown step type for compensation: {}", step.getStepName());
        }
    }
}
```

### 3.2 Comprehensive Refund System

**Strategy**: Create a dedicated refund entity with state tracking and audit trail.

```java
// payment/refund/Refund.java
package com.vendingmachine.payment.refund;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "refunds")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String refundId; // UUID for idempotency

    @Column(nullable = false)
    private Long transactionId;

    @Column(nullable = false)
    private Long originalPaymentId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal refundAmount;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal originalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RefundType refundType; // FULL, PARTIAL

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RefundStatus status; // PENDING, PROCESSING, COMPLETED, FAILED

    @Enumerated(EnumType.STRING)
    private RefundReason reason; // DISPENSING_FAILED, CUSTOMER_REQUEST, TIMEOUT, SYSTEM_ERROR

    private String failureReason;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime processedAt;

    private LocalDateTime completedAt;

    private String initiatedBy; // USER_ID or SYSTEM

    @Column(length = 1000)
    private String notes;

    // Idempotency tracking
    private Integer retryCount;

    private LocalDateTime lastRetryAt;

    public enum RefundType {
        FULL,
        PARTIAL
    }

    public enum RefundStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    public enum RefundReason {
        DISPENSING_FAILED,
        PAYMENT_ERROR,
        CUSTOMER_REQUEST,
        TIMEOUT,
        SYSTEM_ERROR,
        FRAUD_DETECTION,
        DUPLICATE_CHARGE
    }
}
```

**Refund Service with Idempotency**:

```java
// payment/refund/RefundService.java
package com.vendingmachine.payment.refund;

import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefundService {

    private final RefundRepository refundRepository;
    private final PaymentTransactionRepository paymentRepository;
    private final PaymentGatewayClient paymentGatewayClient;
    private final KafkaEventPublisher eventPublisher;

    /**
     * Process refund with idempotency guarantee
     * @param refundId Unique refund identifier for idempotency
     * @param transactionId Original transaction ID
     * @param refundAmount Amount to refund
     * @param reason Reason for refund
     * @return Refund entity
     */
    @Transactional
    public Refund processRefund(String refundId, Long transactionId, BigDecimal refundAmount,
                                Refund.RefundReason reason, String initiatedBy) {

        log.info("Processing refund: refundId={}, transactionId={}, amount={}",
                 refundId, transactionId, refundAmount);

        // Check for existing refund (idempotency)
        Optional<Refund> existingRefund = refundRepository.findByRefundId(refundId);
        if (existingRefund.isPresent()) {
            log.info("Refund {} already exists with status {}", refundId, existingRefund.get().getStatus());
            return existingRefund.get();
        }

        // Validate original payment
        PaymentTransaction originalPayment = paymentRepository.findByTransactionId(transactionId)
            .orElseThrow(() -> new RefundException("Original payment not found for transaction: " + transactionId));

        if (originalPayment.getStatus() != PaymentStatus.COMPLETED) {
            throw new RefundException("Cannot refund payment that is not completed. Status: " + originalPayment.getStatus());
        }

        // Check refund amount validity
        BigDecimal totalRefunded = refundRepository.sumCompletedRefundsByOriginalPaymentId(originalPayment.getId());
        BigDecimal remainingAmount = originalPayment.getAmount().subtract(totalRefunded);

        if (refundAmount.compareTo(remainingAmount) > 0) {
            throw new RefundException(String.format(
                "Refund amount %.2f exceeds remaining amount %.2f", refundAmount, remainingAmount));
        }

        // Create refund record
        Refund refund = Refund.builder()
            .refundId(refundId)
            .transactionId(transactionId)
            .originalPaymentId(originalPayment.getId())
            .refundAmount(refundAmount)
            .originalAmount(originalPayment.getAmount())
            .refundType(refundAmount.compareTo(originalPayment.getAmount()) == 0
                ? Refund.RefundType.FULL : Refund.RefundType.PARTIAL)
            .status(Refund.RefundStatus.PENDING)
            .reason(reason)
            .createdAt(LocalDateTime.now())
            .initiatedBy(initiatedBy)
            .retryCount(0)
            .build();

        refund = refundRepository.save(refund);

        // Execute refund with retry
        return executeRefund(refund);
    }

    @Retry(name = "payment-refund", fallbackMethod = "refundFallback")
    @Transactional
    public Refund executeRefund(Refund refund) {
        try {
            refund.setStatus(Refund.RefundStatus.PROCESSING);
            refund.setProcessedAt(LocalDateTime.now());
            refund.setRetryCount(refund.getRetryCount() + 1);
            refund.setLastRetryAt(LocalDateTime.now());
            refundRepository.save(refund);

            // Call payment gateway for actual refund
            PaymentGatewayResponse response = paymentGatewayClient.processRefund(
                refund.getOriginalPaymentId(),
                refund.getRefundAmount()
            );

            if (response.isSuccess()) {
                refund.setStatus(Refund.RefundStatus.COMPLETED);
                refund.setCompletedAt(LocalDateTime.now());
                refundRepository.save(refund);

                log.info("Refund completed successfully: {}", refund.getRefundId());

                // Publish refund completed event
                eventPublisher.publishRefundCompleted(refund);

                return refund;

            } else {
                refund.setStatus(Refund.RefundStatus.FAILED);
                refund.setFailureReason(response.getErrorMessage());
                refundRepository.save(refund);

                log.error("Refund failed: {} - {}", refund.getRefundId(), response.getErrorMessage());

                // Publish refund failed event
                eventPublisher.publishRefundFailed(refund);

                throw new RefundException("Payment gateway declined refund: " + response.getErrorMessage());
            }

        } catch (Exception e) {
            log.error("Error executing refund: {}", refund.getRefundId(), e);
            refund.setStatus(Refund.RefundStatus.FAILED);
            refund.setFailureReason(e.getMessage());
            refundRepository.save(refund);
            throw e;
        }
    }

    // Fallback method after all retries exhausted
    private Refund refundFallback(Refund refund, Exception e) {
        log.error("All refund retry attempts exhausted for: {}", refund.getRefundId(), e);

        refund.setStatus(Refund.RefundStatus.FAILED);
        refund.setFailureReason("Max retries exhausted: " + e.getMessage());
        refund = refundRepository.save(refund);

        // Trigger manual review alert
        eventPublisher.publishRefundRequiresManualReview(refund);

        return refund;
    }

    /**
     * Helper method to initiate full refund for a transaction
     */
    public Refund refundTransaction(Long transactionId, Refund.RefundReason reason, String initiatedBy) {
        PaymentTransaction payment = paymentRepository.findByTransactionId(transactionId)
            .orElseThrow(() -> new RefundException("Payment not found for transaction: " + transactionId));

        String refundId = UUID.randomUUID().toString();
        return processRefund(refundId, transactionId, payment.getAmount(), reason, initiatedBy);
    }
}
```

**Refund Repository with Analytics**:

```java
// payment/refund/RefundRepository.java
package com.vendingmachine.payment.refund;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RefundRepository extends JpaRepository<Refund, Long> {

    Optional<Refund> findByRefundId(String refundId);

    List<Refund> findByTransactionId(Long transactionId);

    List<Refund> findByOriginalPaymentId(Long paymentId);

    List<Refund> findByStatus(Refund.RefundStatus status);

    @Query("SELECT COALESCE(SUM(r.refundAmount), 0) FROM Refund r " +
           "WHERE r.originalPaymentId = :paymentId AND r.status = 'COMPLETED'")
    BigDecimal sumCompletedRefundsByOriginalPaymentId(Long paymentId);

    @Query("SELECT r FROM Refund r WHERE r.status = 'FAILED' AND r.retryCount < 3 " +
           "AND r.lastRetryAt < :retryAfter")
    List<Refund> findFailedRefundsForRetry(LocalDateTime retryAfter);

    @Query("SELECT r FROM Refund r WHERE r.createdAt BETWEEN :startDate AND :endDate")
    List<Refund> findRefundsByDateRange(LocalDateTime startDate, LocalDateTime endDate);

    @Query("SELECT COUNT(r) FROM Refund r WHERE r.reason = :reason AND r.status = 'COMPLETED'")
    long countCompletedRefundsByReason(Refund.RefundReason reason);
}
```

### 3.3 Automated Refund Recovery

**Strategy**: Implement background job to retry failed refunds and handle stuck transactions.

```java
// refund/RefundRecoveryService.java
package com.vendingmachine.payment.refund;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefundRecoveryService {

    private final RefundRepository refundRepository;
    private final RefundService refundService;
    private final AlertService alertService;

    /**
     * Retry failed refunds every 15 minutes
     */
    @Scheduled(fixedDelay = 900000) // 15 minutes
    @Transactional
    public void retryFailedRefunds() {
        log.info("Starting failed refund recovery process");

        LocalDateTime retryAfter = LocalDateTime.now().minusMinutes(15);
        List<Refund> failedRefunds = refundRepository.findFailedRefundsForRetry(retryAfter);

        log.info("Found {} failed refunds to retry", failedRefunds.size());

        for (Refund refund : failedRefunds) {
            try {
                log.info("Retrying refund: {} (attempt {})", refund.getRefundId(), refund.getRetryCount() + 1);
                refundService.executeRefund(refund);

            } catch (Exception e) {
                log.error("Failed to retry refund: {}", refund.getRefundId(), e);

                // Alert if max retries reached
                if (refund.getRetryCount() >= 3) {
                    alertService.sendRefundFailureAlert(refund);
                }
            }
        }
    }

    /**
     * Monitor pending refunds and alert if stuck
     */
    @Scheduled(fixedDelay = 1800000) // 30 minutes
    public void monitorStuckRefunds() {
        log.info("Checking for stuck refunds");

        LocalDateTime threshold = LocalDateTime.now().minusHours(1);
        List<Refund> stuckRefunds = refundRepository.findByStatus(Refund.RefundStatus.PROCESSING)
            .stream()
            .filter(r -> r.getProcessedAt().isBefore(threshold))
            .toList();

        if (!stuckRefunds.isEmpty()) {
            log.warn("Found {} stuck refunds", stuckRefunds.size());
            stuckRefunds.forEach(alertService::sendStuckRefundAlert);
        }
    }
}
```

### 3.4 Compensating Transaction for Different Failure Scenarios

```java
// compensation/CompensationStrategy.java
package com.vendingmachine.transaction.compensation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CompensationStrategy {

    private final RefundService refundService;
    private final InventoryClient inventoryClient;
    private final NotificationService notificationService;

    /**
     * Scenario 1: Payment succeeded but dispensing failed
     */
    @Transactional
    public void compensateDispensingFailure(Transaction transaction) {
        log.info("Compensating for dispensing failure: transaction {}", transaction.getId());

        try {
            // 1. Issue full refund
            Refund refund = refundService.refundTransaction(
                transaction.getId(),
                Refund.RefundReason.DISPENSING_FAILED,
                "SYSTEM"
            );

            // 2. Restore inventory (if reserved)
            inventoryClient.releaseReservation(transaction.getProductId(), transaction.getQuantity());

            // 3. Notify customer
            notificationService.sendRefundNotification(
                transaction.getUserId(),
                "Your purchase failed. Refund of $" + transaction.getTotalAmount() + " is being processed."
            );

            // 4. Update transaction status
            transaction.setStatus(TransactionStatus.REFUNDED);
            transaction.setRefundId(refund.getRefundId());

            log.info("Successfully compensated dispensing failure for transaction {}", transaction.getId());

        } catch (Exception e) {
            log.error("Failed to compensate dispensing failure for transaction {}", transaction.getId(), e);
            // Escalate to manual review
            notificationService.sendAdminAlert("Manual refund required for transaction " + transaction.getId());
            throw new CompensationException("Compensation failed", e);
        }
    }

    /**
     * Scenario 2: Partial dispensing (some items dispensed, some failed)
     */
    @Transactional
    public void compensatePartialDispensing(Transaction transaction, List<LineItem> dispensedItems) {
        log.info("Compensating for partial dispensing: transaction {}", transaction.getId());

        try {
            // Calculate partial refund amount
            BigDecimal refundAmount = calculatePartialRefundAmount(transaction, dispensedItems);

            if (refundAmount.compareTo(BigDecimal.ZERO) > 0) {
                // Issue partial refund
                String refundId = UUID.randomUUID().toString();
                refundService.processRefund(
                    refundId,
                    transaction.getId(),
                    refundAmount,
                    Refund.RefundReason.DISPENSING_FAILED,
                    "SYSTEM"
                );

                // Notify customer
                notificationService.sendPartialRefundNotification(
                    transaction.getUserId(),
                    dispensedItems,
                    refundAmount
                );
            }

            transaction.setStatus(TransactionStatus.PARTIALLY_COMPLETED);

        } catch (Exception e) {
            log.error("Failed to compensate partial dispensing for transaction {}", transaction.getId(), e);
            throw new CompensationException("Partial compensation failed", e);
        }
    }

    /**
     * Scenario 3: Timeout - transaction stuck in processing
     */
    @Transactional
    public void compensateTimeout(Transaction transaction) {
        log.info("Compensating for timeout: transaction {}", transaction.getId());

        try {
            // Full refund for timeout
            refundService.refundTransaction(
                transaction.getId(),
                Refund.RefundReason.TIMEOUT,
                "SYSTEM"
            );

            // Release any reservations
            inventoryClient.releaseReservation(transaction.getProductId(), transaction.getQuantity());

            // Notify customer
            notificationService.sendTimeoutNotification(transaction.getUserId());

            transaction.setStatus(TransactionStatus.TIMEOUT);

        } catch (Exception e) {
            log.error("Failed to compensate timeout for transaction {}", transaction.getId(), e);
            throw new CompensationException("Timeout compensation failed", e);
        }
    }

    private BigDecimal calculatePartialRefundAmount(Transaction transaction, List<LineItem> dispensedItems) {
        BigDecimal totalAmount = transaction.getTotalAmount();
        BigDecimal dispensedAmount = dispensedItems.stream()
            .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return totalAmount.subtract(dispensedAmount);
    }
}
```

---

## 4. Implementation Roadmap - REVISED ORDER

### Phase 1: Critical Fault Tolerance (Week 1-2)

1. ✅ Add Resilience4j dependencies to all services
2. ✅ Implement circuit breakers for all HTTP clients
3. ✅ Configure retry mechanisms with exponential backoff
4. ✅ Add health indicators for all critical dependencies
5. ✅ Implement Kafka error handlers and DLQ

### Phase 2: Kafka Optimization (Week 3-4) ⚡ **PRIORITIZED**

1. ✅ Create unified DomainEvent model
2. ✅ Consolidate topics to domain-centric architecture (5 topics → 1 topic)
3. ✅ Migrate synchronous operations from Kafka to HTTP
4. ✅ Implement event type-based routing
5. ✅ Add Redis-based event deduplication
6. ✅ Deploy DLQ for failed events

### Phase 3: Refund System (Week 5-6) 💰 **DEPENDS ON KAFKA**

1. ✅ Create Refund entity and repository
2. ✅ Implement RefundService with idempotency
3. ✅ Add refund tracking to payment-service
4. ✅ Create compensation strategies for different failure scenarios
5. ✅ Implement automated refund recovery job
6. ✅ Add refund API endpoints with admin authorization

### Phase 4: Saga Pattern (Week 7-8)

1. ✅ Design saga orchestration entities
2. ✅ Implement SagaOrchestrator service
3. ✅ Create compensators for each business operation
4. ✅ Add saga state persistence
5. ✅ Implement saga recovery mechanisms

### Phase 5: Monitoring & Observability (Week 9-10)

1. ✅ Set up distributed tracing (Sleuth + Zipkin)
2. ✅ Configure metrics export (Prometheus)
3. ✅ Create Grafana dashboards for key metrics
4. ✅ Implement custom health indicators
5. ✅ Set up alerting for critical failures

---

## Key Metrics to Monitor

### Fault Tolerance Metrics

- Circuit breaker state (open/closed/half-open)
- Retry attempt counts and success rates
- Fallback invocation frequency
- Service-to-service latency (p50, p95, p99)
- Database connection pool utilization

### Kafka Metrics

- Consumer lag per topic
- Message processing time
- DLQ message count
- Event deduplication hit rate
- Kafka broker health

### Refund Metrics

- Refund success rate
- Average refund processing time
- Failed refunds requiring manual intervention
- Refund amount by reason (dispensing failure vs timeout)
- Refund recovery job success rate

---

## Testing Strategy

### Chaos Engineering Tests

```java
// Test circuit breaker behavior
@Test
public void testCircuitBreakerOnServiceFailure() {
    // Simulate payment service down
    wireMockServer.stubFor(post("/api/payment/process")
        .willReturn(aResponse().withStatus(500)));

    // Should fail and open circuit
    assertThrows(CircuitBreakerOpenException.class, () -> {
        for (int i = 0; i < 10; i++) {
            transactionService.processTransaction(request);
        }
    });
}

// Test refund idempotency
@Test
public void testRefundIdempotency() {
    String refundId = UUID.randomUUID().toString();

    Refund refund1 = refundService.processRefund(refundId, 1L, BigDecimal.valueOf(10),
        Refund.RefundReason.DISPENSING_FAILED, "SYSTEM");

    Refund refund2 = refundService.processRefund(refundId, 1L, BigDecimal.valueOf(10),
        Refund.RefundReason.DISPENSING_FAILED, "SYSTEM");

    assertEquals(refund1.getId(), refund2.getId());
    verify(paymentGatewayClient, times(1)).processRefund(any(), any());
}

// Test saga compensation
@Test
public void testSagaCompensationOnDispensingFailure() {
    // Setup: successful payment, failed dispensing
    when(paymentClient.processPayment(any())).thenReturn(PaymentResponse.success());
    when(dispensingClient.dispenseItem(any())).thenThrow(new DispensingException());

    assertThrows(SagaException.class, () -> {
        sagaOrchestrator.executeTransactionSaga(1L);
    });

    // Verify compensation occurred
    verify(paymentCompensator).refundPayment(any());
    verify(inventoryCompensator).releaseReservation(any());
}
```

---

## Conclusion

This improvement guide provides comprehensive strategies to enhance your vending machine microservices system across three critical areas:

1. **Fault Tolerance**: Circuit breakers, retries, and bulkheads ensure system resilience
2. **Kafka Optimization**: Consolidated topics and smart sync/async patterns reduce complexity
3. **Refund System**: Robust compensatory actions with idempotency and audit trails

Implement these changes incrementally following the roadmap, and continuously monitor metrics to validate improvements.
