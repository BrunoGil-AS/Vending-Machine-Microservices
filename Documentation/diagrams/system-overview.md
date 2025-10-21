# System Overview - Vending Machine Microservices

## Table of Contents

- [System Context Diagram](#system-context-diagram)
- [Purchase Flow Sequence Diagram](#purchase-flow-sequence-diagram)
- [Kafka Topic Flow Diagram](#kafka-topic-flow-diagram)
- [Deployment Diagram](#deployment-diagram)
- [Service Interaction Map](#service-interaction-map)

---

## System Context Diagram

This diagram shows the high-level system architecture with all services and external dependencies.

```mermaid
graph TB
    subgraph "External Actors"
        CUSTOMER[Customer/User]
        ADMIN[Administrator]
    end

    subgraph "API Layer"
        GATEWAY[API Gateway<br/>Port 8080<br/>JWT Auth]
    end

    subgraph "Infrastructure Services"
        CONFIG[Config Server<br/>Port 8888]
        EUREKA[Eureka Server<br/>Port 8761]
    end

    subgraph "Business Services"
        INVENTORY[Inventory Service<br/>Port 8081]
        PAYMENT[Payment Service<br/>Port 8082]
        TRANSACTION[Transaction Service<br/>Port 8083]
        DISPENSING[Dispensing Service<br/>Port 8084]
        NOTIFICATION[Notification Service<br/>Port 8085]
    end

    subgraph "Data & Messaging Infrastructure"
        MYSQL[(MySQL Database<br/>Port 3306)]
        KAFKA[(Apache Kafka<br/>Port 9092)]
        ZOOKEEPER[(Zookeeper<br/>Port 2181)]
    end

    %% External connections
    CUSTOMER -->|HTTP Requests| GATEWAY
    ADMIN -->|HTTP Requests| GATEWAY

    %% Gateway connections
    GATEWAY -->|Routes| INVENTORY
    GATEWAY -->|Routes| PAYMENT
    GATEWAY -->|Routes| TRANSACTION
    GATEWAY -->|Routes| DISPENSING
    GATEWAY -->|Routes| NOTIFICATION

    %% Config Server connections
    CONFIG -.->|Configuration| EUREKA
    CONFIG -.->|Configuration| GATEWAY
    CONFIG -.->|Configuration| INVENTORY
    CONFIG -.->|Configuration| PAYMENT
    CONFIG -.->|Configuration| TRANSACTION
    CONFIG -.->|Configuration| DISPENSING
    CONFIG -.->|Configuration| NOTIFICATION

    %% Eureka connections
    EUREKA -.->|Service Discovery| GATEWAY
    GATEWAY -.->|Register| EUREKA
    INVENTORY -.->|Register| EUREKA
    PAYMENT -.->|Register| EUREKA
    TRANSACTION -.->|Register| EUREKA
    DISPENSING -.->|Register| EUREKA
    NOTIFICATION -.->|Register| EUREKA

    %% Database connections
    GATEWAY -->|JDBC| MYSQL
    INVENTORY -->|JDBC| MYSQL
    PAYMENT -->|JDBC| MYSQL
    TRANSACTION -->|JDBC| MYSQL
    DISPENSING -->|JDBC| MYSQL
    NOTIFICATION -->|JDBC| MYSQL

    %% Kafka connections
    KAFKA -->|Depends on| ZOOKEEPER
    INVENTORY -->|Pub/Sub| KAFKA
    PAYMENT -->|Pub/Sub| KAFKA
    TRANSACTION -->|Pub/Sub| KAFKA
    DISPENSING -->|Pub/Sub| KAFKA
    NOTIFICATION -->|Subscribe| KAFKA

    style CUSTOMER fill:#e1f5ff
    style ADMIN fill:#fff4e6
    style GATEWAY fill:#ffe6e6
    style CONFIG fill:#f3e5f5
    style EUREKA fill:#f3e5f5
    style MYSQL fill:#e8f5e9
    style KAFKA fill:#fff9c4
    style ZOOKEEPER fill:#fff9c4
```

---

## Purchase Flow Sequence Diagram

Complete sequence diagram showing a successful purchase transaction from customer request to item dispensing.

```mermaid
sequenceDiagram
    autonumber
    actor Customer
    participant Gateway as API Gateway
    participant Transaction as Transaction Service
    participant Inventory as Inventory Service
    participant Payment as Payment Service
    participant Dispensing as Dispensing Service
    participant Kafka
    participant Notification as Notification Service

    Customer->>Gateway: POST /api/transaction/purchase<br/>{productId, quantity, paymentMethod}
    Gateway->>Transaction: Route request (no auth required)

    Note over Transaction: Create Transaction<br/>Status: CREATED
    Transaction->>Kafka: Publish transaction.created event

    Transaction->>Inventory: GET /check-availability<br/>{productId, quantity}
    Inventory-->>Transaction: {available: true}

    alt Stock Available
        Transaction->>Payment: POST /process<br/>{amount, method}
        Payment->>Payment: Simulate payment processing
        Payment->>Kafka: Publish payment.completed event
        Payment-->>Transaction: {status: SUCCESS}

        Transaction->>Kafka: Subscribe payment.completed
        Note over Transaction: Update Status:<br/>PAYMENT_PROCESSING → DISPENSING

        Transaction->>Dispensing: POST /dispense<br/>{productId, quantity}
        Dispensing->>Dispensing: Simulate hardware operation
        Dispensing->>Kafka: Publish dispensing.completed event
        Dispensing-->>Transaction: {status: DISPENSED}

        Kafka->>Inventory: dispensing.completed event
        Inventory->>Inventory: Decrease stock quantity
        Inventory->>Kafka: Publish stock.updated event

        alt Stock Below Threshold
            Inventory->>Kafka: Publish stock.low event
        end

        Transaction->>Kafka: Subscribe dispensing.completed
        Note over Transaction: Update Status:<br/>DISPENSING → COMPLETED

        Transaction->>Kafka: Publish transaction.completed event

        Transaction-->>Gateway: {status: COMPLETED, transactionId}
        Gateway-->>Customer: Purchase successful

        Kafka->>Notification: Consume all events
        Notification->>Notification: Store notifications for admin

    else Stock Not Available
        Transaction-->>Gateway: {error: INSUFFICIENT_STOCK}
        Gateway-->>Customer: Purchase failed
    end
```

---

## Kafka Topic Flow Diagram

Detailed view of Kafka topics and their producer-consumer relationships.

```mermaid
graph LR
    subgraph "Producers"
        TRANS_PROD[Transaction Service]
        PAY_PROD[Payment Service]
        INV_PROD[Inventory Service]
        DISP_PROD[Dispensing Service]
        NOTIF_PROD[Notification Service]
    end

    subgraph "Kafka Topics"
        TRANS_TOPIC[transaction-events<br/>partition: 1<br/>replica: 1]
        PAY_TOPIC[payment-events<br/>partition: 1<br/>replica: 1]
        INV_TOPIC[inventory-events<br/>partition: 1<br/>replica: 1]
        DISP_TOPIC[dispensing-events<br/>partition: 1<br/>replica: 1]
        NOTIF_TOPIC[notification-events<br/>partition: 1<br/>replica: 1]
    end

    subgraph "Consumers"
        TRANS_CONS[Transaction Service]
        PAY_CONS[Payment Service]
        INV_CONS[Inventory Service]
        DISP_CONS[Dispensing Service]
        NOTIF_CONS[Notification Service]
    end

    %% Transaction Events
    TRANS_PROD -->|transaction.created<br/>transaction.completed<br/>transaction.failed| TRANS_TOPIC
    TRANS_TOPIC -->|group: payment-group| PAY_CONS
    TRANS_TOPIC -->|group: dispensing-group| DISP_CONS
    TRANS_TOPIC -->|group: notification-group| NOTIF_CONS

    %% Payment Events
    PAY_PROD -->|payment.initiated<br/>payment.completed<br/>payment.failed| PAY_TOPIC
    PAY_TOPIC -->|group: transaction-group| TRANS_CONS
    PAY_TOPIC -->|group: notification-group| NOTIF_CONS

    %% Inventory Events
    INV_PROD -->|stock.updated<br/>stock.low<br/>product.added| INV_TOPIC
    INV_TOPIC -->|group: notification-group| NOTIF_CONS

    %% Dispensing Events
    DISP_PROD -->|dispensing.requested<br/>dispensing.completed<br/>dispensing.failed| DISP_TOPIC
    DISP_TOPIC -->|group: inventory-group| INV_CONS
    DISP_TOPIC -->|group: transaction-group| TRANS_CONS
    DISP_TOPIC -->|group: notification-group| NOTIF_CONS

    %% Notification Events
    NOTIF_PROD -->|notification.created<br/>notification.sent| NOTIF_TOPIC
    NOTIF_TOPIC -.->|future: email/sms| FUTURE[Future Consumers]

    style TRANS_TOPIC fill:#ffcccc
    style PAY_TOPIC fill:#ccffcc
    style INV_TOPIC fill:#ccccff
    style DISP_TOPIC fill:#ffffcc
    style NOTIF_TOPIC fill:#ffccff
```

### Event Flow Details

| Topic                   | Event Types                                                    | Producers            | Consumers                                                    | Purpose                                 |
| ----------------------- | -------------------------------------------------------------- | -------------------- | ------------------------------------------------------------ | --------------------------------------- |
| **transaction-events**  | transaction.created, transaction.completed, transaction.failed | Transaction Service  | Payment Service, Dispensing Service, Notification Service    | Transaction lifecycle coordination      |
| **payment-events**      | payment.initiated, payment.completed, payment.failed           | Payment Service      | Transaction Service, Notification Service                    | Payment processing results              |
| **inventory-events**    | stock.updated, stock.low, product.added                        | Inventory Service    | Notification Service                                         | Inventory updates and alerts            |
| **dispensing-events**   | dispensing.requested, dispensing.completed, dispensing.failed  | Dispensing Service   | Inventory Service, Transaction Service, Notification Service | Dispensing operations and stock updates |
| **notification-events** | notification.created, notification.sent                        | Notification Service | Future integrations                                          | System-wide notifications               |

---

## Deployment Diagram

Local development deployment architecture showing all components and their network connections.

```mermaid
graph TB
    subgraph "Developer Workstation - localhost"
        subgraph "JVM Processes - Spring Boot Applications"
            CONFIG["Config Server<br/>:8888<br/>Memory: 512MB"]
            EUREKA["Eureka Server<br/>:8761<br/>Memory: 512MB"]
            GATEWAY["API Gateway<br/>:8080<br/>Memory: 512MB"]
            INVENTORY["Inventory Service<br/>:8081<br/>Memory: 768MB"]
            PAYMENT["Payment Service<br/>:8082<br/>Memory: 512MB"]
            TRANSACTION["Transaction Service<br/>:8083<br/>Memory: 768MB"]
            DISPENSING["Dispensing Service<br/>:8084<br/>Memory: 512MB"]
            NOTIFICATION["Notification Service<br/>:8085<br/>Memory: 512MB"]
        end

        subgraph "Database Server"
            MYSQL["MySQL Server<br/>:3306<br/>Memory: 1GB<br/><br/>Databases:<br/>- vending_auth<br/>- vending_inventory<br/>- vending_payment<br/>- vending_transaction<br/>- vending_dispensing<br/>- vending_notification"]
        end

        subgraph "Message Broker"
            ZOOKEEPER["Zookeeper<br/>:2181<br/>Memory: 512MB"]
            KAFKA["Kafka Broker<br/>:9092<br/>Memory: 1GB<br/><br/>Topics:<br/>- transaction-events<br/>- payment-events<br/>- inventory-events<br/>- dispensing-events<br/>- notification-events"]
        end

        subgraph "File System"
            CONFIG_FILES["Configuration Files<br/>config-server/src/main/resources/config/"]
            LOG_FILES["Log Files<br/>logs/*.log"]
        end
    end

    %% Startup dependencies
    MYSQL -.->|Must start first| CONFIG
    ZOOKEEPER -.->|Must start before Kafka| KAFKA
    CONFIG -.->|Provides config| EUREKA
    CONFIG -.->|Provides config| GATEWAY
    CONFIG -.->|Provides config| INVENTORY
    CONFIG -.->|Provides config| PAYMENT
    CONFIG -.->|Provides config| TRANSACTION
    CONFIG -.->|Provides config| DISPENSING
    CONFIG -.->|Provides config| NOTIFICATION

    %% Service dependencies
    EUREKA -.->|Service discovery| GATEWAY
    GATEWAY -.->|Routes to| INVENTORY
    GATEWAY -.->|Routes to| PAYMENT
    GATEWAY -.->|Routes to| TRANSACTION
    GATEWAY -.->|Routes to| DISPENSING
    GATEWAY -.->|Routes to| NOTIFICATION

    %% Database connections
    GATEWAY -->|HikariCP pool| MYSQL
    INVENTORY -->|HikariCP pool| MYSQL
    PAYMENT -->|HikariCP pool| MYSQL
    TRANSACTION -->|HikariCP pool| MYSQL
    DISPENSING -->|HikariCP pool| MYSQL
    NOTIFICATION -->|HikariCP pool| MYSQL

    %% Kafka connections
    INVENTORY -->|Pub/Sub| KAFKA
    PAYMENT -->|Pub/Sub| KAFKA
    TRANSACTION -->|Pub/Sub| KAFKA
    DISPENSING -->|Pub/Sub| KAFKA
    NOTIFICATION -->|Subscribe| KAFKA
    KAFKA -->|Coordination| ZOOKEEPER

    %% File system access
    CONFIG -->|Reads| CONFIG_FILES
    CONFIG -->|Writes| LOG_FILES
    EUREKA -->|Writes| LOG_FILES
    GATEWAY -->|Writes| LOG_FILES
    INVENTORY -->|Writes| LOG_FILES
    PAYMENT -->|Writes| LOG_FILES
    TRANSACTION -->|Writes| LOG_FILES
    DISPENSING -->|Writes| LOG_FILES
    NOTIFICATION -->|Writes| LOG_FILES

    style MYSQL fill:#4caf50
    style KAFKA fill:#ffeb3b
    style ZOOKEEPER fill:#ffc107
    style CONFIG fill:#9c27b0
    style EUREKA fill:#673ab7
    style GATEWAY fill:#f44336
```

### System Requirements

| Component            | Memory   | Disk     | CPU    | Network   |
| -------------------- | -------- | -------- | ------ | --------- |
| Config Server        | 512MB    | 50MB     | Low    | localhost |
| Eureka Server        | 512MB    | 50MB     | Low    | localhost |
| API Gateway          | 512MB    | 100MB    | Medium | localhost |
| Inventory Service    | 768MB    | 200MB    | Medium | localhost |
| Payment Service      | 512MB    | 100MB    | Low    | localhost |
| Transaction Service  | 768MB    | 200MB    | Medium | localhost |
| Dispensing Service   | 512MB    | 100MB    | Low    | localhost |
| Notification Service | 512MB    | 100MB    | Low    | localhost |
| MySQL Server         | 1GB      | 500MB    | Medium | localhost |
| Kafka + Zookeeper    | 1.5GB    | 1GB      | Medium | localhost |
| **Total**            | **~6GB** | **~2GB** | -      | -         |

---

## Service Interaction Map

High-level view of service communication patterns (synchronous vs asynchronous).

```mermaid
graph TB
    subgraph "Client Layer"
        CLIENT[Customer/Admin<br/>Web Interface]
    end

    subgraph "Gateway Layer"
        GATEWAY[API Gateway<br/>JWT Auth & Routing]
    end

    subgraph "Business Logic Layer"
        TRANSACTION[Transaction Service<br/>Orchestrator]
        INVENTORY[Inventory Service<br/>Stock Management]
        PAYMENT[Payment Service<br/>Payment Processing]
        DISPENSING[Dispensing Service<br/>Hardware Control]
        NOTIFICATION[Notification Service<br/>Alert Aggregation]
    end

    subgraph "Event Bus"
        KAFKA[(Apache Kafka<br/>Event Streaming)]
    end

    %% HTTP Synchronous calls (solid lines)
    CLIENT -->|HTTP/REST| GATEWAY
    GATEWAY -->|HTTP| TRANSACTION
    GATEWAY -->|HTTP| INVENTORY
    GATEWAY -->|HTTP| PAYMENT
    GATEWAY -->|HTTP| DISPENSING
    GATEWAY -->|HTTP| NOTIFICATION

    TRANSACTION -->|Sync REST<br/>checkAvailability| INVENTORY
    TRANSACTION -->|Sync REST<br/>processPayment| PAYMENT
    TRANSACTION -->|Sync REST<br/>dispenseItem| DISPENSING

    %% Kafka Asynchronous events (dashed lines)
    TRANSACTION -.->|Async Event<br/>transaction.*| KAFKA
    PAYMENT -.->|Async Event<br/>payment.*| KAFKA
    INVENTORY -.->|Async Event<br/>inventory.*| KAFKA
    DISPENSING -.->|Async Event<br/>dispensing.*| KAFKA
    NOTIFICATION -.->|Async Event<br/>notification.*| KAFKA

    KAFKA -.->|Event Consumer| TRANSACTION
    KAFKA -.->|Event Consumer| PAYMENT
    KAFKA -.->|Event Consumer| INVENTORY
    KAFKA -.->|Event Consumer| DISPENSING
    KAFKA -.->|Event Consumer| NOTIFICATION

    style CLIENT fill:#e3f2fd
    style GATEWAY fill:#ffebee
    style TRANSACTION fill:#fff9c4
    style INVENTORY fill:#f3e5f5
    style PAYMENT fill:#e8f5e9
    style DISPENSING fill:#fce4ec
    style NOTIFICATION fill:#e0f2f1
    style KAFKA fill:#fff3e0
```

### Communication Patterns

#### Synchronous Communication (HTTP/REST)

- **Use Case**: Real-time transactions requiring immediate response
- **Pattern**: Request-Response
- **Examples**:
  - Customer purchase initiation
  - Inventory availability check
  - Payment processing
  - Dispensing request
  - Admin operations

#### Asynchronous Communication (Kafka Events)

- **Use Case**: State updates, notifications, eventual consistency
- **Pattern**: Publish-Subscribe
- **Examples**:
  - Stock level updates
  - Payment completion notifications
  - Transaction status changes
  - Low stock alerts
  - Dispensing completion

### Service Responsibilities

| Service                  | Primary Responsibility            | Communication Pattern       |
| ------------------------ | --------------------------------- | --------------------------- |
| **API Gateway**          | Authentication, Routing, Security | Synchronous (HTTP)          |
| **Transaction Service**  | Purchase orchestration            | Sync + Async (Orchestrator) |
| **Inventory Service**    | Product & stock management        | Sync + Async                |
| **Payment Service**      | Payment processing                | Sync + Async                |
| **Dispensing Service**   | Hardware simulation               | Sync + Async                |
| **Notification Service** | Alert aggregation                 | Async only (Event-driven)   |

---

## Authentication Flow Diagram

JWT-based authentication and authorization flow at API Gateway level.

```mermaid
sequenceDiagram
    autonumber
    actor Admin
    participant Gateway as API Gateway
    participant DB as MySQL (vending_auth)
    participant Service as Business Service

    Note over Admin,Gateway: Login Process
    Admin->>Gateway: POST /api/auth/login<br/>{username, password}
    Gateway->>DB: Query admin_users table
    DB-->>Gateway: User record (hashed password)
    Gateway->>Gateway: Verify BCrypt password

    alt Credentials Valid
        Gateway->>Gateway: Generate JWT token<br/>(8-hour expiry)
        Gateway-->>Admin: {token, expiresIn: 28800}

        Note over Admin,Service: Authenticated Request
        Admin->>Gateway: GET /api/admin/inventory/products<br/>Authorization: Bearer {token}
        Gateway->>Gateway: Validate JWT signature & expiry
        Gateway->>Gateway: Extract user context<br/>(userId, role, username)

        alt Token Valid & Role Authorized
            Gateway->>Service: Forward request with headers:<br/>X-User-Id, X-User-Role, X-Username
            Service->>Service: Process business logic<br/>(trust gateway headers)
            Service-->>Gateway: Response
            Gateway-->>Admin: Response with data
        else Insufficient Role
            Gateway-->>Admin: 403 Forbidden<br/>{error: "Insufficient permissions"}
        end

    else Invalid Credentials
        Gateway-->>Admin: 401 Unauthorized<br/>{error: "Invalid credentials"}
    end

    Note over Admin,Gateway: Token Expiry (after 8 hours)
    Admin->>Gateway: Request with expired token
    Gateway->>Gateway: Validate JWT
    Gateway-->>Admin: 401 Unauthorized<br/>{error: "Token expired"}
```

### Role-Based Access Matrix

| Endpoint Pattern              | SUPER_ADMIN | ADMIN | Public |
| ----------------------------- | ----------- | ----- | ------ |
| `/api/auth/login`             | ✓           | ✓     | ✓      |
| `/api/admin/users/**`         | ✓ (manage)  | ✗     | ✗      |
| `/api/admin/inventory/**`     | ✓           | ✓     | ✗      |
| `/api/admin/payment/**`       | ✓           | ✓     | ✗      |
| `/api/admin/transaction/**`   | ✓           | ✓     | ✗      |
| `/api/admin/dispensing/**`    | ✓           | ✓     | ✗      |
| `/api/admin/notifications/**` | ✓           | ✓     | ✗      |
| `/api/inventory/products`     | ✓           | ✓     | ✓      |
| `/api/transaction/purchase`   | ✓           | ✓     | ✓      |

---

## Error Flow Diagram

Error handling and recovery patterns across services.

```mermaid
graph TD
    START[Purchase Request] --> CHECK_STOCK{Stock Available?}

    CHECK_STOCK -->|No| ERROR_STOCK[Return 400<br/>INSUFFICIENT_STOCK]
    CHECK_STOCK -->|Yes| PROCESS_PAYMENT[Process Payment]

    PROCESS_PAYMENT --> PAYMENT_OK{Payment Success?}
    PAYMENT_OK -->|No| ERROR_PAYMENT[Return 400<br/>PAYMENT_FAILED]
    PAYMENT_OK -->|Yes| DISPENSE[Dispense Item]

    DISPENSE --> DISPENSE_OK{Dispensing Success?}
    DISPENSE_OK -->|No| ERROR_DISPENSE[Compensation:<br/>Log for manual refund]
    DISPENSE_OK -->|Yes| UPDATE_STOCK[Update Stock via Kafka]

    UPDATE_STOCK --> CHECK_LOW{Stock Below<br/>Threshold?}
    CHECK_LOW -->|Yes| ALERT[Publish Low Stock Alert]
    CHECK_LOW -->|No| SUCCESS[Return 200<br/>PURCHASE_COMPLETED]

    ALERT --> SUCCESS

    ERROR_STOCK --> NOTIFY1[Notification Service]
    ERROR_PAYMENT --> NOTIFY2[Notification Service]
    ERROR_DISPENSE --> NOTIFY3[Notification Service]

    NOTIFY1 --> ADMIN_DASHBOARD[Admin Dashboard]
    NOTIFY2 --> ADMIN_DASHBOARD
    NOTIFY3 --> ADMIN_DASHBOARD

    style ERROR_STOCK fill:#ffcccc
    style ERROR_PAYMENT fill:#ffcccc
    style ERROR_DISPENSE fill:#ffcccc
    style SUCCESS fill:#ccffcc
    style ALERT fill:#ffffcc
```

---

## Performance Monitoring Architecture

```mermaid
graph TB
    subgraph "Application Layer"
        SERVICES[All Microservices]
    end

    subgraph "Monitoring Instrumentation"
        ACTUATOR[Spring Boot Actuator]
        MICROMETER[Micrometer Metrics]
        LOGGING[SLF4J + Logback]
    end

    subgraph "Metrics Collection"
        PROMETHEUS[Prometheus Server<br/>Future Integration]
        LOG_AGGREGATOR[Log Aggregation<br/>Future: ELK Stack]
    end

    subgraph "Visualization"
        GRAFANA[Grafana Dashboards<br/>Future Integration]
        EUREKA_UI[Eureka Dashboard<br/>:8761]
    end

    subgraph "Current Monitoring"
        HEALTH[Health Endpoints<br/>/actuator/health]
        METRICS[Metrics Endpoints<br/>/actuator/metrics]
        LOGS[Log Files<br/>logs/*.log]
    end

    SERVICES --> ACTUATOR
    SERVICES --> MICROMETER
    SERVICES --> LOGGING

    ACTUATOR --> HEALTH
    ACTUATOR --> METRICS
    MICROMETER --> PROMETHEUS
    LOGGING --> LOGS
    LOGGING --> LOG_AGGREGATOR

    PROMETHEUS -.-> GRAFANA
    EUREKA_UI --> SERVICES

    style HEALTH fill:#ccffcc
    style METRICS fill:#ccffcc
    style LOGS fill:#ccffcc
    style PROMETHEUS fill:#ffffcc
    style GRAFANA fill:#ffffcc
    style LOG_AGGREGATOR fill:#ffffcc
```

---

## Conclusion

This system overview provides comprehensive diagrams for understanding the complete Vending Machine Microservices architecture. Key takeaways:

1. **Hybrid Communication**: Synchronous HTTP for real-time operations, asynchronous Kafka for eventual consistency
2. **Centralized Security**: JWT authentication at API Gateway with role-based access control
3. **Event-Driven Architecture**: Kafka enables loose coupling and scalability
4. **Service Independence**: Each service owns its data and business logic
5. **Observability**: Comprehensive monitoring through Actuator, logs, and metrics

For service-specific details, refer to individual service diagram documents in this directory.
