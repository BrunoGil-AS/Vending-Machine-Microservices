# Dispensing Service Diagrams

## Table of Contents

- [Service Context Diagram](#service-context-diagram)
- [Component Diagram](#component-diagram)
- [Hardware Simulation Logic](#hardware-simulation-logic)
- [Entity Relationship Diagram](#entity-relationship-diagram)
- [Kafka Topic Flow](#kafka-topic-flow)
- [Sequence Diagrams](#sequence-diagrams)

---

## Service Context Diagram

```mermaid
graph TB
    subgraph "External Services"
        TRANSACTION[Transaction Service<br/>Port 8083]
        INVENTORY[Inventory Service<br/>Port 8081]
    end

    subgraph "Dispensing Service - Port 8084<br/>Hardware Simulation"
        CONTROLLER[Dispensing Controller<br/>REST Endpoints]
        SERVICE[Dispensing Service<br/>Business Logic]
        SIMULATOR[Hardware Simulator<br/>Configurable Failures]
        REPO[Dispensing Repository<br/>JPA]
        KAFKA_PRODUCER[Kafka Producer<br/>Event Publisher]
        KAFKA_CONSUMER[Kafka Consumer<br/>Transaction Events]
    end

    subgraph "Data Infrastructure"
        DB[(MySQL Database<br/>vending_dispensing)]
        KAFKA[(Kafka Broker<br/>dispensing-events)]
    end

    TRANSACTION -->|POST /dispense| CONTROLLER
    CONTROLLER --> SERVICE

    SERVICE --> SIMULATOR
    SERVICE --> REPO
    SERVICE --> KAFKA_PRODUCER

    KAFKA -->|Subscribe transaction.completed| KAFKA_CONSUMER
    KAFKA_CONSUMER --> SERVICE

    REPO <-->|JDBC| DB
    KAFKA_PRODUCER -->|Publish dispensing.*| KAFKA
    KAFKA -->|Consume| INVENTORY

    style SIMULATOR fill:#ffccbc
    style KAFKA_PRODUCER fill:#fff9c4
    style KAFKA_CONSUMER fill:#fff9c4
```

---

## Component Diagram

```mermaid
graph TB
    subgraph "API Layer"
        PUBLIC_API[Public API<br/>/api/dispensing/dispense]
        ADMIN_API[Admin API<br/>/api/admin/dispensing/*]
    end

    subgraph "Business Logic Layer"
        DISPENSING_SERVICE[Dispensing Service<br/>Core Logic]
        VALIDATION[Request Validator]
    end

    subgraph "Simulation Layer"
        HARDWARE_SIMULATOR[Hardware Simulator<br/>Configurable Failures]
        MOTOR_CONTROLLER[Motor Controller Simulation]
        SENSOR_MONITOR[Sensor Monitor Simulation]
    end

    subgraph "Integration Layer"
        KAFKA_PRODUCER[Kafka Producer<br/>Status Events]
        KAFKA_CONSUMER[Kafka Consumer<br/>Transaction Events]
    end

    subgraph "Data Access Layer"
        DISPENSING_REPO[Dispensing Repository]
        PROCESSED_EVENT_REPO[Processed Event Repository]
    end

    subgraph "Domain Model"
        DISPENSING[Dispensing Entity<br/>Status Tracking]
        DISPENSING_STATUS[DispensingStatus Enum<br/>PENDING → DISPENSED]
    end

    PUBLIC_API --> DISPENSING_SERVICE
    ADMIN_API --> DISPENSING_SERVICE

    DISPENSING_SERVICE --> VALIDATION
    DISPENSING_SERVICE --> HARDWARE_SIMULATOR
    DISPENSING_SERVICE --> DISPENSING_REPO
    DISPENSING_SERVICE --> KAFKA_PRODUCER

    KAFKA_CONSUMER --> DISPENSING_SERVICE

    HARDWARE_SIMULATOR --> MOTOR_CONTROLLER
    HARDWARE_SIMULATOR --> SENSOR_MONITOR

    DISPENSING_REPO <--> DISPENSING
    DISPENSING --> DISPENSING_STATUS

    style HARDWARE_SIMULATOR fill:#ffccbc
    style MOTOR_CONTROLLER fill:#ffccbc
    style SENSOR_MONITOR fill:#ffccbc
```

---

## Hardware Simulation Logic

```mermaid
flowchart TD
    START([Dispense Request]) --> VALIDATE{Valid Request?}
    VALIDATE -->|No| ERROR1[Return Validation Error]
    VALIDATE -->|Yes| CREATE[Create Dispensing Record<br/>Status: PENDING]

    CREATE --> MOTOR_CHECK{Motor Status?}
    MOTOR_CHECK -->|Fault| ERROR2[Update Status: FAILED<br/>Reason: MOTOR_FAULT]
    MOTOR_CHECK -->|OK| SENSOR_CHECK{Sensor Status?}

    SENSOR_CHECK -->|Fault| ERROR3[Update Status: FAILED<br/>Reason: SENSOR_FAULT]
    SENSOR_CHECK -->|OK| JAM_CHECK{Jam Check<br/>95% Success Rate}

    JAM_CHECK -->|Jammed 5%| ERROR4[Update Status: FAILED<br/>Reason: PRODUCT_JAM]
    JAM_CHECK -->|Clear 95%| DISPENSE[Simulate Motor Rotation<br/>Wait 500-1000ms]

    DISPENSE --> VERIFY{Product Dispensed?}
    VERIFY -->|No| ERROR5[Update Status: FAILED<br/>Reason: DISPENSE_VERIFICATION_FAILED]
    VERIFY -->|Yes| SUCCESS[Update Status: DISPENSED<br/>Timestamp: now()]

    SUCCESS --> PUBLISH[Publish dispensing.completed<br/>to Kafka]

    ERROR2 --> PUBLISH_FAIL[Publish dispensing.failed]
    ERROR3 --> PUBLISH_FAIL
    ERROR4 --> PUBLISH_FAIL
    ERROR5 --> PUBLISH_FAIL

    PUBLISH --> END([Return Success])
    PUBLISH_FAIL --> END_FAIL([Return Failure])
    ERROR1 --> END_FAIL

    style JAM_CHECK fill:#ffccbc
    style DISPENSE fill:#c5cae9
    style SUCCESS fill:#c8e6c9
    style ERROR2 fill:#ffcdd2
    style ERROR3 fill:#ffcdd2
    style ERROR4 fill:#ffcdd2
    style ERROR5 fill:#ffcdd2
```

### Failure Scenarios

| Failure Type        | Probability | Detection        | Recovery            |
| ------------------- | ----------- | ---------------- | ------------------- |
| Motor Fault         | 1%          | Pre-check        | Manual intervention |
| Sensor Fault        | 1%          | Pre-check        | Manual intervention |
| Product Jam         | 3%          | During dispense  | Manual clearing     |
| Verification Failed | 0.5%        | Post-dispense    | Manual verification |
| Success             | 94.5%       | Normal operation | N/A                 |

---

## Entity Relationship Diagram

```mermaid
erDiagram
    DISPENSING_RECORDS {
        BIGINT id PK "Auto-increment"
        BIGINT transaction_id "Reference to transaction"
        BIGINT product_id "Product to dispense"
        INTEGER quantity "Quantity to dispense"
        VARCHAR(50) status "Dispensing status"
        VARCHAR(255) error_message "Failure reason"
        TIMESTAMP dispensed_at "Completion timestamp"
        TIMESTAMP created_at "Creation time"
        TIMESTAMP updated_at "Update time"
    }

    PROCESSED_EVENTS {
        BIGINT id PK "Auto-increment"
        VARCHAR(255) event_id UK "Unique event ID"
        VARCHAR(100) event_type "Event type"
        TIMESTAMP processed_at "Processing time"
    }
```

### Dispensing Status Enum

```java
PENDING → IN_PROGRESS → DISPENSED
              ↓
            FAILED (MOTOR_FAULT, SENSOR_FAULT, PRODUCT_JAM, VERIFICATION_FAILED)
```

---

## Kafka Topic Flow

```mermaid
graph TB
    subgraph "Dispensing Service"
        DISP_SERVICE[Dispensing Service]
        KAFKA_PRODUCER[Kafka Producer]
        KAFKA_CONSUMER[Event Consumer]
    end

    subgraph "Kafka Topics"
        TRANS_TOPIC[transaction-events]
        DISP_TOPIC[dispensing-events]
    end

    subgraph "Event Consumers"
        TRANSACTION_SVC[Transaction Service]
        INVENTORY_SVC[Inventory Service]
        NOTIFICATION_SVC[Notification Service]
    end

    TRANS_TOPIC -->|transaction.completed| KAFKA_CONSUMER
    KAFKA_CONSUMER --> DISP_SERVICE

    DISP_SERVICE -->|dispensing.completed<br/>dispensing.failed<br/>dispensing.hardware.fault| KAFKA_PRODUCER
    KAFKA_PRODUCER --> DISP_TOPIC

    DISP_TOPIC --> TRANSACTION_SVC
    DISP_TOPIC --> INVENTORY_SVC
    DISP_TOPIC --> NOTIFICATION_SVC

    style KAFKA_PRODUCER fill:#ffccbc
    style KAFKA_CONSUMER fill:#ffccbc
```

---

## Sequence Diagrams

### Successful Dispensing Flow

```mermaid
sequenceDiagram
    autonumber
    actor Transaction as Transaction Service
    participant Controller as Dispensing Controller
    participant Service as Dispensing Service
    participant Simulator as Hardware Simulator
    participant Repo as Dispensing Repository
    participant Kafka

    Transaction->>Controller: POST /api/dispensing/dispense
    Controller->>Service: dispense(request)

    Service->>Service: Validate request
    Service->>Repo: Create PENDING record

    Service->>Simulator: checkMotorStatus()
    Simulator-->>Service: OK

    Service->>Simulator: checkSensorStatus()
    Simulator-->>Service: OK

    Service->>Simulator: simulateDispensing()
    Simulator->>Simulator: Generate random (95% success)
    Simulator->>Simulator: Wait 500-1000ms (motor rotation)
    Simulator-->>Service: SUCCESS

    Service->>Repo: Update status: DISPENSED
    Service->>Kafka: Publish dispensing.completed

    Service-->>Controller: DispensingDTO
    Controller-->>Transaction: 200 OK
```

### Hardware Fault Flow

```mermaid
sequenceDiagram
    autonumber
    participant Service as Dispensing Service
    participant Simulator as Hardware Simulator
    participant Repo as Dispensing Repository
    participant Kafka
    participant Notification

    Service->>Simulator: checkMotorStatus()
    Simulator-->>Service: MOTOR_FAULT

    Service->>Repo: Update status: FAILED<br/>errorMessage: "Motor fault detected"
    Service->>Kafka: Publish dispensing.failed

    Kafka->>Notification: Alert admin<br/>Hardware maintenance required

    Service-->>Service: Return error response
```

### Product Jam Recovery

```mermaid
sequenceDiagram
    autonumber
    participant Service as Dispensing Service
    participant Simulator as Hardware Simulator
    participant Repo as Dispensing Repository
    participant Kafka
    participant Transaction

    Service->>Simulator: simulateDispensing()
    Simulator->>Simulator: Random check (5% jam probability)
    Simulator-->>Service: PRODUCT_JAM

    Service->>Repo: Update status: FAILED<br/>errorMessage: "Product jammed in dispenser"
    Service->>Kafka: Publish dispensing.failed

    Kafka->>Transaction: Consume failure event
    Transaction->>Transaction: Initiate compensation<br/>Mark for refund

    Note over Service,Transaction: Manual intervention required<br/>Admin must clear jam
```

---

## API Endpoints

### Public Endpoints

#### Dispense Item

- **Endpoint**: `POST /api/dispensing/dispense`
- **Auth**: Called by Transaction Service
- **Request**:

```json
{
  "transactionId": 100,
  "productId": 1,
  "quantity": 1
}
```

- **Response**:

```json
{
  "id": 50,
  "transactionId": 100,
  "productId": 1,
  "quantity": 1,
  "status": "DISPENSED",
  "dispensedAt": "2024-01-15T10:30:05Z"
}
```

### Admin Endpoints

#### Get Dispensing History

- **Endpoint**: `GET /api/admin/dispensing/history`
- **Auth**: JWT (ADMIN or SUPER_ADMIN)

#### Get Hardware Status

- **Endpoint**: `GET /api/admin/dispensing/hardware-status`
- **Auth**: JWT (ADMIN or SUPER_ADMIN)

#### Configure Failure Rate

- **Endpoint**: `POST /api/admin/dispensing/configure-failure-rate`
- **Auth**: JWT (SUPER_ADMIN)

---

## Performance Characteristics

- **Average Dispensing Time**: 750ms (500-1000ms)
- **Success Rate**: 94.5%
- **Motor Check**: < 50ms
- **Sensor Check**: < 50ms
- **Event Publishing**: < 100ms

---

## Conclusion

Dispensing Service simulates hardware operations with configurable failure scenarios for realistic testing and development. It tracks all dispensing attempts and publishes events for downstream processing.
