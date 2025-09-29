# Day 1: Requirements Analysis & System Design

## Functional Requirements

### 1. Inventory Management

_Primary Functions:_

- _Item Registration_: Add new products with details (name, price, category, expiry date)
- _Stock Tracking_: Real-time monitoring of item quantities per slot
- _Stock Updates_: Automatic decrement after successful dispensing
- _Restocking_: Manual stock replenishment with batch tracking
- _Low Stock Alerts_: Automatic notifications when items reach minimum threshold
- _Inventory Reports_: Generate stock status, sales, and expiry reports
- _Item Categorization_: Group items by type (beverages, snacks, etc.)

_Acceptance Criteria:_

- System must track individual item quantities in real-time
- Stock levels must be accurate within 1 second of any transaction
- Low stock alerts triggered when quantity ≤ configurable threshold
- Support for at least 50 different product types
- Batch tracking for items with expiry dates

### 2. Transaction Handling

_Primary Functions:_

- _Payment Processing_: Accept multiple payment methods (cash, card, contactless, mobile)
- _Price Calculation_: Calculate total including taxes and discounts
- _Transaction Validation_: Verify sufficient payment before dispensing
- _Change Management_: Calculate and dispense correct change for cash payments
- _Receipt Generation_: Provide transaction receipts (digital/physical)
- _Transaction History_: Maintain complete audit trail
- _Refund Processing_: Handle failed dispensing with automatic refunds

_Acceptance Criteria:_

- Support minimum 4 payment methods simultaneously
- Transaction processing must complete within 3 seconds
- 100% accuracy in change calculation and dispensing
- All transactions logged with unique transaction IDs
- Automatic refund within 30 seconds of dispensing failure

### 3. Item Dispensing

_Primary Functions:_

- _Selection Validation_: Verify item availability and payment adequacy
- _Mechanical Control_: Interface with dispensing hardware per slot
- _Dispensing Verification_: Confirm item successfully dispensed
- _Jam Detection_: Identify and handle mechanical failures
- _Maintenance Mode_: Lock specific slots for servicing
- _Dispensing History_: Log all dispensing attempts and results

_Acceptance Criteria:_

- 99.5% successful dispensing rate under normal conditions
- Dispensing verification within 5 seconds
- Automatic jam detection and user notification
- Support for different item sizes and weights
- Graceful handling of hardware malfunctions

## Non-Functional Requirements

### Performance Requirements (Local Environment)

- _Response Time_: API responses < 1000ms (relaxed for local development)
- _Throughput_: Handle 10-20 concurrent transactions (sufficient for testing)
- _Memory Usage_: Services should run comfortably with 4GB total RAM
- _Startup Time_: All services should start within 2 minutes
- _Database Performance_: H2 in-memory for fast development cycles

### Security Requirements

- _Authentication_: JWT-based authentication for admin operations
- _Authorization_: Role-based access control (Admin, Operator, Maintenance)
- _Data Protection_: Encrypt sensitive data (payment info, user data)
- _Communication Security_: HTTPS/TLS for all external communications
- _Audit Logging_: Complete audit trail for all transactions and admin actions
- _PCI Compliance_: Meet payment card industry standards

### Reliability Requirements

- _Fault Tolerance_: System continues operating with single service failures
- _Data Integrity_: ACID compliance for financial transactions
- _Backup Strategy_: Automated daily backups with 30-day retention
- _Disaster Recovery_: Recovery time objective (RTO) < 2 hours
- _Circuit Breakers_: Prevent cascade failures between services

### Usability Requirements

- _User Interface_: Intuitive touch-screen interface for customers
- _Response Feedback_: Clear visual/audio feedback for all user actions
- _Error Messages_: User-friendly error messages with clear instructions
- _Accessibility_: Support for users with disabilities (audio cues, large text)
- _Multi-language_: Support for multiple languages

### Operational Requirements

- _Monitoring_: Real-time health monitoring and alerting
- _Logging_: Structured logging with correlation IDs
- _Maintenance_: Remote diagnostic capabilities
- _Configuration_: Dynamic configuration updates without restart
- _Deployment_: Zero-downtime deployment capabilities

## Core Use Cases

### Customer Operations

1. _Purchase Item_: Select product, make payment, receive item
2. _Check Availability_: View available products and prices
3. _Receive Change_: Get correct change for cash payments
4. _Get Receipt_: Obtain transaction confirmation
5. _Handle Refund_: Receive refund if item doesn't dispense

### Administrative Operations

1. _Manage Inventory_: Add/remove products, update stock levels
2. _View Reports_: Check sales data and transaction history
3. _Configure Pricing_: Set product prices and apply discounts
4. _Monitor System_: Check system health and alerts
5. _Process Refunds_: Handle customer refund requests

### System Operations

1. _Track Stock_: Monitor inventory levels automatically
2. _Process Payments_: Handle multiple payment methods
3. _Control Dispensing_: Manage physical item delivery
4. _Generate Alerts_: Notify about low stock or errors
5. _Maintain Logs_: Keep complete transaction records

## System Architecture Design

### Microservices Architecture

_Service Decomposition Strategy:_

- _Domain-Driven Design_: Services aligned with business capabilities
- _Data Ownership_: Each service owns its data and database schema
- _Loose Coupling_: Services communicate via well-defined APIs
- _High Cohesion_: Related functionality grouped within services

### Core Services Definition

#### 3. _Inventory Management Service_

- _Purpose_: Manage product inventory and stock levels
- _Responsibilities_:
  - Product catalog management
  - Stock level tracking
  - Inventory updates and alerts
  - Reporting and analytics

#### 5. _Transaction Orchestration Service_

- _Purpose_: Coordinate complex business transactions
- _Responsibilities_:
  - Transaction workflow management
  - Service coordination
  - State management
  - Saga pattern implementation

#### 7. _Notification Service_

- _Purpose_: Handle all system notifications
- _Responsibilities_:
  - Alert generation and delivery
  - Receipt generation
  - System status notifications
  - Maintenance alerts

### Database Schema Planning

#### Inventory Service Database

sql
-- Products table
Products: id, name, description, price, category, barcode, weight, dimensions

-- Stock table
Stock: id, product_id, slot_number, current_quantity, min_threshold, max_capacity

-- Stock_Transactions table
Stock_Transactions: id, product_id, transaction_type, quantity_change, timestamp, reason

#### Payment Service Database

sql
-- Payment_Methods table
Payment_Methods: id, type, enabled, configuration

-- Transactions table
Transactions: id, amount, payment_method, status, timestamp, reference_number

-- Payment_Details table
Payment_Details: id, transaction_id, details, encrypted_data

#### Transaction Service Database

sql
-- Orders table
Orders: id, customer_session, total_amount, status, created_at, completed_at

-- Order_Items table
Order_Items: id, order_id, product_id, quantity, unit_price, slot_number

-- Transaction_Logs table
Transaction_Logs: id, order_id, service_name, action, status, timestamp, details

#### Dispensing Service Database

sql
-- Dispensing_Attempts table
Dispensing_Attempts: id, order_id, slot_number, status, attempt_time, verified_time

-- Maintenance_Records table
Maintenance_Records: id, slot_number, maintenance_type, start_time, end_time, notes

-- Hardware_Status table
Hardware_Status: id, slot_number, status, last_check, error_details

### Technology Stack Rationale

#### _Spring Boot Framework_

- _Justification_: Rapid development, extensive ecosystem, production-ready features
- _Benefits_: Auto-configuration, embedded servers, comprehensive monitoring

#### _Spring Cloud Netflix (Eureka)_

- _Justification_: Mature service discovery solution with Spring integration
- _Benefits_: High availability, zone awareness, built-in load balancing

#### _Spring Cloud Gateway_

- _Justification_: Reactive gateway with excellent Spring integration
- _Benefits_: Built-in filters, route predicates, WebFlux support

### Local Development Environment

#### _H2 In-Memory Database_ (Development)

- _Justification_: Fast startup, no installation required, perfect for local development
- _Benefits_: Embedded mode, web console, SQL compatibility

#### _Docker Compose_ (Optional Local Containerization)

- _Justification_: Consistent environment across development machines
- _Benefits_: Easy service orchestration, network isolation, volume management

#### _Local Service Registry_

- _Justification_: Single machine deployment with port-based service discovery
- _Benefits_: Simplified configuration, no network complexity

### Local Deployment Strategy

#### _Profile-Based Configuration_

- _Development Profile_: H2 database, embedded servers, debug logging
- _Local Docker Profile_: PostgreSQL container, Redis container, service networking
- _Production Profile_: External databases, proper security, optimized performance

#### _Port Allocation for Local Services_

- Eureka Server: 8761
- API Gateway: 8080
- Inventory Service: 8081
- Payment Service: 8082
- Transaction Service: 8083
- Dispensing Service: 8084
- Notification Service: 8085
- Config Server: 8888

#### _Optional Docker Setup_

yaml

# docker-compose.yml for local development

services:
postgres: 5432
redis: 6379
rabbitmq: 5672, 15672 (management)

### Integration Patterns

#### _Synchronous Communication_

- _REST APIs_: For immediate response requirements
- _Circuit Breaker_: Prevent cascade failures
- _Retry Mechanisms_: Handle transient failures

#### _Asynchronous Communication_

- _Event-Driven_: For non-blocking operations
- _Message Queues_: For reliable delivery
- _Event Sourcing_: For audit trail requirements

#### _Data Consistency_

- _Saga Pattern_: For distributed transaction management
- _Event Sourcing_: For complete audit trail
- _CQRS_: For read/write optimization where needed

### Error Handling Strategy

#### _Service Level Error Handling_

- _Global Exception Handler_: Consistent error responses
- _Validation_: Input validation at API boundaries
- _Business Rule Validation_: Domain-specific validation logic

#### _System Level Error Handling_

- _Circuit Breakers_: Prevent system overload
- _Bulkhead Pattern_: Isolate critical resources
- _Timeout Management_: Prevent resource exhaustion

#### _User Experience_

- _Graceful Degradation_: Partial functionality during failures
- _User-Friendly Messages_: Clear error communication
- _Automatic Recovery_: Self-healing where possible

### Security Architecture

#### _Authentication & Authorization_

- _JWT Tokens_: Stateless authentication
- _Role-Based Access Control_: Fine-grained permissions
- _OAuth2_: For third-party integrations

#### _Data Protection_

- _Encryption at Rest_: Database encryption
- _Encryption in Transit_: TLS for all communications
- _PII Protection_: Personal data anonymization

#### _Network Security_

- _API Gateway Security_: Centralized security enforcement
- _Internal Service Security_: mTLS for service-to-service communication
- _Rate Limiting_: Prevent abuse and DoS attacks

## Acceptance Criteria Summary

### System-Wide Acceptance Criteria

1. _Availability_: System operational 99.9% of time
2. _Performance_: Response times under 3 seconds for transactions
3. _Scalability_: Support 100+ concurrent users
4. _Security_: PCI DSS compliance for payment processing
5. _Reliability_: Zero data loss for completed transactions
6. _Maintainability_: Zero-downtime deployments
7. _Monitoring_: Real-time visibility into system health
8. _Recovery_: Automatic recovery from common failure scenarios

This completes the requirements analysis and system design phase. The foundation is now established for detailed technical implementation in the subsequent days.
