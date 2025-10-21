# Vending Machine Microservices - Diagram Index

## Overview

This directory contains comprehensive Mermaid diagrams documenting the complete Vending Machine Microservices architecture. All diagrams follow consistent naming conventions and visual standards for easy navigation.

---

## System-Level Diagrams

### [System Overview](./system-overview.md)

Complete system architecture with all components and interactions.

**Included Diagrams:**

- ✅ System Context Diagram - High-level architecture with 8 services
- ✅ Purchase Flow Sequence Diagram - Complete transaction flow
- ✅ Kafka Topic Flow Diagram - Event streaming architecture
- ✅ Deployment Diagram - Local development setup
- ✅ Service Interaction Map - Sync vs async communication patterns
- ✅ Authentication Flow Diagram - JWT-based security
- ✅ Error Flow Diagram - Error handling patterns
- ✅ Performance Monitoring Architecture

**Key Insights:**

- 8 microservices with hybrid communication (sync + async)
- JWT authentication at API Gateway with RBAC
- 5 Kafka topics for event-driven updates
- Single MySQL server with 6 separate databases
- Local deployment requires ~6GB RAM

---

## Service-Level Diagrams

### [Inventory Service](./inventory-service.md) - Port 8081

Product catalog and stock management with real-time availability checks.

**Included Diagrams:**

- ✅ Service Context Diagram
- ✅ Component Diagram - Internal architecture
- ✅ Data Flow Diagram (Level 0 & 1)
- ✅ Entity Relationship Diagram - Products, Stock, ProcessedEvents
- ✅ Kafka Topic Flow - inventory-events producer, dispensing-events consumer
- ✅ Sequence Diagrams:
  - Stock check and update flow
  - Product creation flow
  - Low stock alert flow

**Key Features:**

- Synchronous availability checks for transactions
- Asynchronous stock updates via Kafka
- Low stock threshold monitoring
- Idempotent event processing

**Database:** vending_inventory  
**Kafka Topics:** Produces `inventory-events`, Consumes `dispensing-events`

---

### [Payment Service](./diagrams/payment-service.md) - Port 8082

**Status:** ✅ COMPLETE

Payment processing simulation with multiple payment methods.

**Included Diagrams:**

- ✅ Service Context Diagram
- ✅ Component Diagram
- ✅ Entity Relationship Diagram - PaymentTransaction table
- ✅ Kafka Topic Flow - payment-events producer
- ✅ Cash Payment Sequence - Cash payment processing
- ✅ Card Payment Sequence - Card payment processing
- ✅ Event-Driven Payment Initiation - Transaction event consumption
- ✅ Payment Simulation Logic Flowchart - Configurable success rates

**Key Features:**

- Simulated payment processing (CASH, CREDIT_CARD, DEBIT_CARD)
- Configurable success rates: 95% cash, 90% credit card, 92% debit card
- Transaction audit logging with PaymentTransaction entity
- Event-driven payment status updates

**Database:** vending_payment  
**Kafka Topics:** Produces `payment-events`, Consumes `transaction-events`

---

### [Transaction Service](./diagrams/transaction-service.md) - Port 8083

**Status:** ✅ COMPLETE

Purchase orchestration service coordinating the complete transaction flow.

**Included Diagrams:**

- ✅ Service Context Diagram - SAGA Orchestrator
- ✅ Component Diagram - Orchestration Layer
- ✅ SAGA Orchestration Pattern - State machine with compensation
- ✅ Entity Relationship Diagram - Transaction, TransactionItem, ProcessedEvents
- ✅ Kafka Topic Flow - Multi-topic integration
- ✅ Successful Purchase Flow Sequence - Complete happy path
- ✅ Payment Failure Compensation - Rollback logic
- ✅ Dispensing Failure Compensation - Manual intervention flow

**Key Features:**

- SAGA pattern orchestrator for distributed transactions
- Synchronous REST calls to Inventory, Payment, Dispensing services
- Asynchronous Kafka event-driven state management
- Compensation logic for payment and dispensing failures
- Transaction status state machine (CREATED → COMPLETED)

**Database:** vending_transaction  
**Kafka Topics:** Produces `transaction-events`, Consumes `payment-events`, `dispensing-events`

---

### [Dispensing Service](./diagrams/dispensing-service.md) - Port 8084

**Status:** ✅ COMPLETE

Item dispensing simulation with hardware status tracking.

**Included Diagrams:**

- ✅ Service Context Diagram - Hardware simulation
- ✅ Component Diagram - Simulation layer architecture
- ✅ Hardware Simulation Logic Flowchart - Failure scenario decision tree
- ✅ Entity Relationship Diagram - DispensingRecord, ProcessedEvents
- ✅ Kafka Topic Flow - Event publishing to inventory
- ✅ Successful Dispensing Sequence - Normal operation flow
- ✅ Hardware Fault Flow - Motor/sensor fault handling
- ✅ Product Jam Recovery - Jam detection and manual intervention

**Key Features:**

- Configurable dispensing success rate: 94.5% (motor 1%, sensor 1%, jam 3%, verify 0.5%)
- Hardware simulation with motor controller and sensor monitor
- Operational status tracking with dispensing records
- Event-driven inventory stock updates
- Realistic 500-1000ms dispensing time simulation

**Database:** vending_dispensing  
**Kafka Topics:** Produces `dispensing-events`, Consumes `transaction-events`

---

### [Notification Service](./diagrams/notification-service.md) - Port 8085

**Status:** ✅ COMPLETE

System-wide notification aggregation and admin alerting.

**Included Diagrams:**

- ✅ Service Context Diagram - Event aggregator
- ✅ Component Diagram - Multi-topic consumer architecture
- ✅ Event Aggregation Flow - Classification decision tree
- ✅ Entity Relationship Diagram - Notification, ProcessedEvents
- ✅ Kafka Topic Flow - Consumes ALL 5 topics
- ✅ Critical Alert Processing Sequence - Alert creation workflow
- ✅ Admin Dashboard Query - Unacknowledged alerts retrieval
- ✅ Alert Acknowledgment Sequence - Admin acknowledgment flow

**Key Features:**

- Event aggregation from all 5 Kafka topics
- Notification classification: CRITICAL, WARNING, INFO
- Alert severity mapping (transaction.failed → CRITICAL, stock.low → WARNING)
- Admin dashboard integration with acknowledgment tracking
- Idempotent event processing with deduplication

**Database:** vending_notification  
**Kafka Topics:** Consumes ALL topics (`transaction-events`, `payment-events`, `inventory-events`, `dispensing-events`, `notification-events`)

---

### [API Gateway](./diagrams/api-gateway.md) - Port 8080

**Status:** ✅ COMPLETE

Authentication, authorization, and routing layer.

**Included Diagrams:**

- ✅ Service Context Diagram - Entry point & security
- ✅ Component Diagram - Filter chain & security modules
- ✅ Authentication Flow Sequence - JWT login & validation
- ✅ Authorization Matrix - Role-based permissions table
- ✅ Entity Relationship Diagram - User entity with roles
- ✅ User Registration Sequence - SUPER_ADMIN only
- ✅ Failed Authentication Flow - Error handling
- ✅ Token Expiration Handling - 8-hour expiry logic

**Key Features:**

- JWT token generation with 8-hour expiry (28,800 seconds)
- Role-based access control: SUPER_ADMIN (all operations), ADMIN (read/update)
- User management with BCrypt password encoding (10 rounds)
- User context propagation via headers (X-User-Id, X-User-Role, X-Username)
- Gateway-level security enforcement with filter chain
- Eureka-based service routing with load balancing

**Database:** vending_auth (User table with username, password, role)  
**Kafka Topics:** None (synchronous HTTP only)

---

## Infrastructure Diagrams

### [Infrastructure Services](./diagrams/infrastructure-services.md)

**Status:** ✅ COMPLETE

Config Server, Eureka Server, Kafka, and MySQL architecture.

**Included Diagrams:**

- ✅ Overall Infrastructure Topology - Complete system infrastructure
- ✅ Config Server Architecture - File-based configuration management
- ✅ Configuration Hierarchy Flow - Bootstrap to runtime
- ✅ Eureka Service Registration Flow - Service discovery process
- ✅ Eureka Dashboard View - Registered instances visualization
- ✅ Kafka Topic Structure - 5 topics with producers/consumers
- ✅ MySQL Database-per-Service - 6 databases architecture
- ✅ Service Startup Sequence - Dependency chain with delays

**Key Features:**

- **Config Server**: File-based configuration with per-service properties
- **Eureka Server**: Service registry with 30-second heartbeats
- **Kafka**: 5 topics (single partition, single replica for local dev)
- **MySQL**: Database-per-service pattern (6 databases on single server)
- **Startup Order**: Config → Eureka → Gateway → Business services
- **Port Allocation**: 8888 (Config), 8761 (Eureka), 9092 (Kafka), 2181 (Zookeeper), 3306 (MySQL)

**Databases:** vending_auth, vending_inventory, vending_payment, vending_transaction, vending_dispensing, vending_notification

**Components:**

- Config Server (Port 8888) - File-based properties
- Eureka Server (Port 8761) - Service registry with web dashboard
- Apache Kafka (Port 9092) + Zookeeper (Port 2181) - Message broker
- MySQL Server (Port 3306) - 6 isolated databases

---

## Diagram Standards & Conventions

### Visual Conventions

**Colors:**

- Infrastructure: Purple shades (#9c27b0, #673ab7)
- Gateway: Red shades (#f44336, #ffebee)
- Business Services: Various pastels for differentiation
- Databases: Green shades (#4caf50, #c8e6c9)
- Kafka: Yellow shades (#ffeb3b, #fff9c4)

**Line Styles:**

- Solid lines (—): Synchronous HTTP/REST calls
- Dashed lines (-.->): Asynchronous Kafka events
- Dotted lines (...): Configuration/registration dependencies

**Node Labels:**

- Include service name, port, and key characteristics
- Use consistent abbreviations (Svc = Service, DB = Database)

### Naming Conventions

**Services:**

- Format: `ServiceName` (PascalCase)
- Example: `InventoryService`, `PaymentService`

**Kafka Topics:**

- Format: `domain-events` (kebab-case)
- Example: `transaction-events`, `payment-events`

**Database Tables:**

- Format: `table_name` (snake_case)
- Example: `payment_transactions`, `admin_users`

**Endpoints:**

- Public: `/api/{domain}/*`
- Admin: `/api/admin/{domain}/*`
- Example: `/api/inventory/products`, `/api/admin/inventory/products`

---

## Cross-Reference Matrix

### Kafka Event Flow

| Event Type              | Producer Service | Topic               | Consumer Services                    |
| ----------------------- | ---------------- | ------------------- | ------------------------------------ |
| `transaction.created`   | Transaction      | transaction-events  | Payment, Dispensing, Notification    |
| `transaction.completed` | Transaction      | transaction-events  | Dispensing, Notification             |
| `transaction.failed`    | Transaction      | transaction-events  | Notification                         |
| `payment.initiated`     | Payment          | payment-events      | Transaction, Notification            |
| `payment.completed`     | Payment          | payment-events      | Transaction, Notification            |
| `payment.failed`        | Payment          | payment-events      | Transaction, Notification            |
| `stock.updated`         | Inventory        | inventory-events    | Notification                         |
| `stock.low`             | Inventory        | inventory-events    | Notification                         |
| `product.added`         | Inventory        | inventory-events    | Notification                         |
| `dispensing.requested`  | Dispensing       | dispensing-events   | Inventory, Transaction, Notification |
| `dispensing.completed`  | Dispensing       | dispensing-events   | Inventory, Transaction, Notification |
| `dispensing.failed`     | Dispensing       | dispensing-events   | Transaction, Notification            |
| `notification.created`  | Notification     | notification-events | Future (Email/SMS)                   |

### Service Dependencies

| Service       | Depends On (Startup)         | Calls Synchronously            | Publishes To        | Subscribes To                     |
| ------------- | ---------------------------- | ------------------------------ | ------------------- | --------------------------------- |
| Config Server | MySQL                        | -                              | -                   | -                                 |
| Eureka Server | Config                       | -                              | -                   | -                                 |
| API Gateway   | Config, Eureka, MySQL        | All Business Services          | -                   | -                                 |
| Inventory     | Config, Eureka, MySQL, Kafka | -                              | inventory-events    | dispensing-events                 |
| Payment       | Config, Eureka, MySQL, Kafka | -                              | payment-events      | transaction-events                |
| Transaction   | Config, Eureka, MySQL, Kafka | Inventory, Payment, Dispensing | transaction-events  | payment-events, dispensing-events |
| Dispensing    | Config, Eureka, MySQL, Kafka | -                              | dispensing-events   | transaction-events                |
| Notification  | Config, Eureka, MySQL, Kafka | -                              | notification-events | ALL topics                        |

### Database Schema

| Service      | Database Name        | Tables                                            | Key Relationships               |
| ------------ | -------------------- | ------------------------------------------------- | ------------------------------- |
| API Gateway  | vending_auth         | admin_users                                       | -                               |
| Inventory    | vending_inventory    | products, stock, processed_events                 | Product 1:1 Stock               |
| Payment      | vending_payment      | payment_transactions                              | -                               |
| Transaction  | vending_transaction  | transactions, transaction_items, processed_events | Transaction 1:N TransactionItem |
| Dispensing   | vending_dispensing   | dispensing_operations, hardware_status            | -                               |
| Notification | vending_notification | notifications                                     | -                               |

---

## Usage Guidelines

### For Developers

1. **Understanding System Flow**: Start with [System Overview](./system-overview.md)
2. **Service Implementation**: Refer to individual service diagrams
3. **API Integration**: Check endpoint specifications in service docs
4. **Event Handling**: Review Kafka Topic Flow diagrams
5. **Database Design**: Consult ERD diagrams for schema details

### For System Administrators

1. **Deployment**: Review Deployment Diagram in System Overview
2. **Monitoring**: Check Performance Monitoring Architecture
3. **Troubleshooting**: Use Error Flow Diagram and service health checks
4. **Capacity Planning**: Refer to System Requirements table

### For Architects

1. **Design Patterns**: Review Service Interaction Map
2. **Data Flow**: Study DFD diagrams for each service
3. **Security**: Examine Authentication Flow Diagram
4. **Scalability**: Analyze Kafka partitioning and connection pooling

---

## Document Status

| Document             | Status      | Last Updated | Completeness |
| -------------------- | ----------- | ------------ | ------------ |
| System Overview      | ✅ Complete | 2025-10-21   | 100%         |
| Inventory Service    | ✅ Complete | 2025-10-21   | 100%         |
| Payment Service      | ✅ Complete | 2025-10-21   | 100%         |
| Transaction Service  | ✅ Complete | 2025-10-21   | 100%         |
| Dispensing Service   | ✅ Complete | 2025-10-21   | 100%         |
| Notification Service | ✅ Complete | 2025-10-21   | 100%         |
| API Gateway          | ✅ Complete | 2025-10-21   | 100%         |
| Infrastructure       | ✅ Complete | 2025-10-21   | 100%         |

**Legend:**

- ✅ Complete: Fully documented with all diagrams
- ⏳ Pending: Planned but not yet created
- 🔄 In Progress: Currently being developed

**Phase 2 Status:** ✅ All services documented - 100% complete

---

## Revision History

| Version | Date       | Author         | Changes                                                                                   |
| ------- | ---------- | -------------- | ----------------------------------------------------------------------------------------- |
| 2.0     | 2025-10-21 | GitHub Copilot | Phase 2 Complete: Payment, Transaction, Dispensing, Notification, Gateway, Infrastructure |
| 1.0     | 2025-01-20 | GitHub Copilot | Initial creation: System Overview, Inventory Service, and Index                           |
| 0.9     | 2025-01-20 | GitHub Copilot | Project Flow Guide created                                                                |

---

## Contributing

When adding new diagrams:

1. Follow the established visual conventions and color scheme
2. Use Mermaid syntax for all diagrams
3. Include both high-level and detailed views
4. Add sequence diagrams for key operations
5. Update this index with new content
6. Maintain consistency with existing diagrams
7. Add cross-references to related diagrams

---

## Related Documentation

- [Project Flow Guide](../project_flow_guide.md) - Business logic and system flows
- [SRS Document](../SRS-Vending-Machine.md) - Complete requirements specification
- [Development Plan](../development_plan.md) - Implementation timeline
- [Authentication Architecture](../Vending%20Machine%20Authentication%20Flow%20Architecture.md) - Security details
- [Messaging Architecture](../Vending%20Machine%20Messaging%20Architecture.md) - Kafka patterns

---

## Conclusion

This diagram collection provides comprehensive visual documentation for the Vending Machine Microservices system. All services have been fully documented with detailed diagrams covering architecture, data flows, sequences, and integrations.

**Current Progress:** 8/8 services documented (100%) ✅  
**Total Diagrams:** 50+ Mermaid diagrams  
**Documentation:** Complete system coverage with 7,000+ lines

**Phase 2 Complete:** All planned diagrams have been created, including Payment Service, Transaction Service (SAGA orchestrator), Dispensing Service (hardware simulation), Notification Service (event aggregation), API Gateway (security), and Infrastructure Services.

For questions or clarifications, refer to the Project Flow Guide or individual service implementations in the repository.
