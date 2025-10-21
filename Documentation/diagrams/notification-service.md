# Notification Service Diagrams

## Table of Contents

- [Service Context Diagram](#service-context-diagram)
- [Component Diagram](#component-diagram)
- [Event Aggregation Flow](#event-aggregation-flow)
- [Entity Relationship Diagram](#entity-relationship-diagram)
- [Kafka Topic Flow](#kafka-topic-flow)
- [Sequence Diagrams](#sequence-diagrams)

---

## Service Context Diagram

```mermaid
graph TB
    subgraph "External Services"
        API_GATEWAY[API Gateway<br/>Port 8080]
    end

    subgraph "Notification Service - Port 8085<br/>Event Aggregator"
        CONTROLLER[Notification Controller<br/>REST Endpoints]
        SERVICE[Notification Service<br/>Alert Management]
        AGGREGATOR[Event Aggregator<br/>Multi-topic Listener]
        REPO[Notification Repository<br/>JPA]
        KAFKA_CONSUMER[Kafka Consumer<br/>All Topics]
    end

    subgraph "Data Infrastructure"
        DB[(MySQL Database<br/>vending_notification)]
        KAFKA[(Kafka Broker<br/>5 Topics)]
    end

    API_GATEWAY -->|GET /admin/notifications| CONTROLLER
    CONTROLLER --> SERVICE

    SERVICE --> AGGREGATOR
    SERVICE --> REPO

    KAFKA -->|All Events| KAFKA_CONSUMER
    KAFKA_CONSUMER --> AGGREGATOR
    AGGREGATOR --> SERVICE

    REPO <-->|JDBC| DB

    style AGGREGATOR fill:#ffccbc
    style KAFKA_CONSUMER fill:#fff9c4
```

---

## Component Diagram

```mermaid
graph TB
    subgraph "API Layer"
        ADMIN_API[Admin API<br/>/api/admin/notifications/*]
    end

    subgraph "Business Logic Layer"
        NOTIFICATION_SERVICE[Notification Service<br/>Alert Management]
        ALERT_CLASSIFIER[Alert Classifier<br/>Severity & Category]
    end

    subgraph "Event Processing Layer"
        EVENT_AGGREGATOR[Event Aggregator<br/>Multi-topic Consumer]
        TRANSACTION_LISTENER[Transaction Event Listener]
        PAYMENT_LISTENER[Payment Event Listener]
        INVENTORY_LISTENER[Inventory Event Listener]
        DISPENSING_LISTENER[Dispensing Event Listener]
        NOTIFICATION_LISTENER[Notification Event Listener]
    end

    subgraph "Data Access Layer"
        NOTIFICATION_REPO[Notification Repository]
        PROCESSED_EVENT_REPO[Processed Event Repository]
    end

    subgraph "Domain Model"
        NOTIFICATION[Notification Entity<br/>Alert Storage]
        ALERT_TYPE[AlertType Enum<br/>CRITICAL, WARNING, INFO]
    end

    ADMIN_API --> NOTIFICATION_SERVICE

    NOTIFICATION_SERVICE --> ALERT_CLASSIFIER
    NOTIFICATION_SERVICE --> NOTIFICATION_REPO

    EVENT_AGGREGATOR --> TRANSACTION_LISTENER
    EVENT_AGGREGATOR --> PAYMENT_LISTENER
    EVENT_AGGREGATOR --> INVENTORY_LISTENER
    EVENT_AGGREGATOR --> DISPENSING_LISTENER
    EVENT_AGGREGATOR --> NOTIFICATION_LISTENER

    TRANSACTION_LISTENER --> NOTIFICATION_SERVICE
    PAYMENT_LISTENER --> NOTIFICATION_SERVICE
    INVENTORY_LISTENER --> NOTIFICATION_SERVICE
    DISPENSING_LISTENER --> NOTIFICATION_SERVICE
    NOTIFICATION_LISTENER --> NOTIFICATION_SERVICE

    NOTIFICATION_REPO <--> NOTIFICATION
    NOTIFICATION --> ALERT_TYPE

    style EVENT_AGGREGATOR fill:#ffccbc
```

---

## Event Aggregation Flow

```mermaid
flowchart TD
    START([Event Received from Kafka]) --> DEDUPE{Already Processed?}
    DEDUPE -->|Yes| SKIP[Skip duplicate event]
    DEDUPE -->|No| CLASSIFY{Classify Event Type}

    CLASSIFY -->|transaction.failed| TRANS_FAIL[Create CRITICAL alert<br/>"Transaction Failed"]
    CLASSIFY -->|payment.failed| PAY_FAIL[Create CRITICAL alert<br/>"Payment Failed"]
    CLASSIFY -->|dispensing.failed| DISP_FAIL[Create CRITICAL alert<br/>"Dispensing Failed"]
    CLASSIFY -->|stock.low| STOCK_LOW[Create WARNING alert<br/>"Low Stock Alert"]
    CLASSIFY -->|stock.depleted| STOCK_DEP[Create CRITICAL alert<br/>"Stock Depleted"]
    CLASSIFY -->|transaction.completed| TRANS_OK[Create INFO alert<br/>"Transaction Completed"]

    TRANS_FAIL --> ENRICH[Enrich with event data]
    PAY_FAIL --> ENRICH
    DISP_FAIL --> ENRICH
    STOCK_LOW --> ENRICH
    STOCK_DEP --> ENRICH
    TRANS_OK --> ENRICH

    ENRICH --> SAVE[Save notification to database]
    SAVE --> MARK[Mark event as processed]

    MARK --> END([Return success])
    SKIP --> END

    style TRANS_FAIL fill:#ffcdd2
    style PAY_FAIL fill:#ffcdd2
    style DISP_FAIL fill:#ffcdd2
    style STOCK_LOW fill:#fff9c4
    style STOCK_DEP fill:#ffcdd2
    style TRANS_OK fill:#c8e6c9
```

### Alert Classification

| Event Type            | Alert Type | Severity | Action Required       |
| --------------------- | ---------- | -------- | --------------------- |
| transaction.failed    | CRITICAL   | High     | Investigate cause     |
| payment.failed        | CRITICAL   | High     | Verify payment system |
| dispensing.failed     | CRITICAL   | High     | Check hardware        |
| stock.low             | WARNING    | Medium   | Restock soon          |
| stock.depleted        | CRITICAL   | High     | Restock immediately   |
| transaction.completed | INFO       | Low      | None                  |
| payment.completed     | INFO       | Low      | None                  |
| dispensing.completed  | INFO       | Low      | None                  |

---

## Entity Relationship Diagram

```mermaid
erDiagram
    NOTIFICATIONS {
        BIGINT id PK "Auto-increment"
        VARCHAR(50) alert_type "CRITICAL, WARNING, INFO"
        VARCHAR(100) event_type "Source event type"
        VARCHAR(255) message "Alert message"
        TEXT details "Event payload JSON"
        BOOLEAN acknowledged "Admin acknowledgment"
        TIMESTAMP acknowledged_at "Acknowledgment time"
        TIMESTAMP created_at "Creation time"
    }

    PROCESSED_EVENTS {
        BIGINT id PK "Auto-increment"
        VARCHAR(255) event_id UK "Unique event ID"
        VARCHAR(100) event_type "Event type"
        TIMESTAMP processed_at "Processing time"
    }
```

---

## Kafka Topic Flow

```mermaid
graph TB
    subgraph "Kafka Topics (All 5)"
        TRANS_TOPIC[transaction-events]
        PAY_TOPIC[payment-events]
        INV_TOPIC[inventory-events]
        DISP_TOPIC[dispensing-events]
        NOTIF_TOPIC[notification-events]
    end

    subgraph "Notification Service Consumers"
        KAFKA_CONSUMER[Multi-Topic Consumer<br/>Group: notification-group]
    end

    subgraph "Notification Service"
        AGGREGATOR[Event Aggregator]
        SERVICE[Notification Service]
        REPO[Notification Repository]
    end

    TRANS_TOPIC --> KAFKA_CONSUMER
    PAY_TOPIC --> KAFKA_CONSUMER
    INV_TOPIC --> KAFKA_CONSUMER
    DISP_TOPIC --> KAFKA_CONSUMER
    NOTIF_TOPIC --> KAFKA_CONSUMER

    KAFKA_CONSUMER --> AGGREGATOR
    AGGREGATOR --> SERVICE
    SERVICE --> REPO

    style KAFKA_CONSUMER fill:#ffccbc
    style AGGREGATOR fill:#ffccbc
```

---

## Sequence Diagrams

### Critical Alert Processing

```mermaid
sequenceDiagram
    autonumber
    participant Kafka
    participant Consumer as Kafka Consumer
    participant Aggregator as Event Aggregator
    participant Service as Notification Service
    participant Classifier as Alert Classifier
    participant Repo as Notification Repository

    Kafka->>Consumer: dispensing.failed event
    Consumer->>Aggregator: Process event

    Aggregator->>Aggregator: Check if already processed
    Aggregator->>Service: Create alert

    Service->>Classifier: Classify severity
    Classifier-->>Service: CRITICAL

    Service->>Service: Enrich message<br/>"Hardware jam detected"
    Service->>Repo: Save notification
    Repo-->>Service: Notification saved

    Service->>Repo: Mark event processed
    Service-->>Consumer: Processing complete
```

### Admin Dashboard Query

```mermaid
sequenceDiagram
    autonumber
    actor Admin
    participant Gateway as API Gateway
    participant Controller as Notification Controller
    participant Service as Notification Service
    participant Repo as Notification Repository

    Admin->>Gateway: GET /api/admin/notifications/unacknowledged
    Gateway->>Gateway: Validate JWT
    Gateway->>Controller: Route request

    Controller->>Service: getUnacknowledgedAlerts()
    Service->>Repo: findByAcknowledgedFalse()
    Repo-->>Service: List<Notification>

    Service->>Service: Convert to DTOs
    Service-->>Controller: List<NotificationDTO>
    Controller-->>Gateway: 200 OK
    Gateway-->>Admin: Critical alerts display
```

### Alert Acknowledgment

```mermaid
sequenceDiagram
    autonumber
    actor Admin
    participant Controller as Notification Controller
    participant Service as Notification Service
    participant Repo as Notification Repository

    Admin->>Controller: POST /api/admin/notifications/1/acknowledge
    Controller->>Service: acknowledgeAlert(1)

    Service->>Repo: findById(1)
    Repo-->>Service: Notification entity

    Service->>Service: Set acknowledged=true<br/>acknowledgedAt=now()
    Service->>Repo: save(notification)
    Repo-->>Service: Updated notification

    Service-->>Controller: NotificationDTO
    Controller-->>Admin: 200 OK
```

---

## API Endpoints

### Admin Endpoints

#### Get All Notifications

- **Endpoint**: `GET /api/admin/notifications`
- **Auth**: JWT (ADMIN or SUPER_ADMIN)
- **Response**:

```json
[
  {
    "id": 1,
    "alertType": "CRITICAL",
    "eventType": "dispensing.failed",
    "message": "Hardware jam detected",
    "details": "{\"productId\":1,\"transactionId\":100}",
    "acknowledged": false,
    "createdAt": "2024-01-15T10:30:00Z"
  }
]
```

#### Get Unacknowledged Alerts

- **Endpoint**: `GET /api/admin/notifications/unacknowledged`
- **Auth**: JWT (ADMIN or SUPER_ADMIN)

#### Acknowledge Alert

- **Endpoint**: `POST /api/admin/notifications/{id}/acknowledge`
- **Auth**: JWT (ADMIN or SUPER_ADMIN)

#### Get Alerts by Type

- **Endpoint**: `GET /api/admin/notifications/type/{alertType}`
- **Auth**: JWT (ADMIN or SUPER_ADMIN)

---

## Performance Characteristics

- **Event Processing**: < 50ms per event
- **Database Writes**: 1 per notification
- **Event Deduplication**: < 10ms
- **Admin Dashboard Query**: < 200ms
- **Kafka Consumer Lag**: < 100 events

---

## Conclusion

Notification Service aggregates events from all Kafka topics and classifies them into actionable alerts for administrators. It provides a centralized dashboard for system monitoring and alerting.
