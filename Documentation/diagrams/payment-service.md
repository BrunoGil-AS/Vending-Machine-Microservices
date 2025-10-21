# Payment Service Diagrams

## Table of Contents

- [Service Context Diagram](#service-context-diagram)
- [Component Diagram](#component-diagram)
- [Data Flow Diagram](#data-flow-diagram)
- [Entity Relationship Diagram](#entity-relationship-diagram)
- [Kafka Topic Flow](#kafka-topic-flow)
- [Sequence Diagrams](#sequence-diagrams)

---

## Service Context Diagram

Shows how Payment Service interacts with other system components.

```mermaid
graph TB
    subgraph "External Services"
        GATEWAY[API Gateway<br/>Port 8080]
        TRANSACTION[Transaction Service<br/>Port 8083]
    end

    subgraph "Payment Service - Port 8082"
        CONTROLLER[Payment Controller<br/>REST Endpoints]
        SERVICE[Payment Service<br/>Business Logic]
        REPO[Payment Repository<br/>JPA]
        KAFKA_PRODUCER[Kafka Producer<br/>Event Publisher]
        KAFKA_CONSUMER[Kafka Consumer<br/>Event Listener]
        SIMULATOR[Payment Simulator<br/>Success/Failure Logic]
    end

    subgraph "Data Infrastructure"
        DB[(MySQL Database<br/>vending_payment)]
        KAFKA[(Kafka Broker<br/>Topics: payment-events<br/>transaction-events)]
    end

    subgraph "Infrastructure Services"
        EUREKA[Eureka Server<br/>Service Registry]
        CONFIG[Config Server<br/>Configuration]
    end

    GATEWAY -->|Route: /api/payment/*<br/>/api/admin/payment/*| CONTROLLER
    TRANSACTION -->|POST /payment/process| CONTROLLER

    CONTROLLER --> SERVICE
    SERVICE --> REPO
    SERVICE --> SIMULATOR
    SERVICE --> KAFKA_PRODUCER
    KAFKA_CONSUMER --> SERVICE

    REPO <-->|JDBC| DB
    KAFKA_PRODUCER -->|Publish events| KAFKA
    KAFKA -->|Consume transaction events| KAFKA_CONSUMER

    CONTROLLER -.->|Register| EUREKA
    CONTROLLER -.->|Load config| CONFIG

    style CONTROLLER fill:#e8eaf6
    style SERVICE fill:#c5cae9
    style REPO fill:#9fa8da
    style SIMULATOR fill:#ffccbc
    style KAFKA_PRODUCER fill:#fff9c4
    style KAFKA_CONSUMER fill:#fff9c4
    style DB fill:#c8e6c9
    style KAFKA fill:#ffeb3b
```

---

## Component Diagram

Internal architecture showing all components and their relationships.

```mermaid
graph TB
    subgraph "Presentation Layer"
        PUBLIC_API[Public API<br/>/api/payment/*]
        ADMIN_API[Admin API<br/>/api/admin/payment/*]
    end

    subgraph "Business Logic Layer"
        PAYMENT_SERVICE[Payment Service<br/>Process Logic]
        VALIDATION[Validation Service<br/>Amount & Method]
        SIMULATOR[Payment Simulator<br/>Configurable Outcomes]
    end

    subgraph "Integration Layer"
        KAFKA_PRODUCER[Kafka Producer Service<br/>Event Publishing]
        KAFKA_CONSUMER[Kafka Consumer Service<br/>Event Consumption]
    end

    subgraph "Data Access Layer"
        PAYMENT_REPO[Payment Repository<br/>JPA]
        PROCESSED_EVENT_REPO[Processed Event Repository<br/>Idempotency]
    end

    subgraph "Domain Model"
        PAYMENT_TX[Payment Transaction<br/>id, transactionId, amount<br/>method, status]
        PAYMENT_METHOD[Payment Method Enum<br/>CASH, CREDIT_CARD<br/>DEBIT_CARD]
        PROCESSED_EVENT[Processed Event<br/>eventId, eventType]
    end

    PUBLIC_API --> PAYMENT_SERVICE
    ADMIN_API --> PAYMENT_SERVICE

    PAYMENT_SERVICE --> VALIDATION
    PAYMENT_SERVICE --> SIMULATOR
    PAYMENT_SERVICE --> PAYMENT_REPO
    PAYMENT_SERVICE --> KAFKA_PRODUCER

    KAFKA_CONSUMER --> PAYMENT_SERVICE
    KAFKA_CONSUMER --> PROCESSED_EVENT_REPO

    SIMULATOR --> PAYMENT_SERVICE

    PAYMENT_REPO <--> PAYMENT_TX
    PAYMENT_TX --> PAYMENT_METHOD
    PROCESSED_EVENT_REPO <--> PROCESSED_EVENT

    style PUBLIC_API fill:#e3f2fd
    style ADMIN_API fill:#ffebee
    style PAYMENT_SERVICE fill:#e8f5e9
    style SIMULATOR fill:#ffccbc
    style KAFKA_PRODUCER fill:#fff9c4
    style KAFKA_CONSUMER fill:#fff9c4
```

---

## Data Flow Diagram

Flow of data through the Payment Service for key operations.

### DFD Level 0 - Context Level

```mermaid
graph LR
    CUSTOMER[Customer]
    ADMIN[Administrator]
    TRANSACTION_SVC[Transaction Service]
    NOTIFICATION_SVC[Notification Service]

    PAYMENT[Payment Service<br/>Process 0]

    CUSTOMER -->|Payment Request| PAYMENT
    ADMIN -->|View Transactions| PAYMENT
    TRANSACTION_SVC -->|Process Payment| PAYMENT

    PAYMENT -->|Payment Result| CUSTOMER
    PAYMENT -->|Payment Status| TRANSACTION_SVC
    PAYMENT -->|Payment Events| NOTIFICATION_SVC

    style PAYMENT fill:#4caf50
```

### DFD Level 1 - Detailed Processes

```mermaid
graph TB
    subgraph "Inputs"
        INPUT1[Payment Request]
        INPUT2[Transaction Event]
        INPUT3[Refund Request]
    end

    subgraph "Payment Service Processes"
        P1[1.0 Validate Payment<br/>Amount & Method]
        P2[2.0 Simulate Processing<br/>Success/Failure]
        P3[3.0 Record Transaction<br/>Store in Database]
        P4[4.0 Publish Events<br/>Notify Services]
        P5[5.0 Handle Refunds<br/>Compensation]
    end

    subgraph "Data Stores"
        D1[(D1: Payment Transactions)]
        D2[(D2: Processed Events)]
    end

    subgraph "Outputs"
        OUTPUT1[Payment Success]
        OUTPUT2[Payment Failure]
        OUTPUT3[Payment Events]
        OUTPUT4[Transaction History]
    end

    INPUT1 --> P1
    INPUT2 --> P1
    INPUT3 --> P5

    P1 --> P2
    P2 --> P3
    P3 --> D1
    P3 --> P4

    P4 --> OUTPUT3
    P4 --> D2

    P2 --> OUTPUT1
    P2 --> OUTPUT2

    D1 --> OUTPUT4

    style P1 fill:#bbdefb
    style P2 fill:#c5cae9
    style P3 fill:#c8e6c9
    style P4 fill:#fff9c4
    style P5 fill:#ffccbc
```

---

## Entity Relationship Diagram

Database schema for Payment Service (vending_payment database).

```mermaid
erDiagram
    PAYMENT_TRANSACTIONS {
        BIGINT id PK "Auto-increment"
        BIGINT transaction_id "References transaction"
        DOUBLE amount "Payment amount"
        VARCHAR(50) method "CASH, CREDIT_CARD, DEBIT_CARD"
        VARCHAR(50) status "PENDING, SUCCESS, FAILED"
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

### Table Details

#### Payment Transactions Table

- **Primary Key**: `id` (BIGINT, auto-increment)
- **Indexes**:
  - Primary key index on `id`
  - Index on `transaction_id` (for lookup by transaction)
  - Index on `status` (for filtering)
  - Index on `created_at` (for time-based queries)
- **Payment Methods**: CASH, CREDIT_CARD, DEBIT_CARD
- **Status Values**: PENDING, SUCCESS, FAILED

#### Processed Events Table

- **Primary Key**: `id` (BIGINT, auto-increment)
- **Unique Constraints**: `event_id` (prevent duplicate event processing)
- **Purpose**: Idempotency control for Kafka event consumption
- **Indexes**:
  - Primary key index on `id`
  - Unique index on `event_id`

---

## Kafka Topic Flow

Payment Service's interaction with Kafka topics.

```mermaid
graph TB
    subgraph "Payment Service"
        PAYMENT_SERVICE[Payment Service]
        KAFKA_PRODUCER[Kafka Producer]
        KAFKA_CONSUMER[Transaction Event Consumer]
    end

    subgraph "Kafka Topics"
        PAY_TOPIC[payment-events<br/>Partition: 1, Replica: 1]
        TRANS_TOPIC[transaction-events<br/>Partition: 1, Replica: 1]
    end

    subgraph "Event Producers"
        PAYMENT_SERVICE -->|payment.initiated<br/>payment.completed<br/>payment.failed| KAFKA_PRODUCER
    end

    subgraph "Event Consumers"
        TRANSACTION[Transaction Service<br/>group: transaction-group]
        NOTIFICATION[Notification Service<br/>group: notification-group]
        KAFKA_CONSUMER
    end

    KAFKA_PRODUCER -->|Publish| PAY_TOPIC
    PAY_TOPIC -->|Subscribe| TRANSACTION
    PAY_TOPIC -->|Subscribe| NOTIFICATION

    TRANS_TOPIC -->|Subscribe<br/>group: payment-group| KAFKA_CONSUMER
    KAFKA_CONSUMER -->|Trigger Payment| PAYMENT_SERVICE

    style KAFKA_PRODUCER fill:#ffccbc
    style KAFKA_CONSUMER fill:#ffccbc
    style PAY_TOPIC fill:#bbdefb
    style TRANS_TOPIC fill:#c5cae9
```

### Event Schemas

#### Published Events (payment-events topic)

##### payment.initiated

```json
{
  "eventId": "uuid-123",
  "eventType": "PAYMENT_INITIATED",
  "timestamp": "2024-01-15T10:30:00Z",
  "correlationId": "transaction-uuid",
  "payload": {
    "transactionId": 100,
    "amount": 2.5,
    "paymentMethod": "CASH",
    "status": "PENDING"
  }
}
```

##### payment.completed

```json
{
  "eventId": "uuid-456",
  "eventType": "PAYMENT_COMPLETED",
  "timestamp": "2024-01-15T10:30:02Z",
  "correlationId": "transaction-uuid",
  "payload": {
    "transactionId": 100,
    "paymentId": 50,
    "amount": 2.5,
    "paymentMethod": "CASH",
    "status": "SUCCESS",
    "paidAmount": 3.0,
    "changeAmount": 0.5
  }
}
```

##### payment.failed

```json
{
  "eventId": "uuid-789",
  "eventType": "PAYMENT_FAILED",
  "timestamp": "2024-01-15T10:30:02Z",
  "correlationId": "transaction-uuid",
  "payload": {
    "transactionId": 100,
    "amount": 2.5,
    "paymentMethod": "CREDIT_CARD",
    "status": "FAILED",
    "failureReason": "INSUFFICIENT_FUNDS",
    "errorCode": "ERR_PAYMENT_001"
  }
}
```

#### Consumed Events (transaction-events topic)

##### transaction.created

```json
{
  "eventId": "uuid-321",
  "eventType": "TRANSACTION_CREATED",
  "timestamp": "2024-01-15T10:29:58Z",
  "correlationId": "transaction-uuid",
  "payload": {
    "transactionId": 100,
    "totalAmount": 2.5,
    "paymentMethod": "CASH",
    "items": [
      {
        "productId": 1,
        "quantity": 1,
        "unitPrice": 2.5
      }
    ]
  }
}
```

---

## Sequence Diagrams

### Cash Payment Processing Flow

```mermaid
sequenceDiagram
    autonumber
    participant Transaction as Transaction Service
    participant Controller as Payment Controller
    participant Service as Payment Service
    participant Simulator as Payment Simulator
    participant Repo as Payment Repository
    participant DB as MySQL Database
    participant Kafka as Kafka Broker

    Note over Transaction,Kafka: Synchronous Payment Processing
    Transaction->>Controller: POST /api/payment/process<br/>{transactionId, amount, method: CASH, paidAmount}
    Controller->>Service: processPayment(request)

    Service->>Service: Validate amount > 0<br/>Validate paidAmount >= amount
    Service->>Service: Calculate change = paidAmount - amount

    Service->>Simulator: simulatePayment(CASH)
    Simulator->>Simulator: Random success (95% rate)
    Simulator-->>Service: Success

    Service->>Repo: Create PaymentTransaction<br/>{transactionId, amount, CASH, PENDING}
    Repo->>DB: INSERT INTO payment_transactions
    DB-->>Repo: Payment ID

    Service->>Service: Update status to SUCCESS
    Service->>Repo: save(paymentTransaction)
    Repo->>DB: UPDATE payment_transactions SET status = 'SUCCESS'

    Service->>Kafka: Publish payment.completed event<br/>{transactionId, paymentId, amount, change}

    Service-->>Controller: PaymentResponse {status: SUCCESS, change: 0.50}
    Controller-->>Transaction: 200 OK {status: SUCCESS}
```

### Card Payment Processing Flow

```mermaid
sequenceDiagram
    autonumber
    participant Transaction as Transaction Service
    participant Controller as Payment Controller
    participant Service as Payment Service
    participant Simulator as Payment Simulator
    participant Repo as Payment Repository
    participant DB as MySQL
    participant Kafka as Kafka Broker
    participant Notification as Notification Service

    Transaction->>Controller: POST /api/payment/process<br/>{transactionId, amount, method: CREDIT_CARD}
    Controller->>Service: processPayment(request)

    Service->>Service: Validate card payment<br/>(no change for cards)

    Service->>Simulator: simulatePayment(CREDIT_CARD)
    Simulator->>Simulator: Random failure (5% rate)
    Simulator-->>Service: Failure (INSUFFICIENT_FUNDS)

    Service->>Repo: Create PaymentTransaction<br/>{transactionId, amount, CREDIT_CARD, PENDING}
    Repo->>DB: INSERT INTO payment_transactions

    Service->>Service: Update status to FAILED
    Service->>Repo: save(paymentTransaction)
    Repo->>DB: UPDATE payment_transactions SET status = 'FAILED'

    Service->>Kafka: Publish payment.failed event<br/>{transactionId, reason: INSUFFICIENT_FUNDS}

    Kafka->>Notification: Consume payment.failed
    Notification->>Notification: Create admin alert<br/>Type: PAYMENT_FAILED

    Service-->>Controller: PaymentResponse {status: FAILED}
    Controller-->>Transaction: 400 Bad Request {error: PAYMENT_FAILED}
```

### Event-Driven Payment Initiation

```mermaid
sequenceDiagram
    autonumber
    participant Transaction as Transaction Service
    participant Kafka as Kafka Broker
    participant Consumer as Payment Consumer
    participant Service as Payment Service
    participant Repo as Repository

    Transaction->>Kafka: Publish transaction.created event<br/>{transactionId, amount, paymentMethod}

    Kafka->>Consumer: Consume event<br/>group: payment-group
    Consumer->>Repo: Check ProcessedEvent<br/>(prevent duplicate)

    alt Event Not Processed
        Consumer->>Service: initiatePayment(event)

        Service->>Service: Extract payment details<br/>from transaction event

        Service->>Repo: Create PaymentTransaction<br/>status: PENDING
        Repo-->>Service: Payment created

        Service->>Kafka: Publish payment.initiated event

        Service->>Repo: save(ProcessedEvent)

        Note over Service: Payment will be processed<br/>via synchronous call

    else Event Already Processed
        Consumer->>Consumer: Log: Duplicate event ignored
    end
```

---

## API Endpoints

### Public Endpoints

#### Process Payment

- **Endpoint**: `POST /api/payment/process`
- **Auth**: None (called by Transaction Service)
- **Request**:

```json
{
  "transactionId": 100,
  "amount": 2.5,
  "paymentMethod": "CASH",
  "paidAmount": 3.0
}
```

- **Response (Success)**:

```json
{
  "id": 50,
  "amount": 2.5,
  "method": "CASH",
  "status": "SUCCESS",
  "changeAmount": 0.5,
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T10:30:02Z"
}
```

- **Response (Failure)**:

```json
{
  "id": 51,
  "amount": 2.5,
  "method": "CREDIT_CARD",
  "status": "FAILED",
  "failureReason": "INSUFFICIENT_FUNDS",
  "createdAt": "2024-01-15T10:30:00Z"
}
```

#### Process Refund

- **Endpoint**: `POST /api/payment/refund`
- **Auth**: None (internal use)
- **Request**:

```json
{
  "paymentId": 50,
  "reason": "DISPENSING_FAILED"
}
```

### Admin Endpoints

#### Get All Transactions

- **Endpoint**: `GET /api/admin/payment/transactions`
- **Auth**: JWT (ADMIN or SUPER_ADMIN)
- **Response**:

```json
[
  {
    "id": 50,
    "transactionId": 100,
    "amount": 2.5,
    "method": "CASH",
    "status": "SUCCESS",
    "createdAt": "2024-01-15T10:30:00Z",
    "updatedAt": "2024-01-15T10:30:02Z"
  }
]
```

#### Get Payment Statistics

- **Endpoint**: `GET /api/admin/payment/statistics`
- **Auth**: JWT (ADMIN or SUPER_ADMIN)
- **Response**:

```json
{
  "totalPayments": 150,
  "successfulPayments": 142,
  "failedPayments": 8,
  "successRate": 94.67,
  "totalAmount": 375.0,
  "paymentMethods": {
    "CASH": 80,
    "CREDIT_CARD": 50,
    "DEBIT_CARD": 20
  }
}
```

---

## Payment Simulation Logic

### Success Rates (Configurable)

```mermaid
graph TB
    START[Payment Request] --> METHOD{Payment Method?}

    METHOD -->|CASH| CASH_SIM[Cash Simulation<br/>95% Success]
    METHOD -->|CREDIT_CARD| CARD_SIM[Card Simulation<br/>90% Success]
    METHOD -->|DEBIT_CARD| DEBIT_SIM[Debit Simulation<br/>92% Success]

    CASH_SIM --> CASH_OK{Random < 0.95?}
    CARD_SIM --> CARD_OK{Random < 0.90?}
    DEBIT_SIM --> DEBIT_OK{Random < 0.92?}

    CASH_OK -->|Yes| SUCCESS[Status: SUCCESS<br/>Calculate Change]
    CASH_OK -->|No| FAIL[Status: FAILED<br/>Reason: CASH_REJECTED]

    CARD_OK -->|Yes| SUCCESS
    CARD_OK -->|No| CARD_FAIL[Status: FAILED<br/>Reason: INSUFFICIENT_FUNDS]

    DEBIT_OK -->|Yes| SUCCESS
    DEBIT_OK -->|No| DEBIT_FAIL[Status: FAILED<br/>Reason: ACCOUNT_ERROR]

    SUCCESS --> PUBLISH_SUCCESS[Publish payment.completed]
    FAIL --> PUBLISH_FAIL[Publish payment.failed]
    CARD_FAIL --> PUBLISH_FAIL
    DEBIT_FAIL --> PUBLISH_FAIL

    style SUCCESS fill:#c8e6c9
    style FAIL fill:#ffcccc
    style CARD_FAIL fill:#ffcccc
    style DEBIT_FAIL fill:#ffcccc
```

### Failure Scenarios

| Payment Method | Possible Failures                                 | Failure Rate |
| -------------- | ------------------------------------------------- | ------------ |
| CASH           | CASH_REJECTED, INSUFFICIENT_AMOUNT                | 5%           |
| CREDIT_CARD    | INSUFFICIENT_FUNDS, CARD_DECLINED, CARD_EXPIRED   | 10%          |
| DEBIT_CARD     | ACCOUNT_ERROR, INSUFFICIENT_BALANCE, CARD_BLOCKED | 8%           |

---

## Error Handling

### Error Scenarios

1. **Invalid Amount**

   - HTTP Status: 400
   - Response: `{"error": "Amount must be positive", "correlationId": "uuid"}`

2. **Insufficient Paid Amount (Cash)**

   - HTTP Status: 400
   - Response: `{"error": "Paid amount less than total", "required": 2.50, "paid": 2.00}`

3. **Payment Processing Failed**

   - HTTP Status: 400
   - Response: `{"error": "Payment failed", "reason": "INSUFFICIENT_FUNDS", "paymentId": 51}`

4. **Transaction Not Found**
   - HTTP Status: 404
   - Response: `{"error": "Transaction not found", "transactionId": 999}`

---

## Performance Characteristics

- **Payment Processing Time**: < 500ms average
- **Success Rate**: 95% for CASH, 90% for CREDIT_CARD, 92% for DEBIT_CARD
- **Database Writes**: 2 per payment (create + update status)
- **Event Publishing**: < 100ms to Kafka
- **Connection Pool**: HikariCP with 10-20 connections
- **Idempotency**: ProcessedEvent table prevents duplicate processing

---

## Monitoring & Health Checks

### Actuator Endpoints

- `/actuator/health` - Overall service health
- `/actuator/metrics` - Application metrics
- `/actuator/info` - Service information

### Custom Metrics

- `payment.processing.count` - Total payments processed
- `payment.success.count` - Successful payments
- `payment.failure.count` - Failed payments
- `payment.method.cash.count` - Cash payments
- `payment.method.card.count` - Card payments
- `payment.processing.duration` - Average processing time

### Health Indicators

- Database connectivity
- Kafka broker connectivity
- Eureka registration status
- Payment simulator status

---

## Business Rules

### Payment Validation

1. **Amount Validation**: Must be positive (> 0)
2. **Cash Payments**: Paid amount must be >= total amount
3. **Card Payments**: No change calculation (exact amount)
4. **Transaction Association**: Must reference valid transaction ID

### Payment Flow Rules

1. All payments start with status PENDING
2. Successful payments update to SUCCESS
3. Failed payments update to FAILED with reason
4. Events published after status update
5. Idempotent processing via ProcessedEvent table

---

## Conclusion

The Payment Service provides payment processing simulation with configurable success/failure rates for testing. Key features include:

- Multiple payment method support (CASH, CREDIT_CARD, DEBIT_CARD)
- Realistic payment simulation with configurable outcomes
- Change calculation for cash payments
- Event-driven architecture for payment status updates
- Complete audit trail for all payment transactions
- Idempotent event processing for data consistency
- Admin interface for payment monitoring and statistics
