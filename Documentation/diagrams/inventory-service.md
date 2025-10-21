# Inventory Service Diagrams

## Table of Contents

- [Service Context Diagram](#service-context-diagram)
- [Component Diagram](#component-diagram)
- [Data Flow Diagram](#data-flow-diagram)
- [Entity Relationship Diagram](#entity-relationship-diagram)
- [Kafka Topic Flow](#kafka-topic-flow)
- [Sequence Diagrams](#sequence-diagrams)

---

## Service Context Diagram

Shows how Inventory Service interacts with other system components.

```mermaid
graph TB
    subgraph "External Services"
        GATEWAY[API Gateway<br/>Port 8080]
        TRANSACTION[Transaction Service<br/>Port 8083]
    end

    subgraph "Inventory Service - Port 8081"
        CONTROLLER[Product Controller<br/>REST Endpoints]
        SERVICE[Product Service<br/>Business Logic]
        REPO[Product Repository<br/>Stock Repository]
        KAFKA_PRODUCER[Kafka Producer<br/>Event Publisher]
        KAFKA_CONSUMER[Kafka Consumer<br/>Event Listener]
    end

    subgraph "Data Infrastructure"
        DB[(MySQL Database<br/>vending_inventory)]
        KAFKA[(Kafka Broker<br/>Topics: inventory-events<br/>dispensing-events)]
    end

    subgraph "Infrastructure Services"
        EUREKA[Eureka Server<br/>Service Registry]
        CONFIG[Config Server<br/>Configuration]
    end

    GATEWAY -->|Route: /api/inventory/*<br/>/api/admin/inventory/*| CONTROLLER
    TRANSACTION -->|GET /check-availability| CONTROLLER

    CONTROLLER --> SERVICE
    SERVICE --> REPO
    SERVICE --> KAFKA_PRODUCER
    KAFKA_CONSUMER --> SERVICE

    REPO <-->|JDBC| DB
    KAFKA_PRODUCER -->|Publish events| KAFKA
    KAFKA -->|Consume dispensing events| KAFKA_CONSUMER

    CONTROLLER -.->|Register| EUREKA
    CONTROLLER -.->|Load config| CONFIG

    style CONTROLLER fill:#e8eaf6
    style SERVICE fill:#c5cae9
    style REPO fill:#9fa8da
    style KAFKA_PRODUCER fill:#ffccbc
    style KAFKA_CONSUMER fill:#ffccbc
    style DB fill:#c8e6c9
    style KAFKA fill:#fff9c4
```

---

## Component Diagram

Internal architecture showing all components and their relationships.

```mermaid
graph TB
    subgraph "Presentation Layer"
        PUBLIC_API[Public API<br/>/api/inventory/*]
        ADMIN_API[Admin API<br/>/api/admin/inventory/*]
    end

    subgraph "Business Logic Layer"
        PRODUCT_SERVICE[Product Service<br/>CRUD Operations]
        STOCK_SERVICE[Stock Service<br/>Stock Management]
        VALIDATION[Validation Service<br/>Business Rules]
    end

    subgraph "Integration Layer"
        KAFKA_PRODUCER[Kafka Producer Service<br/>Event Publishing]
        KAFKA_CONSUMER[Kafka Consumer Service<br/>Event Consumption]
        SYNC_SERVICE[Initial State Sync Service<br/>Stock Synchronization]
    end

    subgraph "Data Access Layer"
        PRODUCT_REPO[Product Repository<br/>JPA]
        STOCK_REPO[Stock Repository<br/>JPA]
        PROCESSED_EVENT_REPO[Processed Event Repository<br/>Idempotency]
    end

    subgraph "Domain Model"
        PRODUCT[Product Entity<br/>id, name, price, category]
        STOCK[Stock Entity<br/>id, quantity, minThreshold]
        PROCESSED_EVENT[Processed Event Entity<br/>eventId, eventType]
    end

    PUBLIC_API --> PRODUCT_SERVICE
    PUBLIC_API --> STOCK_SERVICE
    ADMIN_API --> PRODUCT_SERVICE
    ADMIN_API --> STOCK_SERVICE

    PRODUCT_SERVICE --> VALIDATION
    STOCK_SERVICE --> VALIDATION

    PRODUCT_SERVICE --> PRODUCT_REPO
    STOCK_SERVICE --> STOCK_REPO
    STOCK_SERVICE --> KAFKA_PRODUCER

    KAFKA_CONSUMER --> STOCK_SERVICE
    KAFKA_CONSUMER --> PROCESSED_EVENT_REPO

    PRODUCT_REPO <--> PRODUCT
    STOCK_REPO <--> STOCK
    PROCESSED_EVENT_REPO <--> PROCESSED_EVENT

    SYNC_SERVICE --> PRODUCT_SERVICE
    SYNC_SERVICE --> KAFKA_PRODUCER

    style PUBLIC_API fill:#e3f2fd
    style ADMIN_API fill:#ffebee
    style PRODUCT_SERVICE fill:#e8f5e9
    style STOCK_SERVICE fill:#e8f5e9
    style KAFKA_PRODUCER fill:#fff9c4
    style KAFKA_CONSUMER fill:#fff9c4
```

---

## Data Flow Diagram

Flow of data through the Inventory Service for key operations.

### DFD Level 0 - Context Level

```mermaid
graph LR
    CUSTOMER[Customer]
    ADMIN[Administrator]
    TRANSACTION_SVC[Transaction Service]
    DISPENSING_SVC[Dispensing Service]
    NOTIFICATION_SVC[Notification Service]

    INVENTORY[Inventory Service<br/>Process 0]

    CUSTOMER -->|View Products| INVENTORY
    ADMIN -->|Manage Products<br/>Update Stock| INVENTORY
    TRANSACTION_SVC -->|Check Availability| INVENTORY
    DISPENSING_SVC -->|Dispensing Events| INVENTORY

    INVENTORY -->|Product List| CUSTOMER
    INVENTORY -->|Availability Status| TRANSACTION_SVC
    INVENTORY -->|Stock Events| NOTIFICATION_SVC
    INVENTORY -->|Low Stock Alerts| NOTIFICATION_SVC

    style INVENTORY fill:#4caf50
```

### DFD Level 1 - Detailed Processes

```mermaid
graph TB
    subgraph "Inputs"
        INPUT1[Product Request]
        INPUT2[Stock Update Request]
        INPUT3[Availability Check]
        INPUT4[Dispensing Event]
    end

    subgraph "Inventory Service Processes"
        P1[1.0 Manage Products<br/>CRUD Operations]
        P2[2.0 Manage Stock<br/>Track Quantities]
        P3[3.0 Check Availability<br/>Validate Stock]
        P4[4.0 Process Dispensing<br/>Update Stock]
        P5[5.0 Monitor Thresholds<br/>Generate Alerts]
    end

    subgraph "Data Stores"
        D1[(D1: Products)]
        D2[(D2: Stock)]
        D3[(D3: Processed Events)]
    end

    subgraph "Outputs"
        OUTPUT1[Product Data]
        OUTPUT2[Availability Status]
        OUTPUT3[Stock Update Events]
        OUTPUT4[Low Stock Alerts]
    end

    INPUT1 --> P1
    INPUT2 --> P2
    INPUT3 --> P3
    INPUT4 --> P4

    P1 <--> D1
    P2 <--> D2
    P3 --> D1
    P3 --> D2
    P4 --> D2
    P4 --> D3
    P4 --> P5
    P2 --> P5

    P5 --> D2

    P1 --> OUTPUT1
    P3 --> OUTPUT2
    P2 --> OUTPUT3
    P4 --> OUTPUT3
    P5 --> OUTPUT4

    style P1 fill:#bbdefb
    style P2 fill:#c5cae9
    style P3 fill:#c8e6c9
    style P4 fill:#ffccbc
    style P5 fill:#ffecb3
```

---

## Entity Relationship Diagram

Database schema for Inventory Service (vending_inventory database).

```mermaid
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

### Table Details

#### Product Table

- **Primary Key**: `id` (BIGINT, auto-increment)
- **Unique Constraints**: None
- **Indexes**:
  - Primary key index on `id`
  - Index on `category` (for filtering)
- **Relationships**: One-to-One with Stock

#### Stock Table

- **Primary Key**: `id` (BIGINT, auto-increment)
- **Foreign Keys**: `product_id` references `product(id)`
- **Unique Constraints**: `product_id` (one stock record per product)
- **Indexes**:
  - Primary key index on `id`
  - Unique index on `product_id`
  - Index on `quantity` (for low stock queries)
- **Relationships**: One-to-One with Product (owning side)

#### Processed Events Table

- **Primary Key**: `id` (BIGINT, auto-increment)
- **Unique Constraints**: `event_id` (prevent duplicate event processing)
- **Purpose**: Idempotency control for Kafka event consumption
- **Indexes**:
  - Primary key index on `id`
  - Unique index on `event_id`
  - Composite index on `(event_id, event_type)`

---

## Kafka Topic Flow

Inventory Service's interaction with Kafka topics.

```mermaid
graph TB
    subgraph "Inventory Service"
        STOCK_SERVICE[Stock Service]
        KAFKA_PRODUCER[Kafka Producer]
        KAFKA_CONSUMER[Dispensing Event Consumer]
    end

    subgraph "Kafka Topics"
        INV_TOPIC[inventory-events<br/>Partition: 1, Replica: 1]
        DISP_TOPIC[dispensing-events<br/>Partition: 1, Replica: 1]
    end

    subgraph "Event Producers"
        STOCK_SERVICE -->|stock.updated<br/>stock.low<br/>product.added| KAFKA_PRODUCER
    end

    subgraph "Event Consumers"
        NOTIFICATION[Notification Service<br/>group: notification-group]
        KAFKA_CONSUMER
    end

    KAFKA_PRODUCER -->|Publish| INV_TOPIC
    INV_TOPIC -->|Subscribe| NOTIFICATION

    DISP_TOPIC -->|Subscribe<br/>group: inventory-group| KAFKA_CONSUMER
    KAFKA_CONSUMER -->|Update Stock| STOCK_SERVICE

    style KAFKA_PRODUCER fill:#ffccbc
    style KAFKA_CONSUMER fill:#ffccbc
    style INV_TOPIC fill:#bbdefb
    style DISP_TOPIC fill:#c5cae9
```

### Event Schemas

#### Published Events (inventory-events topic)

**stock.updated event:**

```json
{
  "eventId": "uuid-123",
  "eventType": "STOCK_UPDATED",
  "timestamp": "2024-01-15T10:30:00Z",
  "correlationId": "transaction-uuid",
  "payload": {
    "productId": 1,
    "productName": "Coca Cola 500ml",
    "previousQuantity": 25,
    "currentQuantity": 24,
    "minThreshold": 5
  }
}
```

**stock.low event:**

```json
{
  "eventId": "uuid-456",
  "eventType": "LOW_STOCK_ALERT",
  "timestamp": "2024-01-15T10:30:00Z",
  "correlationId": "inventory-check-uuid",
  "payload": {
    "productId": 1,
    "productName": "Coca Cola 500ml",
    "currentQuantity": 4,
    "minThreshold": 5,
    "alertLevel": "WARNING"
  }
}
```

**product.added event:**

```json
{
  "eventId": "uuid-789",
  "eventType": "PRODUCT_ADDED",
  "timestamp": "2024-01-15T10:30:00Z",
  "correlationId": "admin-operation-uuid",
  "payload": {
    "productId": 10,
    "productName": "Snickers Bar",
    "price": 1.5,
    "category": "Snacks",
    "initialQuantity": 30
  }
}
```

#### Consumed Events (dispensing-events topic)

**dispensing.completed event:**

```json
{
  "eventId": "uuid-321",
  "eventType": "DISPENSING_COMPLETED",
  "timestamp": "2024-01-15T10:29:58Z",
  "correlationId": "transaction-uuid",
  "payload": {
    "transactionId": 100,
    "productId": 1,
    "quantity": 1,
    "dispensingOperationId": 50
  }
}
```

---

## Sequence Diagrams

### Stock Check and Update Flow

```mermaid
sequenceDiagram
    autonumber
    participant Transaction as Transaction Service
    participant Controller as Inventory Controller
    participant Service as Stock Service
    participant Repo as Stock Repository
    participant DB as MySQL Database
    participant Kafka as Kafka Broker

    Note over Transaction,Kafka: Availability Check (Synchronous)
    Transaction->>Controller: GET /check-availability<br/>{productId, quantity}
    Controller->>Service: checkAvailability(productId, quantity)
    Service->>Repo: findByProductId(productId)
    Repo->>DB: SELECT * FROM stock WHERE product_id = ?
    DB-->>Repo: Stock record
    Repo-->>Service: Stock entity
    Service->>Service: Validate: quantity <= stock.quantity
    Service-->>Controller: AvailabilityResponse {available: true}
    Controller-->>Transaction: 200 OK {available: true}

    Note over Transaction,Kafka: Stock Update (Asynchronous)
    Note over Kafka: Dispensing completed event published
    Kafka->>Service: Consume dispensing.completed event
    Service->>Repo: Check ProcessedEvent<br/>(prevent duplicate)

    alt Event Not Processed
        Service->>Repo: findByProductId(productId)
        Repo->>DB: SELECT * FROM stock WHERE product_id = ?
        DB-->>Repo: Stock record
        Repo-->>Service: Stock entity

        Service->>Service: stock.quantity -= dispensed.quantity
        Service->>Repo: save(stock)
        Repo->>DB: UPDATE stock SET quantity = ? WHERE id = ?
        DB-->>Repo: Update confirmed

        Service->>Repo: save(ProcessedEvent)
        Repo->>DB: INSERT INTO processed_events

        Service->>Kafka: Publish stock.updated event

        alt Stock Below Threshold
            Service->>Service: Check: quantity <= minThreshold
            Service->>Kafka: Publish stock.low alert
        end
    else Event Already Processed
        Service->>Service: Log: Duplicate event ignored
    end
```

### Product Creation Flow

```mermaid
sequenceDiagram
    autonumber
    actor Admin
    participant Gateway as API Gateway
    participant Controller as Inventory Controller
    participant Service as Product Service
    participant Repo as Repository
    participant DB as MySQL
    participant Kafka as Kafka Broker

    Admin->>Gateway: POST /api/admin/inventory/products<br/>Authorization: Bearer {token}<br/>{name, price, category, description}
    Gateway->>Gateway: Validate JWT & Role
    Gateway->>Controller: Forward with headers<br/>X-User-Id, X-User-Role

    Controller->>Service: createProduct(dto, userId)
    Service->>Service: Validate product data<br/>(price > 0, name not empty)

    Service->>Repo: Check if product exists<br/>findByName(name)

    alt Product Does Not Exist
        Service->>Repo: save(Product)
        Repo->>DB: INSERT INTO products
        DB-->>Repo: Product with ID

        Service->>Repo: save(Stock)<br/>{productId, quantity: 0, threshold: 5}
        Repo->>DB: INSERT INTO stock

        Service->>Kafka: Publish product.added event

        Service-->>Controller: ProductResponse
        Controller-->>Gateway: 201 Created
        Gateway-->>Admin: Product created successfully
    else Product Already Exists
        Service-->>Controller: BusinessException: Product exists
        Controller-->>Gateway: 400 Bad Request
        Gateway-->>Admin: Error: Product already exists
    end
```

### Low Stock Alert Flow

```mermaid
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

---

## API Endpoints

### Public Endpoints

#### Get All Products

- **Endpoint**: `GET /api/inventory/products`
- **Auth**: None
- **Response**:

```json
[
  {
    "id": 1,
    "name": "Coca Cola 500ml",
    "price": 2.5,
    "category": "Beverages",
    "description": "Refreshing cola drink",
    "stockQuantity": 24,
    "available": true
  }
]
```

#### Check Availability

- **Endpoint**: `GET /api/inventory/availability/{productId}?quantity={quantity}`
- **Auth**: None
- **Response**:

```json
{
  "productId": 1,
  "available": true,
  "currentQuantity": 24,
  "requestedQuantity": 1
}
```

### Admin Endpoints

#### Create Product

- **Endpoint**: `POST /api/admin/inventory/products`
- **Auth**: JWT (ADMIN or SUPER_ADMIN)
- **Request**:

```json
{
  "name": "Snickers Bar",
  "price": 1.5,
  "category": "Snacks",
  "description": "Chocolate bar with peanuts"
}
```

#### Update Stock

- **Endpoint**: `PUT /api/admin/inventory/stock/{productId}`
- **Auth**: JWT (ADMIN or SUPER_ADMIN)
- **Request**:

```json
{
  "quantity": 50,
  "minThreshold": 10
}
```

#### Get Inventory Reports

- **Endpoint**: `GET /api/admin/inventory/reports`
- **Auth**: JWT (ADMIN or SUPER_ADMIN)
- **Response**:

```json
{
  "totalProducts": 15,
  "lowStockProducts": 3,
  "outOfStockProducts": 1,
  "totalValue": 1250.0,
  "lastUpdated": "2024-01-15T10:30:00Z"
}
```

---

## Error Handling

### Error Scenarios

1. **Product Not Found**

   - HTTP Status: 404
   - Response: `{"error": "Product not found", "correlationId": "uuid"}`

2. **Insufficient Stock**

   - HTTP Status: 400
   - Response: `{"error": "Insufficient stock", "available": 5, "requested": 10}`

3. **Invalid Stock Update**

   - HTTP Status: 400
   - Response: `{"error": "Quantity cannot be negative"}`

4. **Duplicate Product**
   - HTTP Status: 409
   - Response: `{"error": "Product already exists"}`

---

## Performance Characteristics

- **Database Queries**: Optimized with indexes on `product_id`, `quantity`, `category`
- **Event Processing**: < 100ms from Kafka consumption to database update
- **Availability Check**: < 200ms response time
- **Connection Pool**: HikariCP with 10-20 connections
- **Idempotency**: ProcessedEvent table prevents duplicate event processing

---

## Monitoring & Health Checks

### Actuator Endpoints

- `/actuator/health` - Overall service health
- `/actuator/metrics` - Application metrics
- `/actuator/info` - Service information

### Custom Metrics

- `inventory.stock.updates.count` - Stock update operations
- `inventory.low.stock.alerts.count` - Low stock alerts generated
- `inventory.availability.checks.count` - Availability check requests
- `inventory.events.consumed.count` - Kafka events processed

### Health Indicators

- Database connectivity
- Kafka broker connectivity
- Eureka registration status
- Disk space availability

---

## Conclusion

The Inventory Service is a critical component managing product catalog and stock levels with both synchronous availability checks and asynchronous event-driven stock updates. Key features include:

- Real-time stock availability for transaction processing
- Event-driven stock updates after dispensing
- Low stock threshold monitoring and alerting
- Idempotent event processing for data consistency
- Comprehensive admin management interface
