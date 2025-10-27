# SRS Diagrams Reference

**Software Requirements Specification - Vending Machine Control System:**

Version: 1.0  
Date: October 23, 2025  
Document Purpose: Maps SRS Section 11 diagram requirements to actual implementation

---

## Table of Contents

- [11.1 System Context Diagram](#111-system-context-diagram)
- [11.2 Service Interaction Sequence Diagrams](#112-service-interaction-sequence-diagrams)
- [11.3 Kafka Topic Flow Diagram](#113-kafka-topic-flow-diagram)
- [11.4 Component Diagram](#114-component-diagram)
- [11.5 Database Entity Relationship Diagram](#115-database-entity-relationship-diagram)
- [11.6 Deployment Diagram (Local Development)](#116-deployment-diagram-local-development)

---

## 11.1 System Context Diagram

**SRS Section**: 11.1  
**Status**: ✅ **IMPLEMENTED**  
**Location**: `Documentation/diagrams/system-overview.md`

### System Context Diagram

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
title: 'System Overview: System Context Diagram'
---
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

**Reference**: For detailed architecture, see [system-overview.md](diagrams/system-overview.md#system-context-diagram)

---

## 11.2 Service Interaction Sequence Diagrams

**SRS Section**: 11.2  
**Status**: ✅ **IMPLEMENTED**

### 11.2.1 Complete Purchase Flow

**Location**: `Documentation/diagrams/system-overview.md`

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
title: 'System Overview: Purchase Flow Sequence'
---
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

**Reference**: [system-overview.md - Purchase Flow](diagrams/system-overview.md#purchase-flow-sequence-diagram)

---

### 11.2.2 Failed Payment Flow

**SRS Requirement**: "Failed payment flow"  
**Status**: ✅ **IMPLEMENTED**  
**Location**: `Documentation/diagrams/transaction-service.md`

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
title: 'Transaction Service: Payment Failure with Compensation'
---
sequenceDiagram
    autonumber
    actor Customer
    participant Service as Transaction Service
    participant Inventory as Inventory Service
    participant Payment as Payment Service
    participant Kafka

    Customer->>Service: Purchase Request

    Service->>Inventory: Check Availability
    Inventory-->>Service: Available

    Service->>Payment: Process Payment
    Payment->>Payment: Simulation Failed
    Payment->>Kafka: Publish payment.failed event
    Payment-->>Service: PAYMENT_FAILED

    Service->>Service: Update Status: FAILED
    Service->>Kafka: Publish transaction.failed event

    Note over Service: No compensation needed<br/>(inventory not yet affected)

    Service-->>Customer: Transaction Failed: Payment Error

    Kafka->>Service: Consume payment.failed
    Note over Service: Log failure for admin review
```

**Reference**: [transaction-service.md - Payment Failure Flow](diagrams/transaction-service.md#payment-failure-flow-with-compensation)

---

### 11.2.3 Failed Dispensing Flow

**SRS Requirement**: "Failed dispensing flow"  
**Status**: ✅ **IMPLEMENTED**  
**Location**: `Documentation/diagrams/transaction-service.md`

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
title: 'Transaction Service: Dispensing Failure with Compensation'
---
sequenceDiagram
    autonumber
    participant Service as Transaction Service
    participant Payment as Payment Service
    participant Dispensing as Dispensing Service
    participant Kafka

    Service->>Dispensing: Dispense Item
    Dispensing->>Dispensing: Simulation: Hardware Fault
    Dispensing->>Kafka: Publish dispensing.failed event
    Dispensing-->>Service: DISPENSING_FAILED

    Service->>Service: Update Status: FAILED
    Service->>Kafka: Publish transaction.failed event

    Note over Service: Compensation Required:<br/>Manual refund processing

    Service->>Kafka: Publish refund.required event

    Kafka->>Service: Consume dispensing.failed
    Service->>Service: Log for manual intervention

    Note over Service: Admin notification created<br/>for manual refund
```

**Reference**: [transaction-service.md - Dispensing Failure Flow](diagrams/transaction-service.md#dispensing-failure-with-compensation)

---

### 11.2.4 Low Stock Alert Flow

**SRS Requirement**: "Low stock alert flow"  
**Status**: ✅ **IMPLEMENTED**  
**Location**: `Documentation/diagrams/inventory-service.md`

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
title: 'Inventory Service: Low Stock Alert Flow'
---
sequenceDiagram
    autonumber
    participant Dispensing as Dispensing Service
    participant Kafka as Kafka Broker
    participant Consumer as Inventory Kafka Consumer
    participant Service as Stock Service
    participant Repo as Stock Repository
    participant Notification as Notification Service

    Dispensing->>Kafka: Publish dispensing.completed<br/>{productId: 5, quantity: 1}

    Kafka->>Consumer: Consume event<br/>group: inventory-group
    Consumer->>Service: processDispensingEvent(event)

    Service->>Repo: findByProductId(5)
    Repo-->>Service: Stock {id: 5, quantity: 6, minThreshold: 5}

    Service->>Service: Update: quantity = 6 - 1 = 5
    Service->>Repo: save(stock)

    Service->>Kafka: Publish stock.updated event

    Service->>Service: Check threshold: 5 <= 5

    alt Stock At or Below Threshold
        Service->>Kafka: Publish stock.low event<br/>{productId: 5, quantity: 5, threshold: 5}

        Kafka->>Notification: Consume stock.low event
        Notification->>Notification: Create notification<br/>Type: LOW_STOCK<br/>Severity: WARNING
        Notification->>Notification: Store in database

        Note over Notification: Admin can view in dashboard
    end
```

**Reference**: [inventory-service.md - Low Stock Alert Flow](diagrams/inventory-service.md#low-stock-alert-flow)

---

### 11.2.5 Admin Operations Flow

**SRS Requirement**: "Admin operations flow"  
**Status**: ✅ **IMPLEMENTED**  
**Location**: `Documentation/diagrams/api-gateway.md`

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
title: 'API Gateway: Authentication Flow'
---
sequenceDiagram
    autonumber
    actor User
    participant Gateway as API Gateway
    participant AuthFilter as JWT Auth Filter
    participant UserService as User Service
    participant JwtUtil as JWT Utility
    participant DB as MySQL Database
    participant Backend as Backend Service

    User->>Gateway: POST /auth/login<br/>{username, password}
    Gateway->>UserService: authenticate(credentials)

    UserService->>DB: findByUsername(username)
    DB-->>UserService: User entity

    UserService->>UserService: Verify BCrypt password

    alt Password Valid
        UserService->>JwtUtil: generateToken(user)
        JwtUtil->>JwtUtil: Create JWT<br/>8hr expiry<br/>Claims: userId, role
        JwtUtil-->>UserService: JWT token
        UserService-->>Gateway: AuthResponse + token
        Gateway-->>User: 200 OK<br/>{token, userId, role}
    else Password Invalid
        UserService-->>Gateway: Authentication failed
        Gateway-->>User: 401 Unauthorized
    end

    Note over User: User makes authenticated request

    User->>Gateway: GET /api/admin/inventory/**<br/>Header: Authorization: Bearer {token}
    Gateway->>AuthFilter: Extract token

    AuthFilter->>JwtUtil: validateToken(token)

    alt Token Valid
        JwtUtil-->>AuthFilter: Claims (userId, role)
        AuthFilter->>AuthFilter: Add headers:<br/>X-User-Id, X-User-Role, X-Username
        AuthFilter->>Backend: Forward request + headers
        Backend-->>Gateway: Response
        Gateway-->>User: 200 OK
    else Token Invalid/Expired
        AuthFilter-->>Gateway: Token validation failed
        Gateway-->>User: 401 Unauthorized
    end
```

**Reference**: [api-gateway.md - Authentication Flow](diagrams/api-gateway.md#authentication-flow)

---

## 11.3 Kafka Topic Flow Diagram

**SRS Section**: 11.3  
**Status**: ✅ **IMPLEMENTED**  
**Location**: `Documentation/diagrams/system-overview.md`

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
title: 'System Overview: Kafka Topic Flow'
---
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

**Reference**: [system-overview.md - Kafka Topic Flow](diagrams/system-overview.md#kafka-topic-flow-diagram)

---

## 11.4 Component Diagram

**SRS Section**: 11.4  
**Status**: ✅ **IMPLEMENTED** (Multiple component diagrams available)

### API Gateway Component Diagram

**Location**: `Documentation/diagrams/api-gateway.md`

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
title: 'API Gateway: Component Diagram'
---
flowchart TB
 subgraph subGraph0["Gateway Filter Chain"]
        PRE_FILTER["Pre-Filters<br>Logging, CORS"]
        AUTH_FILTER["JWT Auth Filter"]
        AUTHZ_FILTER["Authorization Filter"]
        POST_FILTER["Post-Filters<br>Response Headers"]
  end
 subgraph subGraph1["Authentication Module"]
        USER_SERVICE["User Service"]
        JWT_UTIL["JWT Token Util<br>8hr Expiry"]
        PASSWORD_ENCODER["BCrypt Password Encoder"]
  end
 subgraph subGraph2["Authorization Module"]
        ROLE_CHECKER["Role-Based Access Control"]
        PERMISSION_VALIDATOR["Permission Validator"]
  end
 subgraph subGraph3["Routing Module"]
        ROUTE_CONFIG["Route Configuration"]
        LOAD_BALANCER["Load Balancer"]
        EUREKA_CLIENT["Eureka Client"]
  end
 subgraph subGraph4["User Management"]
        USER_CONTROLLER["User Controller<br>CRUD Operations"]
        USER_REPO["User Repository"]
  end
 subgraph subGraph5["Domain Model"]
        USER["User Entity<br>Username, Password, Role"]
        ROLE["Role Enum<br>SUPER_ADMIN, ADMIN"]
  end
    PRE_FILTER --> AUTH_FILTER
    AUTH_FILTER --> USER_SERVICE & JWT_UTIL & AUTHZ_FILTER
    USER_SERVICE --> PASSWORD_ENCODER & USER_REPO
    AUTHZ_FILTER --> ROLE_CHECKER & ROUTE_CONFIG
    ROLE_CHECKER --> PERMISSION_VALIDATOR
    ROUTE_CONFIG --> LOAD_BALANCER & POST_FILTER
    LOAD_BALANCER --> EUREKA_CLIENT
    USER_CONTROLLER --> USER_SERVICE
    USER_REPO <--> USER
    USER --> ROLE
     PRE_FILTER:::Sky
     AUTH_FILTER:::Sky
     AUTHZ_FILTER:::Sky
     POST_FILTER:::Sky
     USER_SERVICE:::Sky
     JWT_UTIL:::Sky
     PASSWORD_ENCODER:::Sky
     ROLE_CHECKER:::Sky
     PERMISSION_VALIDATOR:::Sky
     ROUTE_CONFIG:::Sky
     LOAD_BALANCER:::Sky
     EUREKA_CLIENT:::Sky
     USER_CONTROLLER:::Sky
     USER_REPO:::Sky
     USER:::Sky
     ROLE:::Sky
    classDef Sky stroke-width:1px, stroke-dasharray:none, stroke:#374D7C, fill:#E2EBFF, color:#374D7C
    style AUTH_FILTER fill:#ffccbc
    style AUTHZ_FILTER fill:#ffccbc
    style JWT_UTIL fill:#fff9c4
```

**Additional Component Diagrams Available:**

- [Inventory Service Components](diagrams/inventory-service.md#component-diagram)
- [Payment Service Components](diagrams/payment-service.md#component-diagram)
- [Transaction Service Components](diagrams/transaction-service.md#component-diagram)
- [Dispensing Service Components](diagrams/dispensing-service.md#component-diagram)
- [Notification Service Components](diagrams/notification-service.md#component-diagram)

---

## 11.5 Database Entity Relationship Diagram

**SRS Section**: 11.5  
**Status**: ✅ **IMPLEMENTED** (Per-service ERDs available)

### Consolidated Database Overview

**Database Architecture**: Database-per-service pattern with 6 independent databases

#### 11.5.1 API Gateway Database (vending_auth)

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
title: 'API Gateway: Entity Relationship Diagram'
---
erDiagram
    USERS {
        BIGINT id PK "Auto-increment"
        VARCHAR(50) username UK "Unique username"
        VARCHAR(255) password "BCrypt hashed"
        VARCHAR(20) role "SUPER_ADMIN, ADMIN"
        TIMESTAMP created_at "Registration time"
        TIMESTAMP updated_at "Update time"
    }
```

**Reference**: [api-gateway.md - ERD](diagrams/api-gateway.md#entity-relationship-diagram)

---

#### 11.5.2 Inventory Service Database (vending_inventory)

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
title: 'Inventory Service: Entity Relationship Diagram'
---
erDiagram
    PRODUCT ||--o| STOCK : "has one"
    STOCK }o--|| PRODUCT : "belongs to"

    PRODUCT {
        BIGINT id PK "Auto-increment"
        VARCHAR(255) name "Product name"
        DOUBLE price "Product price"
        VARCHAR(255) category "Product category"
        TEXT description "Product description"
        TIMESTAMP created_at "Creation timestamp"
        TIMESTAMP updated_at "Update timestamp"
    }

    STOCK {
        BIGINT id PK "Auto-increment"
        BIGINT product_id FK "References Product"
        INTEGER quantity "Current quantity"
        INTEGER min_threshold "Low stock threshold"
        TIMESTAMP created_at "Creation timestamp"
        TIMESTAMP updated_at "Update timestamp"
    }

    PROCESSED_EVENTS {
        BIGINT id PK "Auto-increment"
        VARCHAR(255) event_id UK "Unique event ID"
        VARCHAR(100) event_type "Type of event"
        TIMESTAMP processed_at "Processing timestamp"
    }
```

**Reference**: [inventory-service.md - ERD](diagrams/inventory-service.md#entity-relationship-diagram)

---

#### 11.5.3 Payment Service Database (vending_payment)

**Reference**: [payment-service.md - ERD](diagrams/payment-service.md#entity-relationship-diagram)

#### 11.5.4 Transaction Service Database (vending_transaction)

**Reference**: [transaction-service.md - ERD](diagrams/transaction-service.md#entity-relationship-diagram)

#### 11.5.5 Dispensing Service Database (vending_dispensing)

**Reference**: [dispensing-service.md - ERD](diagrams/dispensing-service.md#entity-relationship-diagram)

#### 11.5.6 Notification Service Database (vending_notification)

**Reference**: [notification-service.md - ERD](diagrams/notification-service.md#entity-relationship-diagram)

---

## 11.6 Deployment Diagram (Local Development)

**SRS Section**: 11.6  
**Status**: ✅ **IMPLEMENTED**  
**Location**: `Documentation/diagrams/system-overview.md`

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
title: 'System Overview: Deployment Architecture'
---
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

**Reference**: [system-overview.md - Deployment Diagram](diagrams/system-overview.md#deployment-diagram)

---

## Additional Diagrams (Not in SRS Requirements)

The following diagrams were created beyond SRS requirements for comprehensive documentation:

### Infrastructure Architecture

- **Overall Infrastructure Topology**: [infrastructure-services.md](diagrams/infrastructure-services.md#overall-infrastructure-topology)
- **Config Server Architecture**: [infrastructure-services.md](diagrams/infrastructure-services.md#config-server-architecture)
- **Eureka Server Architecture**: [infrastructure-services.md](diagrams/infrastructure-services.md#eureka-server-architecture)
- **Kafka Architecture**: [infrastructure-services.md](diagrams/infrastructure-services.md#kafka-architecture)
- **Service Startup Sequence**: [infrastructure-services.md](diagrams/infrastructure-services.md#startup-sequence)

### Business Logic Flows

- **SAGA Orchestration Pattern**: [transaction-service.md](diagrams/transaction-service.md#saga-orchestration-pattern)
- **Hardware Simulation Logic**: [dispensing-service.md](diagrams/dispensing-service.md#hardware-simulation-logic)
- **Event Aggregation Flow**: [notification-service.md](diagrams/notification-service.md#event-aggregation-flow)
- **Payment Simulation Logic**: [payment-service.md](diagrams/payment-service.md#payment-simulation-logic)

### Data Flow Diagrams

- **Inventory DFD Level 0 & 1**: [inventory-service.md](diagrams/inventory-service.md#data-flow-diagram)
- **Payment DFD Level 0 & 1**: [payment-service.md](diagrams/payment-service.md#data-flow-diagram)

---

## Diagram Summary

### SRS Requirements Status

| SRS Section | Requirement              | Status | Location                           |
| ----------- | ------------------------ | ------ | ---------------------------------- |
| 11.1        | System Context Diagram   | ✅     | system-overview.md                 |
| 11.2.1      | Complete Purchase Flow   | ✅     | system-overview.md                 |
| 11.2.2      | Failed Payment Flow      | ✅     | transaction-service.md             |
| 11.2.3      | Failed Dispensing Flow   | ✅     | transaction-service.md             |
| 11.2.4      | Low Stock Alert Flow     | ✅     | inventory-service.md               |
| 11.2.5      | Admin Operations Flow    | ✅     | api-gateway.md                     |
| 11.3        | Kafka Topic Flow Diagram | ✅     | system-overview.md                 |
| 11.4        | Component Diagram        | ✅     | All service-specific diagram files |
| 11.5        | Database ERD             | ✅     | All service-specific diagram files |
| 11.6        | Deployment Diagram       | ✅     | system-overview.md                 |

### Total Diagrams Delivered

- **SRS Required Diagrams**: 10 ✅
- **Additional Diagrams**: 40+ (for comprehensive documentation)
- **Total Diagrams**: 50+
- **Documentation Files**: 8 specialized markdown files

---

## Navigation

### Quick Links to Documentation

1. **System Overview**: [system-overview.md](diagrams/system-overview.md)
2. **API Gateway**: [api-gateway.md](diagrams/api-gateway.md)
3. **Inventory Service**: [inventory-service.md](diagrams/inventory-service.md)
4. **Payment Service**: [payment-service.md](diagrams/payment-service.md)
5. **Transaction Service**: [transaction-service.md](diagrams/transaction-service.md)
6. **Dispensing Service**: [dispensing-service.md](diagrams/dispensing-service.md)
7. **Notification Service**: [notification-service.md](diagrams/notification-service.md)
8. **Infrastructure Services**: [infrastructure-services.md](diagrams/infrastructure-services.md)

### Main Documentation Index

For complete diagram catalog and navigation, see: [diagrams/README.md](diagrams/README.md)

---

## Document Revision History

| Version | Date             | Author    | Changes                                    |
| ------- | ---------------- | --------- | ------------------------------------------ |
| 1.0     | October 23, 2025 | Bruno Gil | Initial creation - mapped all SRS diagrams |

---

**Document Status**: ✅ Complete - All SRS Section 11 diagram requirements fulfilled  
**Last Updated**: October 23, 2025  
**Next Review**: As needed for SRS updates
