# 🔧 Kafka Infrastructure - Detailed Setup Guide

## 📋 Description

This guide provides detailed instructions for setting up and configuring the Kafka infrastructure for the Vending Machine microservices ecosystem.

## 🏗️ Full Architecture

```text
┌─────────────────────────────────────────────────────────────────┐
│                    Vending Machine Microservices                │
├─────────────┬─────────────┬─────────────┬─────────────┬─────────┤
│ Inventory   │  Payment    │ Transaction │ Dispensing  │ Notific.│
│ (port 8081) │ (port 8082) │ (port 8083) │ (port 8084) │ (8085)  │
└─────────────┴─────────────┴─────────────┴─────────────┴─────────┘
                                │
                                │ Events via Kafka
                                │
┌─────────────────────────────────────────────────────────────────┐
│                      Kafka Infrastructure                       │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐  │
│  │ Kafka UI    │  │   Kafka     │  │      Zookeeper          │  │
│  │ (port 9090) │  │(port 9092)  │  │     (port 2181)         │  │
│  └─────────────┘  └─────────────┘  └─────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

## 🔧 Docker Compose Configuration

### Zookeeper Service

```yaml
zookeeper:
  image: confluentinc/cp-zookeeper:7.4.0
  hostname: zookeeper
  container_name: vending-zookeeper
  ports:
    - "2181:2181"
  environment:
    ZOOKEEPER_CLIENT_PORT: 2181
    ZOOKEEPER_TICK_TIME: 2000
  volumes:
    - zookeeper-data:/var/lib/zookeeper/data
    - zookeeper-logs:/var/lib/zookeeper/log
  networks:
    - vending-kafka-network
```

### Kafka Service

```yaml
kafka:
  image: confluentinc/cp-kafka:7.4.0
  hostname: kafka
  container_name: vending-kafka
  depends_on:
    - zookeeper
  ports:
    - "9092:9092"
    - "9101:9101"
  environment:
    KAFKA_BROKER_ID: 1
    KAFKA_ZOOKEEPER_CONNECT: "zookeeper:2181"
    KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
    KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:29092,PLAINTEXT_HOST://localhost:9092
    KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:29092,PLAINTEXT_HOST://0.0.0.0:9092
    KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
    KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
    KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
    KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS: 0
    KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
    KAFKA_JMX_PORT: 9101
    KAFKA_JMX_HOSTNAME: localhost
  volumes:
    - kafka-data:/var/lib/kafka/data
  networks:
    - vending-kafka-network
```

### Kafka UI Service

```yaml
kafka-ui:
  image: provectuslabs/kafka-ui:latest
  container_name: vending-kafka-ui
  depends_on:
    - kafka
  ports:
    - "9090:8080"
  environment:
    KAFKA_CLUSTERS_0_NAME: vending-machine-cluster
    KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: kafka:29092
    KAFKA_CLUSTERS_0_ZOOKEEPER: zookeeper:2181
  networks:
    - vending-kafka-network
```

## 📊 Topic Configuration

### Automatic Topics

The following topics are automatically created on startup:

```yaml
Topics:
  transaction-events:
    partitions: 1
    replication-factor: 1
    description: "Transaction lifecycle events (STARTED, COMPLETED, FAILED)"

  payment-events:
    partitions: 1
    replication-factor: 1
    description: "Payment processing events (PROCESSING, SUCCESSFUL, FAILED)"

  inventory-events:
    partitions: 1
    replication-factor: 1
    description: "Stock and inventory events (UPDATED, LOW, OUT_OF_STOCK)"

  dispensing-events:
    partitions: 1
    replication-factor: 1
    description: "Hardware dispensing events (STARTED, COMPLETED, FAILED)"

  notification-events:
    partitions: 1
    replication-factor: 1
    description: "System notifications and alerts"
```

### Manual Topic Creation

```powershell
# Create a topic manually
docker exec vending-kafka kafka-topics --bootstrap-server localhost:9092 --create --topic custom-topic --partitions 1 --replication-factor 1

# List all topics
docker exec vending-kafka kafka-topics --bootstrap-server localhost:9092 --list

# Describe a topic
docker exec vending-kafka kafka-topics --bootstrap-server localhost:9092 --describe --topic transaction-events
```

## 🔌 Spring Boot Microservice Configuration

### Base Configuration (application.properties)

```properties
# Kafka Configuration
spring.kafka.bootstrap-servers=localhost:9092
spring.kafka.consumer.auto-offset-reset=earliest
spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer
spring.kafka.consumer.properties.spring.json.trusted.packages=*
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer
spring.kafka.consumer.enable-auto-commit=false
spring.kafka.listener.ack-mode=manual_immediate
```

### Producer Configuration

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
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, false);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
```

### Consumer Configuration

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
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "vending-machine-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);
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

## 📝 Usage Examples

### Producer Example

```java
@Service
public class TransactionEventPublisher {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    public void publishTransactionEvent(TransactionEvent event) {
        try {
            kafkaTemplate.send("transaction-events", event.getTransactionId(), event);
            log.info("KAFKA: Published transaction event: {}", event);
        } catch (Exception e) {
            log.error("KAFKA: Failed to publish transaction event", e);
        }
    }
}
```

### Consumer Example

```java
@Component
public class PaymentEventConsumer {

    @KafkaListener(topics = "transaction-events", groupId = "payment-group")
    public void handleTransactionEvent(
            @Payload TransactionEvent event,
            @Header Map<String, Object> headers,
            Acknowledgment ack) {
        try {
            log.info("KAFKA: Received transaction event: {}", event);

            // Process the event
            processTransactionEvent(event);

            // Acknowledge the message
            ack.acknowledge();
        } catch (Exception e) {
            log.error("KAFKA: Error processing transaction event", e);
            // Handle error or implement retry logic
        }
    }
}
```

## 🌐 Environment Variables

Create a `.env` file for custom configurations:

```env
# Kafka Configuration
KAFKA_BROKER_ID=1
KAFKA_AUTO_CREATE_TOPICS=true
KAFKA_DELETE_TOPIC_ENABLE=true
KAFKA_LOG_RETENTION_HOURS=168

# Zookeeper Configuration
ZOOKEEPER_CLIENT_PORT=2181
ZOOKEEPER_TICK_TIME=2000

# Kafka UI Configuration
KAFKA_UI_PORT=9090
KAFKA_CLUSTERS_0_NAME=vending-machine-cluster
```

## 🔒 Security Configuration (Optional)

For environments that require authentication:

```yaml
# Add to kafka service in docker-compose.yml
environment:
  KAFKA_SECURITY_INTER_BROKER_PROTOCOL: SASL_PLAINTEXT
  KAFKA_SASL_MECHANISM_INTER_BROKER_PROTOCOL: PLAIN
  KAFKA_SASL_ENABLED_MECHANISMS: PLAIN
  KAFKA_OPTS: "-Djava.security.auth.login.config=/etc/kafka/kafka_server_jaas.conf"
```

## 📊 Monitoring and Metrics

### JMX Metrics

Kafka exposes JMX metrics on port 9101. You can connect tools like:

- JConsole
- Prometheus + JMX Exporter
- Grafana

### Health Checks

```powershell
# Verify Kafka is responding
docker exec vending-kafka kafka-broker-api-versions --bootstrap-server localhost:9092

# Verify topics
docker exec vending-kafka kafka-topics --bootstrap-server localhost:9092 --list

# Verify consumer groups
docker exec vending-kafka kafka-consumer-groups --bootstrap-server localhost:9092 --list
```

## 📁 Recommended Event Structure

### Event Structure

```json
{
  "eventId": "uuid",
  "eventType": "TRANSACTION_STARTED|PAYMENT_SUCCESSFUL|STOCK_UPDATED|etc",
  "entityId": "string",
  "entityType": "TRANSACTION|PAYMENT|PRODUCT|etc",
  "timestamp": "2024-01-01T10:00:00Z",
  "data": {
    // Service specific data
  },
  "metadata": {
    "source": "service-name",
    "version": "1.0",
    "correlationId": "uuid"
  }
}
```

## 🚀 Next Steps

1. Customize the configuration according to your needs
2. Implement producers in your microservices
3. Implement the corresponding consumers
4. Configure monitoring and alerts
5. Consider production-level settings

## 🔗 References

- [Apache Kafka Documentation](https://kafka.apache.org/documentation/)
- [Spring Kafka Documentation](https://spring.io/projects/spring-kafka)
- [Confluent Platform](https://docs.confluent.io/)
