# Transaction Service Diagrams

## Table of Contents

- [Service Context Diagram](#service-context-diagram)
- [Component Diagram](#component-diagram)
- [SAGA Orchestration Pattern](#saga-orchestration-pattern)
- [Entity Relationship Diagram](#entity-relationship-diagram)
- [Kafka Topic Flow](#kafka-topic-flow)
- [Sequence Diagrams](#sequence-diagrams)

---

## Service Context Diagram

Transaction Service as the central orchestrator coordinating the entire purchase flow.

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
title: 'Transaction Service: Service Context Diagram'
---
graph TB
    subgraph "External Services"
        GATEWAY[API Gateway<br/>Port 8080]
        INVENTORY[Inventory Service<br/>Port 8081]
        PAYMENT[Payment Service<br/>Port 8082]
        DISPENSING[Dispensing Service<br/>Port 8084]
    end

    subgraph "Transaction Service - Port 8083<br/>SAGA Orchestrator"
        CONTROLLER[Transaction Controller<br/>REST Endpoints]
        SERVICE[Transaction Service<br/>Orchestration Logic]
        SAGA[SAGA Coordinator<br/>State Machine]
        REPO[Transaction Repository<br/>JPA]
        KAFKA_PRODUCER[Kafka Producer<br/>Event Publisher]
        KAFKA_CONSUMER[Kafka Consumer<br/>Event Listener]
        INVENTORY_CLIENT[Inventory Client<br/>REST Client]
        PAYMENT_CLIENT[Payment Client<br/>REST Client]
        DISPENSING_CLIENT[Dispensing Client<br/>REST Client]
    end

    subgraph "Data Infrastructure"
        DB[(MySQL Database<br/>vending_transaction)]
        KAFKA[(Kafka Broker<br/>All Topics)]
    end

    GATEWAY -->|POST /purchase| CONTROLLER
    CONTROLLER --> SERVICE

    SERVICE --> SAGA
    SERVICE --> REPO
    SERVICE --> KAFKA_PRODUCER

    SAGA -->|Check Availability| INVENTORY_CLIENT
    INVENTORY_CLIENT -->|Sync REST| INVENTORY

    SAGA -->|Process Payment| PAYMENT_CLIENT
    PAYMENT_CLIENT -->|Sync REST| PAYMENT

    SAGA -->|Dispense Item| DISPENSING_CLIENT
    DISPENSING_CLIENT -->|Sync REST| DISPENSING

    KAFKA_CONSUMER --> SERVICE

    REPO <-->|JDBC| DB
    KAFKA_PRODUCER -->|Publish| KAFKA
    KAFKA -->|Subscribe| KAFKA_CONSUMER

    style CONTROLLER fill:#e8eaf6
    style SERVICE fill:#c5cae9
    style SAGA fill:#ffccbc
    style KAFKA_PRODUCER fill:#fff9c4
    style KAFKA_CONSUMER fill:#fff9c4
```

---

## Component Diagram

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
title: 'Transaction Service: Component Diagram'
---
graph TB
    subgraph "Presentation Layer"
        PUBLIC_API[Public API<br/>/api/transaction/purchase]
        ADMIN_API[Admin API<br/>/api/admin/transaction/*]
    end

    subgraph "Orchestration Layer - SAGA Pattern"
        SAGA_COORDINATOR[SAGA Coordinator<br/>State Machine]
        COMPENSATION_HANDLER[Compensation Handler<br/>Rollback Logic]
    end

    subgraph "Business Logic Layer"
        TRANSACTION_SERVICE[Transaction Service<br/>Purchase Flow]
        VALIDATION[Validation Service<br/>Request Validation]
    end

    subgraph "Integration Layer"
        INVENTORY_CLIENT[Inventory Client<br/>Availability Check]
        PAYMENT_CLIENT[Payment Client<br/>Payment Processing]
        DISPENSING_CLIENT[Dispensing Client<br/>Item Dispensing]
        KAFKA_PRODUCER[Kafka Producer<br/>Events]
        KAFKA_CONSUMER[Kafka Consumer<br/>Status Updates]
    end

    subgraph "Data Access Layer"
        TRANSACTION_REPO[Transaction Repository]
        ITEM_REPO[Transaction Item Repository]
        PROCESSED_EVENT_REPO[Processed Event Repository]
    end

    subgraph "Domain Model"
        TRANSACTION[Transaction Entity<br/>Status State Machine]
        TRANSACTION_ITEM[Transaction Item]
        TRANSACTION_STATUS[Transaction Status Enum<br/>CREATED → COMPLETED]
    end

    PUBLIC_API --> TRANSACTION_SERVICE
    ADMIN_API --> TRANSACTION_SERVICE

    TRANSACTION_SERVICE --> SAGA_COORDINATOR
    SAGA_COORDINATOR --> VALIDATION
    SAGA_COORDINATOR --> INVENTORY_CLIENT
    SAGA_COORDINATOR --> PAYMENT_CLIENT
    SAGA_COORDINATOR --> DISPENSING_CLIENT
    SAGA_COORDINATOR --> COMPENSATION_HANDLER

    TRANSACTION_SERVICE --> TRANSACTION_REPO
    TRANSACTION_SERVICE --> KAFKA_PRODUCER
    KAFKA_CONSUMER --> TRANSACTION_SERVICE

    TRANSACTION_REPO <--> TRANSACTION
    TRANSACTION <--> TRANSACTION_ITEM
    TRANSACTION --> TRANSACTION_STATUS

    style SAGA_COORDINATOR fill:#ffccbc
    style COMPENSATION_HANDLER fill:#ffccbc
```

---

## SAGA Orchestration Pattern

Transaction Service implements orchestration-based SAGA for distributed transaction management.

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
title: 'Transaction Service: SAGA Orchestration Pattern'
---
stateDiagram-v2
    [*] --> CREATED: Purchase Request

    CREATED --> CHECKING_AVAILABILITY: Check Stock
    CHECKING_AVAILABILITY --> PAYMENT_PROCESSING: Stock Available
    CHECKING_AVAILABILITY --> FAILED_INSUFFICIENT_STOCK: Stock Unavailable

    PAYMENT_PROCESSING --> DISPENSING: Payment Success
    PAYMENT_PROCESSING --> FAILED_PAYMENT: Payment Failed

    DISPENSING --> UPDATING_INVENTORY: Dispensing Success
    DISPENSING --> FAILED_DISPENSING: Dispensing Failed

    UPDATING_INVENTORY --> COMPLETED: Stock Updated

    FAILED_INSUFFICIENT_STOCK --> [*]: Return Error
    FAILED_PAYMENT --> [*]: Notify Customer
    FAILED_DISPENSING --> COMPENSATING: Initiate Compensation

    COMPENSATING --> FAILED_COMPENSATED: Refund Issued
    FAILED_COMPENSATED --> [*]: Manual Intervention Required

    COMPLETED --> [*]: Purchase Complete

    note right of CREATED
        Initial transaction created
        Status: CREATED
    end note

    note right of PAYMENT_PROCESSING
        Synchronous call to Payment Service
        Can succeed or fail
    end note

    note right of DISPENSING
        Synchronous call to Dispensing Service
        Hardware operation
    end note

    note right of COMPENSATING
        Compensation: Log for manual refund
        No automatic stock rollback
    end note
```

### SAGA Steps

| Step | Action               | Service Called | Sync/Async  | Compensation               |
| ---- | -------------------- | -------------- | ----------- | -------------------------- |
| 1    | Check Availability   | Inventory      | Sync REST   | None needed                |
| 2    | Process Payment      | Payment        | Sync REST   | Refund (logged)            |
| 3    | Dispense Item        | Dispensing     | Sync REST   | Manual intervention        |
| 4    | Update Stock         | Inventory      | Async Kafka | None (event not published) |
| 5    | Complete Transaction | Self           | Internal    | None                       |

---

## Entity Relationship Diagram

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
title: 'Transaction Service: Entity Relationship Diagram'
---
erDiagram
    TRANSACTIONS ||--o{ TRANSACTION_ITEMS : contains
    TRANSACTION_ITEMS }o--|| TRANSACTIONS : "belongs to"

    TRANSACTIONS {
        BIGINT id PK "Auto-increment"
        DECIMAL total_amount "Total transaction amount"
        VARCHAR(50) payment_method "CASH, CARD"
        DECIMAL paid_amount "Amount paid"
        DECIMAL change_amount "Change returned"
        VARCHAR(50) status "Transaction status"
        TIMESTAMP created_at "Creation time"
        TIMESTAMP updated_at "Update time"
    }

    TRANSACTION_ITEMS {
        BIGINT id PK "Auto-increment"
        BIGINT transaction_id FK "References transaction"
        BIGINT product_id "Product reference"
        INTEGER quantity "Item quantity"
        DECIMAL unit_price "Price per unit"
        TIMESTAMP created_at "Creation time"
    }

    PROCESSED_EVENTS {
        BIGINT id PK "Auto-increment"
        VARCHAR(255) event_id UK "Unique event ID"
        VARCHAR(100) event_type "Event type"
        TIMESTAMP processed_at "Processing time"
    }
```

### Transaction Status Enum

```plaintext
CREATED → PAYMENT_PROCESSING → DISPENSING → COMPLETED
                ↓                    ↓
        FAILED_PAYMENT      FAILED_DISPENSING
                                     ↓
                            FAILED_COMPENSATED
```

---

## Kafka Topic Flow

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
title: 'Transaction Service: Kafka Topic Flow'
---
graph TB
    subgraph "Transaction Service"
        TRANS_SERVICE[Transaction Service]
        KAFKA_PRODUCER[Kafka Producer]
        KAFKA_CONSUMER[Event Consumer]
    end

    subgraph "Kafka Topics"
        TRANS_TOPIC[transaction-events]
        PAY_TOPIC[payment-events]
        DISP_TOPIC[dispensing-events]
    end

    subgraph "Consumers"
        PAYMENT_SVC[Payment Service]
        DISPENSING_SVC[Dispensing Service]
        NOTIFICATION_SVC[Notification Service]
    end

    TRANS_SERVICE -->|transaction.created<br/>transaction.completed<br/>transaction.failed| KAFKA_PRODUCER
    KAFKA_PRODUCER --> TRANS_TOPIC

    TRANS_TOPIC --> PAYMENT_SVC
    TRANS_TOPIC --> DISPENSING_SVC
    TRANS_TOPIC --> NOTIFICATION_SVC

    PAY_TOPIC -->|payment.completed<br/>payment.failed| KAFKA_CONSUMER
    DISP_TOPIC -->|dispensing.completed<br/>dispensing.failed| KAFKA_CONSUMER

    KAFKA_CONSUMER --> TRANS_SERVICE

    style KAFKA_PRODUCER fill:#ffccbc
    style KAFKA_CONSUMER fill:#ffccbc
```

---

## Sequence Diagrams

### Successful Purchase Flow

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
title: 'Transaction Service: Successful Purchase Flow'
---
sequenceDiagram
    autonumber
    actor Customer
    participant Gateway
    participant Controller as Transaction Controller
    participant Service as Transaction Service
    participant Inventory as Inventory Service
    participant Payment as Payment Service
    participant Dispensing as Dispensing Service
    participant Kafka

    Customer->>Gateway: POST /api/transaction/purchase
    Gateway->>Controller: Route request
    Controller->>Service: purchase(request)

    Service->>Service: Create Transaction<br/>Status: CREATED
    Service->>Kafka: Publish transaction.created

    Service->>Inventory: GET /check-availability
    Inventory-->>Service: {available: true}

    Service->>Service: Update Status: PAYMENT_PROCESSING
    Service->>Payment: POST /process
    Payment-->>Service: {status: SUCCESS}

    Service->>Kafka: Listen payment.completed
    Service->>Service: Update Status: DISPENSING

    Service->>Dispensing: POST /dispense
    Dispensing-->>Service: {status: DISPENSED}

    Service->>Kafka: Listen dispensing.completed
    Service->>Service: Update Status: COMPLETED
    Service->>Kafka: Publish transaction.completed

    Service-->>Controller: TransactionDTO
    Controller-->>Gateway: 200 OK
    Gateway-->>Customer: Purchase successful
```

### Payment Failure Flow with Compensation

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
    participant Notification

    Customer->>Service: Purchase request

    Service->>Inventory: Check availability
    Inventory-->>Service: Available

    Service->>Payment: Process payment
    Payment-->>Service: FAILED
    Payment->>Kafka: Publish payment.failed

    Kafka->>Service: Consume payment.failed
    Service->>Service: Update Status: FAILED_PAYMENT
    Service->>Kafka: Publish transaction.failed

    Kafka->>Notification: Consume transaction.failed
    Notification->>Notification: Create alert for admin

    Service-->>Customer: 400 Payment Failed
```

### Dispensing Failure with Compensation

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
    participant Notification

    Service->>Payment: Process payment
    Payment-->>Service: SUCCESS

    Service->>Dispensing: Dispense item
    Dispensing-->>Service: FAILED (Hardware Jam)
    Dispensing->>Kafka: Publish dispensing.failed

    Kafka->>Service: Consume dispensing.failed
    Service->>Service: Update Status: FAILED_DISPENSING

    Note over Service: Compensation Logic
    Service->>Service: Log refund required<br/>(Manual intervention)
    Service->>Service: Update Status: FAILED_COMPENSATED

    Service->>Kafka: Publish transaction.failed
    Kafka->>Notification: Alert admin<br/>Manual refund needed

    Note over Service,Notification: No automatic stock rollback<br/>Stock not updated yet
```

---

## API Endpoints

### Public Endpoints

#### Purchase Transaction

- **Endpoint**: `POST /api/transaction/purchase`
- **Auth**: None
- **Request**:

```json
{
  "productId": 1,
  "quantity": 1,
  "paymentMethod": "CASH",
  "paidAmount": 3.0
}
```

- **Response**:

```json
{
  "id": 100,
  "items": [
    {
      "productId": 1,
      "productName": "Coca Cola 500ml",
      "quantity": 1,
      "unitPrice": 2.5
    }
  ],
  "totalAmount": 2.5,
  "paidAmount": 3.0,
  "changeAmount": 0.5,
  "paymentMethod": "CASH",
  "status": "COMPLETED",
  "createdAt": "2024-01-15T10:30:00Z"
}
```

### Admin Endpoints

#### Get Transaction History

- **Endpoint**: `GET /api/admin/transaction/history`
- **Auth**: JWT (ADMIN or SUPER_ADMIN)

#### Get Transaction Statistics

- **Endpoint**: `GET /api/admin/transaction/statistics`
- **Auth**: JWT (ADMIN or SUPER_ADMIN)

---

## Performance Characteristics

- **End-to-End Purchase**: < 5 seconds
- **Availability Check**: < 200ms
- **Payment Processing**: < 500ms
- **Dispensing**: < 1 second
- **Event Publishing**: < 100ms
- **Database Writes**: 3-5 per transaction

---

## Monitoring & Health Checks

### Custom Metrics

- `transaction.purchase.count` - Total purchases
- `transaction.success.count` - Successful transactions
- `transaction.failure.count` - Failed transactions
- `transaction.duration.avg` - Average completion time
- `transaction.step.inventory.duration` - Inventory check time
- `transaction.step.payment.duration` - Payment processing time
- `transaction.step.dispensing.duration` - Dispensing time

---

## Conclusion

The Transaction Service is the central orchestrator implementing SAGA pattern for distributed transaction management. It coordinates inventory checks, payment processing, and item dispensing while handling failures gracefully with compensation logic.
