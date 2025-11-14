# 🔗 Microservice Integration Guide

## 📋 Overview

This guide explains how to integrate the Vending Machine microservices with the Kafka infrastructure for event-driven communication.

## 🚀 Prerequisites

1. Kafka infrastructure running (`.\scripts\kafka-manager.ps1 start`)
2. All topics created automatically
3. Microservices configured with proper Kafka dependencies

## 📦 Required Dependencies

Add these dependencies to each microservice's `pom.xml`:

```xml
<!-- Spring Kafka -->
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>

<!-- JSON Processing -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
```

## ⚙️ Configuration

### application.properties

Add to each microservice's `application.properties`:

```properties
# Kafka Configuration
spring.kafka.bootstrap-servers=localhost:9092

# Producer Configuration
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer
spring.kafka.producer.acks=1
spring.kafka.producer.retries=3

# Consumer Configuration
spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer
spring.kafka.consumer.properties.spring.json.trusted.packages=*
spring.kafka.consumer.auto-offset-reset=earliest
spring.kafka.consumer.enable-auto-commit=false

# Listener Configuration
spring.kafka.listener.ack-mode=manual_immediate
```

## 🏗️ Service-Specific Integration

### 1. Transaction Service (Port 8083)

**Produces**: `transaction-events`
**Consumes**: `payment-events`, `dispensing-events`

#### Event Producer

```java
@Service
public class TransactionEventPublisher {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    public void publishTransactionStarted(TransactionStartedEvent event) {
        kafkaTemplate.send("transaction-events", event.getTransactionId(), event);
        log.info("KAFKA: Published transaction started event: {}", event.getTransactionId());
    }

    public void publishTransactionCompleted(TransactionCompletedEvent event) {
        kafkaTemplate.send("transaction-events", event.getTransactionId(), event);
        log.info("KAFKA: Published transaction completed event: {}", event.getTransactionId());
    }
}
```

#### Event Consumer

```java
@Component
public class TransactionEventConsumer {

    @KafkaListener(topics = "payment-events", groupId = "transaction-group")
    public void handlePaymentEvent(@Payload PaymentEvent event, Acknowledgment ack) {
        try {
            log.info("KAFKA: Received payment event: {}", event);
            processPaymentEvent(event);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("KAFKA: Error processing payment event", e);
        }
    }
}
```

### 2. Payment Service (Port 8082)

**Produces**: `payment-events`
**Consumes**: `transaction-events`

#### Event Producer in Java

```java
@Service
public class PaymentEventPublisher {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    public void publishPaymentProcessing(PaymentProcessingEvent event) {
        kafkaTemplate.send("payment-events", event.getPaymentId(), event);
        log.info("KAFKA: Published payment processing event: {}", event.getPaymentId());
    }

    public void publishPaymentCompleted(PaymentCompletedEvent event) {
        kafkaTemplate.send("payment-events", event.getPaymentId(), event);
        log.info("KAFKA: Published payment completed event: {}", event.getPaymentId());
    }
}
```

### 3. Inventory Service (Port 8081)

**Produces**: `inventory-events`
**Consumes**: `transaction-events`, `dispensing-events`

#### Event Publisher

```java
@Service
public class InventoryEventPublisher {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    public void publishStockUpdated(StockUpdatedEvent event) {
        kafkaTemplate.send("inventory-events", event.getProductId(), event);
        log.info("KAFKA: Published stock updated event for product: {}", event.getProductId());
    }

    public void publishOutOfStock(OutOfStockEvent event) {
        kafkaTemplate.send("inventory-events", event.getProductId(), event);
        log.info("KAFKA: Published out of stock event for product: {}", event.getProductId());
    }
}
```

### 4. Dispensing Service (Port 8084)

**Produces**: `dispensing-events`
**Consumes**: `transaction-events`, `payment-events`, `inventory-events`

#### Unified Event Consumer

```java
@Component
public class DispensingEventConsumer {

    @KafkaListener(topics = "transaction-events", groupId = "dispensing-group")
    public void handleTransactionEvent(@Payload TransactionEvent event, Acknowledgment ack) {
        try {
            if ("TRANSACTION_COMPLETED".equals(event.getEventType())) {
                initiateDispensing(event);
            }
            ack.acknowledge();
        } catch (Exception e) {
            log.error("KAFKA: Error processing transaction event", e);
        }
    }

    @KafkaListener(topics = "payment-events", groupId = "dispensing-group")
    public void handlePaymentEvent(@Payload PaymentEvent event, Acknowledgment ack) {
        try {
            if ("PAYMENT_SUCCESSFUL".equals(event.getEventType())) {
                confirmPaymentForDispensing(event);
            }
            ack.acknowledge();
        } catch (Exception e) {
            log.error("KAFKA: Error processing payment event", e);
        }
    }
}
```

### 5. Notification Service (Port 8085)

**Produces**: `notification-events`
**Consumes**: All topics

#### Unified Event Consumer in Java

```java
@Component
public class NotificationEventConsumer {

    @KafkaListener(topics = {
        "transaction-events",
        "payment-events",
        "inventory-events",
        "dispensing-events"
    }, groupId = "notification-group")
    public void handleAnyEvent(@Payload Object event,
                              @Header("kafka_receivedTopic") String topic,
                              Acknowledgment ack) {
        try {
            log.info("KAFKA: Received event from topic {}: {}", topic, event);

            switch (topic) {
                case "transaction-events":
                    handleTransactionNotification((TransactionEvent) event);
                    break;
                case "payment-events":
                    handlePaymentNotification((PaymentEvent) event);
                    break;
                case "inventory-events":
                    handleInventoryNotification((InventoryEvent) event);
                    break;
                case "dispensing-events":
                    handleDispensingNotification((DispensingEvent) event);
                    break;
            }

            ack.acknowledge();
        } catch (Exception e) {
            log.error("KAFKA: Error processing notification event", e);
        }
    }
}
```

## 📨 Event Structure Standards

### Base Event Class

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseEvent {
    private String eventId = UUID.randomUUID().toString();
    private String eventType;
    private String entityId;
    private String entityType;
    private LocalDateTime timestamp = LocalDateTime.now();
    private EventMetadata metadata;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventMetadata {
    private String source;
    private String version = "1.0";
    private String correlationId;
    private Map<String, Object> additionalData = new HashMap<>();
}
```

### Transaction Events

```java
@Data
@EqualsAndHashCode(callSuper = true)
public class TransactionStartedEvent extends BaseEvent {
    private String transactionId;
    private String userId;
    private String productId;
    private BigDecimal amount;
    private String paymentMethod;

    public TransactionStartedEvent(String transactionId, String userId, String productId, BigDecimal amount) {
        super();
        this.transactionId = transactionId;
        this.userId = userId;
        this.productId = productId;
        this.amount = amount;
        setEventType("TRANSACTION_STARTED");
        setEntityId(transactionId);
        setEntityType("TRANSACTION");
    }
}
```

### Payment Events

```java
@Data
@EqualsAndHashCode(callSuper = true)
public class PaymentCompletedEvent extends BaseEvent {
    private String paymentId;
    private String transactionId;
    private BigDecimal amount;
    private String paymentMethod;
    private String status;

    public PaymentCompletedEvent(String paymentId, String transactionId, BigDecimal amount) {
        super();
        this.paymentId = paymentId;
        this.transactionId = transactionId;
        this.amount = amount;
        setEventType("PAYMENT_COMPLETED");
        setEntityId(paymentId);
        setEntityType("PAYMENT");
    }
}
```

## 🔧 Configuration Classes

### Kafka Producer Config

```java
@Configuration
@EnableKafka
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
```

### Kafka Consumer Config

```java
@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }
}
```

## 🔍 Testing Integration

### Integration Test Example

```java
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties")
@EmbeddedKafka(partitions = 1, brokerProperties = {"listeners=PLAINTEXT://localhost:9093", "port=9093"})
class KafkaIntegrationTest {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private TransactionEventPublisher eventPublisher;

    @Test
    void shouldPublishAndConsumeTransactionEvent() {
        // Given
        TransactionStartedEvent event = new TransactionStartedEvent("tx-123", "user-456", "product-789", new BigDecimal("2.50"));

        // When
        eventPublisher.publishTransactionStarted(event);

        // Then
        // Verify event was published and consumed
        // Add appropriate assertions
    }
}
```

## 🚦 Best Practices

### Error Handling

```java
@Component
public class RobustEventConsumer {

    @KafkaListener(topics = "transaction-events", groupId = "payment-group")
    public void handleTransactionEvent(@Payload TransactionEvent event, Acknowledgment ack) {
        try {
            // Process event
            processEvent(event);

            // Acknowledge only on success
            ack.acknowledge();

        } catch (RetryableException e) {
            log.warn("KAFKA: Retryable error processing event, will retry: {}", e.getMessage());
            // Don't acknowledge - message will be redelivered

        } catch (Exception e) {
            log.error("KAFKA: Fatal error processing event: {}", event, e);

            // Send to dead letter topic or handle appropriately
            handleFatalError(event, e);

            // Acknowledge to prevent infinite redelivery
            ack.acknowledge();
        }
    }
}
```

### Correlation ID Tracking

```java
@Component
public class CorrelationIdAwareConsumer {

    @KafkaListener(topics = "transaction-events", groupId = "payment-group")
    public void handleEvent(@Payload TransactionEvent event, Acknowledgment ack) {
        String correlationId = event.getMetadata().getCorrelationId();

        try (MDCCloseable mdcCloseable = MDC.putCloseable("correlationId", correlationId)) {
            log.info("KAFKA: Processing event with correlation ID: {}", correlationId);
            processEvent(event);
            ack.acknowledge();
        }
    }
}
```

## 📊 Monitoring Integration

### Health Check

```java
@Component
public class KafkaHealthIndicator implements HealthIndicator {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public Health health() {
        try {
            kafkaTemplate.getProducerFactory().createProducer().close();
            return Health.up().withDetail("kafka", "Available").build();
        } catch (Exception e) {
            return Health.down().withDetail("kafka", "Unavailable").withException(e).build();
        }
    }
}
```

---

**This integration guide ensures all Vending Machine microservices communicate effectively through Kafka events.** 🏪
