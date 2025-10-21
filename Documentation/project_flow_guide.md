# Vending Machine Microservices - Project Flow Guide

---

## System Overview

The Vending Machine Control System is a microservices-based application built with Spring Boot, implementing a complete purchase flow from product selection to item dispensing. The system uses both synchronous (REST) and asynchronous (Kafka) communication patterns to ensure data consistency and system reliability.

### Architecture Highlights

- **8 Microservices**: Config, Eureka, Gateway, Inventory, Payment, Transaction, Dispensing, Notification
- **Communication**: Hybrid approach - Synchronous HTTP for real-time transactions, Asynchronous Kafka for updates
- **Database**: MySQL with database-per-service pattern (8 separate databases)
- **Security**: JWT-based authentication at API Gateway level with RBAC
- **Service Discovery**: Eureka Server for dynamic service registration
- **Configuration**: Centralized Config Server with file-based properties

---

## Business Logic Flow

### Complete Purchase Flow

```plaintext
1. Customer selects product → API Gateway
2. Gateway routes to Transaction Service
3. Transaction Service checks inventory availability (Synchronous REST call to Inventory Service)
4. If available, Transaction Service processes payment (Synchronous REST call to Payment Service)
5. Payment Service validates payment and publishes payment-completed event to Kafka
6. Transaction Service consumes payment event and requests dispensing (Synchronous REST call to Dispensing Service)
7. Dispensing Service simulates item dispensing and publishes dispensing-completed event to Kafka
8. Inventory Service consumes dispensing event and updates stock levels
9. If stock falls below threshold, Inventory Service publishes low-stock-alert event
10. Notification Service consumes all events and stores notifications for admin review
11. Transaction Service updates transaction status to COMPLETED
12. Customer receives confirmation response
```

### Failure Scenarios

#### Insufficient Stock

- Transaction Service receives "not available" response from Inventory Service
- Transaction status set to FAILED_INSUFFICIENT_STOCK
- Customer receives immediate error response
- No payment processing initiated

#### Payment Failure

- Payment Service publishes payment-failed event to Kafka
- Transaction Service consumes event and updates status to FAILED_PAYMENT
- No dispensing initiated
- Customer receives payment failure notification

#### Dispensing Failure

- Dispensing Service publishes dispensing-failed event to Kafka
- Transaction Service consumes event and updates status to FAILED_DISPENSING
- Compensation: Stock levels NOT reduced (event not published)
- Notification Service alerts admin for manual intervention
- Customer receives dispensing failure notification

---

## Service Dependencies

### Infrastructure Layer

#### Config Server (Port 8888)

- **Purpose**: Centralized configuration management
- **Dependencies**: None (starts first)
- **Configuration Source**: Local file system (`config-server/src/main/resources/config/`)
- **Provides Config To**: All services via `spring.config.import`

#### Eureka Server (Port 8761)

- **Purpose**: Service discovery and registration
- **Dependencies**: Config Server
- **Consumers**: All business services and Gateway

#### MySQL Database (Port 3306)

- **Purpose**: Data persistence
- **Databases**:
  - `vending_auth` - API Gateway user management
  - `vending_inventory` - Products and stock
  - `vending_payment` - Payment transactions
  - `vending_transaction` - Orders and transaction history
  - `vending_dispensing` - Dispensing operations
  - `vending_notification` - System notifications

#### Apache Kafka (Port 9092) + Zookeeper (Port 2181)

- **Purpose**: Event streaming platform
- **Topics**: 5 topics with 1 partition each (single replica for local dev)
  - transaction-events
  - payment-events
  - inventory-events
  - dispensing-events
  - notification-events

### Gateway Layer

#### API Gateway (Port 8080)

- **Purpose**: Authentication, authorization, and routing
- **Dependencies**: Config Server, Eureka Server, MySQL
- **Key Features**:
  - JWT token generation and validation
  - Role-based access control (SUPER_ADMIN, ADMIN)
  - User management
  - Request routing to business services
  - User context propagation via headers (X-User-Id, X-User-Role, X-Username)

### Business Services Layer

#### Inventory Service (Port 8081)

- **Purpose**: Product catalog and stock management
- **Dependencies**: Config Server, Eureka Server, MySQL, Kafka
- **Synchronous Calls**: Receives availability check from Transaction Service
- **Kafka Producer**: Publishes `stock.updated`, `stock.low` events
- **Kafka Consumer**: Consumes `dispensing.completed` events to update stock
- **Key Entities**: Product, Stock, ProcessedEvent

#### Payment Service (Port 8082)

- **Purpose**: Payment processing simulation
- **Dependencies**: Config Server, Eureka Server, MySQL, Kafka
- **Synchronous Calls**: Receives payment request from Transaction Service
- **Kafka Producer**: Publishes `payment.initiated`, `payment.completed`, `payment.failed` events
- **Kafka Consumer**: Consumes `transaction.created` events
- **Key Entities**: PaymentTransaction

#### Transaction Service (Port 8083)

- **Purpose**: Purchase flow orchestration
- **Dependencies**: Config Server, Eureka Server, MySQL, Kafka, Inventory Service, Payment Service, Dispensing Service
- **Synchronous Calls**:
  - Calls Inventory Service for availability check
  - Calls Payment Service for payment processing
  - Calls Dispensing Service for item dispensing
- **Kafka Producer**: Publishes `transaction.created`, `transaction.completed`, `transaction.failed` events
- **Kafka Consumer**: Consumes `payment.completed`, `payment.failed`, `dispensing.completed`, `dispensing.failed` events
- **Key Entities**: Transaction, TransactionItem, ProcessedEvent

#### Dispensing Service (Port 8084)

- **Purpose**: Item dispensing simulation
- **Dependencies**: Config Server, Eureka Server, MySQL, Kafka
- **Synchronous Calls**: Receives dispensing request from Transaction Service
- **Kafka Producer**: Publishes `dispensing.requested`, `dispensing.completed`, `dispensing.failed` events
- **Kafka Consumer**: Consumes `transaction.completed` events
- **Key Entities**: DispensingOperation, HardwareStatus

#### Notification Service (Port 8085)

- **Purpose**: System-wide notification aggregation and storage
- **Dependencies**: Config Server, Eureka Server, MySQL, Kafka
- **Synchronous Calls**: None (event-driven only)
- **Kafka Consumer**: Consumes ALL events from all topics for notification generation
- **Key Entities**: Notification

---

## Kafka Event Flow

### Topic Architecture

| Topic               | Partitions | Replication | Producers            | Consumers                                                    |
| ------------------- | ---------- | ----------- | -------------------- | ------------------------------------------------------------ |
| transaction-events  | 1          | 1           | Transaction Service  | Payment Service, Dispensing Service, Notification Service    |
| payment-events      | 1          | 1           | Payment Service      | Transaction Service, Notification Service                    |
| inventory-events    | 1          | 1           | Inventory Service    | Notification Service                                         |
| dispensing-events   | 1          | 1           | Dispensing Service   | Inventory Service, Transaction Service, Notification Service |
| notification-events | 1          | 1           | Notification Service | (Future: Email/SMS services)                                 |

### Event Schemas

All events follow a standard structure with correlation IDs for tracing:

```json
{
  "eventId": "uuid",
  "eventType": "EVENT_TYPE_ENUM",
  "timestamp": "2024-01-15T10:30:00Z",
  "correlationId": "transaction-uuid",
  "payload": {
    // Event-specific data
  }
}
```

### Producer-Consumer Relationships

#### Transaction Events

- **Producer**: Transaction Service
- **Events**: `transaction.created`, `transaction.completed`, `transaction.failed`
- **Consumers**:
  - Payment Service: Listens for `transaction.created` to initiate payment
  - Dispensing Service: Listens for `transaction.completed` (alternative flow)
  - Notification Service: Logs all transaction events

#### Payment Events

- **Producer**: Payment Service
- **Events**: `payment.initiated`, `payment.completed`, `payment.failed`
- **Consumers**:
  - Transaction Service: Updates transaction status based on payment outcome
  - Notification Service: Logs payment events and generates admin alerts on failures

#### Inventory Events

- **Producer**: Inventory Service
- **Events**: `stock.updated`, `stock.low`, `product.added`
- **Consumers**:
  - Notification Service: Generates low stock alerts for admin dashboard

#### Dispensing Events

- **Producer**: Dispensing Service
- **Events**: `dispensing.requested`, `dispensing.completed`, `dispensing.failed`
- **Consumers**:
  - Inventory Service: Updates stock quantities after successful dispensing
  - Transaction Service: Updates transaction status
  - Notification Service: Logs dispensing operations and alerts on failures

#### Notification Events

- **Producer**: Notification Service
- **Events**: `notification.created`, `notification.sent`
- **Consumers**: Future integrations (Email, SMS, Push notifications)

### Event Processing Guarantees

- **Delivery Semantics**: At-least-once delivery
- **Idempotency**: All consumers use `ProcessedEvent` table to prevent duplicate processing
- **Event Ordering**: Single partition per topic ensures order within topic
- **Failure Handling**: Dead letter queue pattern for failed event processing (to be implemented)

---

## REST API Endpoints

### Public Endpoints (No Authentication Required)

#### Inventory Service REST API

- `GET /api/inventory/products` - List all available products
- `GET /api/inventory/availability/{productId}` - Check product availability

#### Transaction Service REST API

- `POST /api/transaction/purchase` - Initiate purchase transaction

  ```json
  {
    "productId": 1,
    "quantity": 1,
    "paymentMethod": "CASH",
    "paidAmount": 2.5
  }
  ```

#### Payment Service API

- `POST /api/payment/process` - Process payment (called by Transaction Service)

### Protected Endpoints (JWT Authentication Required)

#### API Gateway - Authentication

- `POST /api/auth/login` - Admin login (returns JWT token)

  ```json
  {
    "username": "admin",
    "password": "password"
  }
  ```

- `POST /api/admin/users` - Create admin user (SUPER_ADMIN only)
- `GET /api/admin/users` - List all users (ADMIN)
- `PUT /api/admin/users/{id}` - Update user (SUPER_ADMIN only)
- `DELETE /api/admin/users/{id}` - Delete user (SUPER_ADMIN only)

#### Inventory Service

- `POST /api/admin/inventory/products` - Create new product
- `PUT /api/admin/inventory/products/{id}` - Update product
- `DELETE /api/admin/inventory/products/{id}` - Delete product
- `PUT /api/admin/inventory/stock/{productId}` - Update stock level
- `GET /api/admin/inventory/reports` - Generate inventory reports

#### Payment Service

- `GET /api/admin/payment/transactions` - View payment history
- `GET /api/admin/payment/statistics` - Payment processing statistics

#### Transaction Service

- `GET /api/admin/transaction/history` - View transaction history
- `GET /api/admin/transaction/{id}` - View transaction details
- `GET /api/admin/transaction/statistics` - Transaction statistics

#### Dispensing Service

- `GET /api/admin/dispensing/status` - Hardware status information
- `GET /api/admin/dispensing/history` - Dispensing operation history
- `PUT /api/admin/dispensing/configuration` - Update simulation parameters

#### Notification Service

- `GET /api/admin/notifications` - Retrieve notification history
- `GET /api/admin/notifications/statistics` - Notification statistics
- `PUT /api/admin/notifications/{id}/acknowledge` - Acknowledge notification

---

## Authentication & Authorization

### JWT Token Flow

1. Admin submits credentials to `POST /api/auth/login`
2. API Gateway validates credentials against MySQL database
3. Gateway generates JWT token with 8-hour expiry containing:
   - User ID
   - Username
   - Role (SUPER_ADMIN or ADMIN)
4. Token returned to client: `{"token": "eyJhbG...", "expiresIn": 28800}`
5. Client includes token in subsequent requests: `Authorization: Bearer {token}`
6. Gateway validates token on each request and adds user context headers
7. Business services trust headers from gateway (no authentication logic in services)

### Role-Based Access Control

#### SUPER_ADMIN Role

- Full system access
- User management (create, update, delete admin users)
- All admin operations
- Cannot be deleted by other users

#### ADMIN Role

- Inventory management
- Transaction monitoring
- Payment monitoring
- Dispensing control
- Notification management
- Cannot create other users

### User Context Propagation

Gateway adds these headers to all authenticated requests:

- `X-User-Id`: Authenticated user's database ID
- `X-User-Role`: User's role (SUPER_ADMIN or ADMIN)
- `X-Username`: Username for audit logging

Business services read these headers for:

- Audit logging (who performed the action)
- Business logic decisions (if needed)
- Correlation with transaction data

---

## System Startup Order

**Critical**: Services must start in this order to ensure proper configuration and registration.

1. **MySQL Server** (Port 3306) - Must be running with all databases created
2. **Zookeeper** (Port 2181) - Required for Kafka
3. **Kafka Broker** (Port 9092) - Message broker
4. **Config Server** (Port 8888) - Configuration provider
5. **Eureka Server** (Port 8761) - Service registry
6. **Business Services** (Parallel start possible):
   - Inventory Service (Port 8081)
   - Payment Service (Port 8082)
   - Transaction Service (Port 8083)
   - Dispensing Service (Port 8084)
   - Notification Service (Port 8085)
7. **API Gateway** (Port 8080) - Entry point (starts last to ensure all services registered)

### Health Checks

All services expose health endpoints via Spring Boot Actuator:

- `http://localhost:{port}/actuator/health`
- Includes database connectivity, Kafka connectivity, and service-specific health indicators

### Service Discovery Dashboard

Eureka dashboard available at: `http://localhost:8761`

- Shows all registered services
- Service status and health
- Instance information

---

## Performance Characteristics

### Response Time Targets

- **API Response Time**: < 2000ms under normal load
- **Event Processing Time**: < 100ms for Kafka event consumption
- **Database Query Time**: < 500ms for database operations
- **Service Startup Time**: < 120 seconds per service

### Throughput Targets

- **Concurrent Transactions**: 10-50 simultaneous purchases
- **Event Throughput**: 1000+ events per minute
- **Database Connections**: 20+ concurrent connections per service
- **API Request Rate**: 100+ requests per minute

### Resource Requirements

- **Memory**: 6GB RAM minimum for all services
- **CPU**: Normal operations < 70% utilization
- **Disk**: 2GB free space for logs and data
- **Network**: Localhost communication sufficient

---

## Monitoring & Observability

### Logging Strategy

- **Structured Logging**: All services use SLF4J with Logback
- **Correlation IDs**: Track requests across service boundaries
- **Log Levels**: INFO for business operations, DEBUG for troubleshooting, ERROR for failures
- **Log Files**: Separate log file per service in `logs/` directory

### Metrics Collection

Spring Boot Actuator exposes:

- `/actuator/metrics` - JVM and application metrics
- `/actuator/prometheus` - Prometheus-formatted metrics
- Custom metrics for business operations (transaction success rate, inventory levels, etc.)

### Audit Trail

- All admin operations logged with user context
- Transaction history maintained in database
- Event history available through Kafka log compaction
- Notification history stored for compliance

---

## Data Consistency Model

### Synchronous Consistency (Strong)

Used for critical real-time operations:

- Inventory availability checks
- Payment processing
- Immediate transaction status

### Eventual Consistency (Asynchronous)

Used for non-critical updates:

- Stock level updates after dispensing
- Notification generation
- Audit log creation
- Reporting and analytics

### Conflict Resolution

- **Optimistic Locking**: Version fields on key entities
- **Idempotent Consumers**: ProcessedEvent table prevents duplicate processing
- **Event Ordering**: Single partition per topic ensures order
- **Manual Intervention**: Admin dashboard for conflict resolution

---

## Testing Strategy

### Unit Tests

- Service layer logic
- Repository operations
- Utility functions
- 80%+ coverage target

### Integration Tests

- REST API endpoints with authentication
- Kafka event publishing and consumption
- Database operations
- Inter-service communication

### End-to-End Tests

- Complete purchase flow
- Failure scenarios (insufficient stock, payment failure, dispensing failure)
- Admin operations
- Security and authorization

### Load Tests

- Concurrent user simulation
- Peak load handling
- Resource utilization monitoring
- Performance degradation analysis

---

## Future Enhancements

### Short-term

- Circuit breaker pattern for resilience
- Advanced caching with Redis
- Enhanced monitoring with Prometheus/Grafana
- Containerization with Docker

### Long-term

- Real payment gateway integration
- Kubernetes orchestration
- Multi-region deployment
- Advanced analytics and reporting
- Mobile app integration
- Real hardware integration

---

## Troubleshooting Guide

### Common Issues

1. **Service Won't Start**

   - Check if Config Server is running
   - Verify Eureka Server is accessible
   - Ensure MySQL database exists and is accessible
   - Check Kafka broker is running

2. **Events Not Consumed**

   - Verify Kafka topics exist
   - Check consumer group configuration
   - Review ProcessedEvent table for duplicates
   - Check Kafka broker connectivity

3. **Authentication Failures**

   - Verify JWT token not expired (8-hour limit)
   - Check user exists in database and is active
   - Ensure correct password hash
   - Verify gateway routing configuration

4. **Slow Performance**
   - Check database connection pool exhaustion
   - Monitor JVM heap usage
   - Review Kafka consumer lag
   - Check for network latency

### Debug Tools

- **Eureka Dashboard**: Service registration status
- **Actuator Endpoints**: Health, metrics, and diagnostics
- **Kafka Consumer Groups**: Event processing lag
- **Database Logs**: Query performance and errors
- **Application Logs**: Correlation ID tracking across services

---

## Conclusion

This project flow guide provides a comprehensive overview of the Vending Machine Microservices system architecture, business flows, and operational characteristics. Use this document as a reference for understanding system behavior, troubleshooting issues, and planning enhancements.

For detailed service-specific diagrams, refer to the `/Documentation/diagrams/` directory.
