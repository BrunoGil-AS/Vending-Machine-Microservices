# Infrastructure Services Diagrams

## Table of Contents

- [Overall Infrastructure Topology](#overall-infrastructure-topology)
- [Config Server Architecture](#config-server-architecture)
- [Eureka Server Architecture](#eureka-server-architecture)
- [Kafka Architecture](#kafka-architecture)
- [MySQL Database Architecture](#mysql-database-architecture)

---

## Overall Infrastructure Topology

```mermaid
---
config:
  theme: base
  themeVariables:
    primaryColor: '#a39cd1ff'
    primaryTextColor: '#000000'
    secondaryTextColor: '#ffffffff'
    primaryBorderColor: '#000000'
    lineColor: '#000000ff'
    secondaryColor: '#90b590ff'
    tertiaryColor: '#f9f6a1ff'
  look: neo
title: 'Infrastructure Services: Overall Infrastructure Topology'
---
graph TB
    subgraph "Configuration Management"
        CONFIG[Config Server<br/>Port 8888<br/>File-based Config]
    end

    subgraph "Service Discovery"
        EUREKA[Eureka Server<br/>Port 8761<br/>Service Registry]
    end

    subgraph "API Layer"
        GATEWAY[API Gateway<br/>Port 8080]
    end

    subgraph "Business Services"
        INVENTORY[Inventory<br/>8081]
        PAYMENT[Payment<br/>8082]
        TRANSACTION[Transaction<br/>8083]
        DISPENSING[Dispensing<br/>8084]
        NOTIFICATION[Notification<br/>8085]
    end

    subgraph "Message Broker"
        ZOOKEEPER[Zookeeper<br/>Port 2181<br/>Coordination]
        KAFKA[Kafka Broker<br/>Port 9092<br/>5 Topics]
    end

    subgraph "Database"
        MYSQL[(MySQL Server<br/>Port 3306<br/>6 Databases)]
    end

    CONFIG -.->|Provides Config| EUREKA
    CONFIG -.->|Provides Config| GATEWAY
    CONFIG -.->|Provides Config| INVENTORY
    CONFIG -.->|Provides Config| PAYMENT
    CONFIG -.->|Provides Config| TRANSACTION
    CONFIG -.->|Provides Config| DISPENSING
    CONFIG -.->|Provides Config| NOTIFICATION

    EUREKA <-->|Register/Discover| GATEWAY
    EUREKA <-->|Register/Discover| INVENTORY
    EUREKA <-->|Register/Discover| PAYMENT
    EUREKA <-->|Register/Discover| TRANSACTION
    EUREKA <-->|Register/Discover| DISPENSING
    EUREKA <-->|Register/Discover| NOTIFICATION

    ZOOKEEPER <--> KAFKA

    INVENTORY <--> KAFKA
    PAYMENT <--> KAFKA
    TRANSACTION <--> KAFKA
    DISPENSING <--> KAFKA
    NOTIFICATION <--> KAFKA

    GATEWAY <--> MYSQL
    INVENTORY <--> MYSQL
    PAYMENT <--> MYSQL
    TRANSACTION <--> MYSQL
    DISPENSING <--> MYSQL
    NOTIFICATION <--> MYSQL

    style CONFIG fill:#c5cae9
    style EUREKA fill:#ffccbc
    style KAFKA fill:#fff9c4
    style ZOOKEEPER fill:#fff9c4
    style MYSQL fill:#c8e6c9
```

---

## Config Server Architecture

### Component Diagram

```mermaid
---
config:
  theme: base
  themeVariables:
    primaryColor: '#a39cd1ff'
    primaryTextColor: '#000000'
    secondaryTextColor: '#ffffffff'
    primaryBorderColor: '#000000'
    lineColor: '#000000ff'
    secondaryColor: '#90b590ff'
    tertiaryColor: '#f9f6a1ff'
  look: neo
title: 'Infrastructure Services: Config Server Component Diagram'
---
graph TB
    subgraph "Config Server - Port 8888"
        CONFIG_APP[Config Server Application<br/>@EnableConfigServer]
        FILE_REPO[File Repository Backend<br/>Local Properties]
        CONFIG_PROPS[Application Properties<br/>Per Service]
    end

    subgraph "Client Services"
        EUREKA[Eureka Server]
        GATEWAY[API Gateway]
        BUSINESS[Business Services]
    end

    subgraph "Configuration Files"
        EUREKA_PROPS[eureka-server.properties]
        GATEWAY_PROPS[api-gateway.properties]
        INVENTORY_PROPS[inventory-service.properties]
        PAYMENT_PROPS[payment-service.properties]
        TRANSACTION_PROPS[transaction-service.properties]
        DISPENSING_PROPS[dispensing-service.properties]
        NOTIFICATION_PROPS[notification-service.properties]
    end

    CONFIG_APP --> FILE_REPO
    FILE_REPO --> CONFIG_PROPS

    CONFIG_PROPS --> EUREKA_PROPS
    CONFIG_PROPS --> GATEWAY_PROPS
    CONFIG_PROPS --> INVENTORY_PROPS
    CONFIG_PROPS --> PAYMENT_PROPS
    CONFIG_PROPS --> TRANSACTION_PROPS
    CONFIG_PROPS --> DISPENSING_PROPS
    CONFIG_PROPS --> NOTIFICATION_PROPS

    EUREKA_PROPS -.->|HTTP GET| EUREKA
    GATEWAY_PROPS -.->|HTTP GET| GATEWAY
    INVENTORY_PROPS -.->|HTTP GET| BUSINESS

    style CONFIG_APP fill:#c5cae9
```

### Configuration Hierarchy

```mermaid
---
config:
  theme: base
  themeVariables:
    primaryColor: '#a39cd1ff'
    primaryTextColor: '#000000'
    secondaryTextColor: '#ffffffff'
    primaryBorderColor: '#000000'
    lineColor: '#000000ff'
    secondaryColor: '#90b590ff'
    tertiaryColor: '#f9f6a1ff'
  look: neo
title: 'Infrastructure Services: Configuration Hierarchy'
---
graph LR
    subgraph "Bootstrap Phase"
        BOOT[application.properties<br/>in service]
        BOOT_CONFIG[spring.config.import=<br/>configserver:http://localhost:8888]
    end

    subgraph "Config Server Phase"
        CONFIG_SERVER[Config Server]
        SERVICE_CONFIG[service-name.properties]
    end

    subgraph "Runtime Phase"
        MERGED[Merged Configuration<br/>Bootstrap + Config Server]
        APPLICATION[Service Application Context]
    end

    BOOT --> BOOT_CONFIG
    BOOT_CONFIG -->|Fetch at startup| CONFIG_SERVER
    CONFIG_SERVER --> SERVICE_CONFIG
    SERVICE_CONFIG --> MERGED
    BOOT --> MERGED
    MERGED --> APPLICATION
```

### Stored Configuration Properties

| Service              | Port | Database             | Kafka Topics       | Eureka |
| -------------------- | ---- | -------------------- | ------------------ | ------ |
| eureka-server        | 8761 | None                 | None               | Self   |
| api-gateway          | 8080 | vending_auth         | None               | Client |
| inventory-service    | 8081 | vending_inventory    | inventory-events   | Client |
| payment-service      | 8082 | vending_payment      | payment-events     | Client |
| transaction-service  | 8083 | vending_transaction  | transaction-events | Client |
| dispensing-service   | 8084 | vending_dispensing   | dispensing-events  | Client |
| notification-service | 8085 | vending_notification | All topics         | Client |

---

## Eureka Server Architecture

### Service Registration Flow

```mermaid
---
config:
  theme: base
  themeVariables:
    primaryColor: '#a39cd1ff'
    primaryTextColor: '#000000'
    secondaryTextColor: '#ffffffff'
    primaryBorderColor: '#000000'
    lineColor: '#000000ff'
    secondaryColor: '#90b590ff'
    tertiaryColor: '#f9f6a1ff'
  look: neo
title: 'Infrastructure Services: Service Registration Flow'
---
sequenceDiagram
    autonumber
    participant Service as Business Service
    participant Eureka as Eureka Server
    participant Gateway as API Gateway

    Note over Service: Service starts up

    Service->>Service: Read eureka.client.serviceUrl
    Service->>Eureka: POST /eureka/apps/{APP_ID}<br/>Register instance
    Eureka->>Eureka: Store instance metadata<br/>(host, port, status)
    Eureka-->>Service: 204 No Content

    loop Every 30 seconds
        Service->>Eureka: PUT /eureka/apps/{APP_ID}/{INSTANCE_ID}<br/>Heartbeat
        Eureka-->>Service: 200 OK
    end

    Note over Gateway: Gateway needs to call service

    Gateway->>Eureka: GET /eureka/apps/{APP_ID}<br/>Discover instances
    Eureka-->>Gateway: Instance list<br/>(host, port, status)

    Gateway->>Gateway: Load balance<br/>Select instance
    Gateway->>Service: HTTP Request
```

### Eureka Dashboard View

```mermaid
---
config:
  theme: base
  themeVariables:
    primaryColor: '#a39cd1ff'
    primaryTextColor: '#000000'
    secondaryTextColor: '#ffffffff'
    primaryBorderColor: '#000000'
    lineColor: '#000000ff'
    secondaryColor: '#90b590ff'
    tertiaryColor: '#f9f6a1ff'
  look: neo
title: 'Infrastructure Services: Eureka Dashboard View'
---
graph TB
    subgraph "Eureka Server - http://localhost:8761"
        DASHBOARD[Eureka Dashboard<br/>Web UI]
        REGISTRY[Service Registry<br/>In-Memory]
    end

    subgraph "Registered Instances"
        GATEWAY_INST[API-GATEWAY<br/>localhost:8080<br/>Status: UP]
        INV_INST[INVENTORY-SERVICE<br/>localhost:8081<br/>Status: UP]
        PAY_INST[PAYMENT-SERVICE<br/>localhost:8082<br/>Status: UP]
        TRANS_INST[TRANSACTION-SERVICE<br/>localhost:8083<br/>Status: UP]
        DISP_INST[DISPENSING-SERVICE<br/>localhost:8084<br/>Status: UP]
        NOTIF_INST[NOTIFICATION-SERVICE<br/>localhost:8085<br/>Status: UP]
    end

    DASHBOARD --> REGISTRY

    REGISTRY --> GATEWAY_INST
    REGISTRY --> INV_INST
    REGISTRY --> PAY_INST
    REGISTRY --> TRANS_INST
    REGISTRY --> DISP_INST
    REGISTRY --> NOTIF_INST

    style DASHBOARD fill:#ffccbc
    style REGISTRY fill:#ffccbc
```

### Instance Metadata

```json
{
  "instance": {
    "instanceId": "localhost:inventory-service:8081",
    "app": "INVENTORY-SERVICE",
    "ipAddr": "127.0.0.1",
    "port": {
      "$": 8081,
      "@enabled": true
    },
    "status": "UP",
    "healthCheckUrl": "http://localhost:8081/actuator/health",
    "homePageUrl": "http://localhost:8081/",
    "metadata": {
      "management.port": "8081"
    }
  }
}
```

---

## Kafka Architecture

### Topic Structure

```mermaid
---
config:
  theme: base
  themeVariables:
    primaryColor: '#a39cd1ff'
    primaryTextColor: '#000000'
    secondaryTextColor: '#ffffffff'
    primaryBorderColor: '#000000'
    lineColor: '#000000ff'
    secondaryColor: '#90b590ff'
    tertiaryColor: '#f9f6a1ff'
  look: neo
title: 'Infrastructure Services: Kafka Topic Structure'
---
graph TB
    subgraph "Kafka Broker - Port 9092"
        BROKER[Kafka Broker]
    end

    subgraph "Topics (Single Partition, Single Replica)"
        TRANS_TOPIC[transaction-events<br/>Partition: 0<br/>Replication: 1]
        PAY_TOPIC[payment-events<br/>Partition: 0<br/>Replication: 1]
        INV_TOPIC[inventory-events<br/>Partition: 0<br/>Replication: 1]
        DISP_TOPIC[dispensing-events<br/>Partition: 0<br/>Replication: 1]
        NOTIF_TOPIC[notification-events<br/>Partition: 0<br/>Replication: 1]
    end

    subgraph "Producers"
        TRANS_PROD[Transaction Service]
        PAY_PROD[Payment Service]
        INV_PROD[Inventory Service]
        DISP_PROD[Dispensing Service]
    end

    subgraph "Consumers"
        TRANS_CONS[Transaction Service<br/>Group: transaction-group]
        PAY_CONS[Payment Service<br/>Group: payment-group]
        INV_CONS[Inventory Service<br/>Group: inventory-group]
        DISP_CONS[Dispensing Service<br/>Group: dispensing-group]
        NOTIF_CONS[Notification Service<br/>Group: notification-group]
    end

    BROKER --> TRANS_TOPIC
    BROKER --> PAY_TOPIC
    BROKER --> INV_TOPIC
    BROKER --> DISP_TOPIC
    BROKER --> NOTIF_TOPIC

    TRANS_PROD -->|Publish| TRANS_TOPIC
    PAY_PROD -->|Publish| PAY_TOPIC
    INV_PROD -->|Publish| INV_TOPIC
    DISP_PROD -->|Publish| DISP_TOPIC

    TRANS_TOPIC -->|Subscribe| TRANS_CONS
    PAY_TOPIC -->|Subscribe| PAY_CONS
    INV_TOPIC -->|Subscribe| INV_CONS
    DISP_TOPIC -->|Subscribe| DISP_CONS

    TRANS_TOPIC -->|Subscribe| NOTIF_CONS
    PAY_TOPIC -->|Subscribe| NOTIF_CONS
    INV_TOPIC -->|Subscribe| NOTIF_CONS
    DISP_TOPIC -->|Subscribe| NOTIF_CONS
    NOTIF_TOPIC -->|Subscribe| NOTIF_CONS

    style BROKER fill:#fff9c4
```

### Kafka Topic Configuration

```java
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic transactionEvents() {
        return TopicBuilder.name("transaction-events")
            .partitions(1)
            .replicas(1)
            .build();
    }

    @Bean
    public NewTopic paymentEvents() {
        return TopicBuilder.name("payment-events")
            .partitions(1)
            .replicas(1)
            .build();
    }

    // ... other topics
}
```

### Event Flow Summary

| Event Type               | Producer    | Primary Consumers      | Secondary Consumers |
| ------------------------ | ----------- | ---------------------- | ------------------- |
| transaction.created      | Transaction | Payment, Dispensing    | Notification        |
| transaction.completed    | Transaction | Inventory              | Notification        |
| transaction.failed       | Transaction | None                   | Notification        |
| payment.completed        | Payment     | Transaction            | Notification        |
| payment.failed           | Payment     | Transaction            | Notification        |
| inventory.stock.low      | Inventory   | None                   | Notification        |
| inventory.stock.depleted | Inventory   | None                   | Notification        |
| dispensing.completed     | Dispensing  | Transaction, Inventory | Notification        |
| dispensing.failed        | Dispensing  | Transaction            | Notification        |

---

## MySQL Database Architecture

### Database-per-Service Pattern

```mermaid
---
config:
  theme: base
  themeVariables:
    primaryColor: '#a39cd1ff'
    primaryTextColor: '#000000'
    secondaryTextColor: '#ffffffff'
    primaryBorderColor: '#000000'
    lineColor: '#000000ff'
    secondaryColor: '#90b590ff'
    tertiaryColor: '#f9f6a1ff'
  look: neo
title: 'Infrastructure Services: Database-per-Service Pattern'
---
graph TB
    subgraph "MySQL Server - Port 3306"
        MYSQL[MySQL Server 8.0+]
    end

    subgraph "Databases"
        AUTH_DB[(vending_auth<br/>User authentication)]
        INV_DB[(vending_inventory<br/>Products & Stock)]
        PAY_DB[(vending_payment<br/>Payment transactions)]
        TRANS_DB[(vending_transaction<br/>Purchase transactions)]
        DISP_DB[(vending_dispensing<br/>Dispensing records)]
        NOTIF_DB[(vending_notification<br/>Alerts & notifications)]
    end

    subgraph "Services"
        GATEWAY[API Gateway]
        INVENTORY[Inventory Service]
        PAYMENT[Payment Service]
        TRANSACTION[Transaction Service]
        DISPENSING[Dispensing Service]
        NOTIFICATION[Notification Service]
    end

    MYSQL --> AUTH_DB
    MYSQL --> INV_DB
    MYSQL --> PAY_DB
    MYSQL --> TRANS_DB
    MYSQL --> DISP_DB
    MYSQL --> NOTIF_DB

    GATEWAY <--> AUTH_DB
    INVENTORY <--> INV_DB
    PAYMENT <--> PAY_DB
    TRANSACTION <--> TRANS_DB
    DISPENSING <--> DISP_DB
    NOTIFICATION <--> NOTIF_DB

    style MYSQL fill:#c8e6c9
```

### Schema Management

| Database             | Schema Management | DDL Mode | Manual Scripts       |
| -------------------- | ----------------- | -------- | -------------------- |
| vending_auth         | Manual            | none     | Required             |
| vending_inventory    | Hibernate         | update   | Optional (test data) |
| vending_payment      | Hibernate         | update   | No                   |
| vending_transaction  | Hibernate         | update   | No                   |
| vending_dispensing   | Hibernate         | update   | No                   |
| vending_notification | Hibernate         | update   | No                   |

### Database Initialization Script

```sql
-- scripts/create-databases.sql
CREATE DATABASE IF NOT EXISTS vending_auth;
CREATE DATABASE IF NOT EXISTS vending_inventory;
CREATE DATABASE IF NOT EXISTS vending_payment;
CREATE DATABASE IF NOT EXISTS vending_transaction;
CREATE DATABASE IF NOT EXISTS vending_dispensing;
CREATE DATABASE IF NOT EXISTS vending_notification;

-- Grant privileges
GRANT ALL PRIVILEGES ON vending_auth.* TO 'vending_user'@'localhost';
GRANT ALL PRIVILEGES ON vending_inventory.* TO 'vending_user'@'localhost';
GRANT ALL PRIVILEGES ON vending_payment.* TO 'vending_user'@'localhost';
GRANT ALL PRIVILEGES ON vending_transaction.* TO 'vending_user'@'localhost';
GRANT ALL PRIVILEGES ON vending_dispensing.* TO 'vending_user'@'localhost';
GRANT ALL PRIVILEGES ON vending_notification.* TO 'vending_user'@'localhost';
FLUSH PRIVILEGES;
```

### Connection Pool Configuration

```properties
# Common database configuration for all services
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=30000
spring.datasource.hikari.idle-timeout=600000
spring.datasource.hikari.max-lifetime=1800000
```

---

## Startup Sequence

### Service Dependency Chain

```mermaid
---
config:
  theme: base
  themeVariables:
    primaryColor: '#a39cd1ff'
    primaryTextColor: '#000000'
    secondaryTextColor: '#ffffffff'
    primaryBorderColor: '#000000'
    lineColor: '#000000ff'
    secondaryColor: '#90b590ff'
    tertiaryColor: '#f9f6a1ff'
  look: neo
title: 'Infrastructure Services: Service Dependency Chain'
---
graph TB
    START([System Startup]) --> MYSQL[Start MySQL Server<br/>Port 3306]
    START --> ZOOKEEPER[Start Zookeeper<br/>Port 2181]

    MYSQL --> DB_INIT[Run database scripts<br/>Create 6 databases]
    ZOOKEEPER --> KAFKA[Start Kafka Broker<br/>Port 9092]

    DB_INIT --> CONFIG[Start Config Server<br/>Port 8888]
    CONFIG --> EUREKA[Start Eureka Server<br/>Port 8761]

    EUREKA --> GATEWAY[Start API Gateway<br/>Port 8080]

    GATEWAY --> BUSINESS[Start Business Services<br/>Ports 8081-8085]
    KAFKA --> BUSINESS
    DB_INIT --> BUSINESS

    BUSINESS --> READY([System Ready])

    style CONFIG fill:#c5cae9
    style EUREKA fill:#ffccbc
    style KAFKA fill:#fff9c4
    style MYSQL fill:#c8e6c9
```

### Startup Scripts

#### build.sh

```bash
#!/bin/bash
# Build order: common-library first, then all services
cd common-library && mvn clean install
cd ../config-server && mvn clean package
cd ../eureka-server && mvn clean package
cd ../api-gateway && mvn clean package
cd ../inventory-service && mvn clean package
cd ../payment-service && mvn clean package
cd ../transaction-service && mvn clean package
cd ../dispensing-service && mvn clean package
cd ../notification-service && mvn clean package
```

#### start-services.sh

```bash
#!/bin/bash
# Start services in correct order with delays
java -jar config-server/target/*.jar &
sleep 10

java -jar eureka-server/target/*.jar &
sleep 15

java -jar api-gateway/target/*.jar &
sleep 10

# Business services (parallel start)
java -jar inventory-service/target/*.jar &
java -jar payment-service/target/*.jar &
java -jar transaction-service/target/*.jar &
java -jar dispensing-service/target/*.jar &
java -jar notification-service/target/*.jar &
```

---

## Health Monitoring

### Service Health Endpoints

All services expose: `http://localhost:{port}/actuator/health`

```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "MySQL",
        "validationQuery": "isValid()"
      }
    },
    "diskSpace": {
      "status": "UP"
    },
    "eureka": {
      "status": "UP"
    }
  }
}
```

### Monitoring Dashboard URLs

- **Eureka Dashboard**: <http://localhost:8761>
- **Config Server Health**: <http://localhost:8888/actuator/health>
- **Gateway Health**: <http://localhost:8080/actuator/health>

---

## Conclusion

The infrastructure layer provides essential services for configuration management (Config Server), service discovery (Eureka), asynchronous messaging (Kafka), and data persistence (MySQL). All services follow the database-per-service pattern and communicate via REST and Kafka events.
