# Vending Machine Control System - Updated Development Plan

## Project Overview

**Project Goal: Vending Machine Control System:**

The system should support the following core functionalities:

1. **Inventory Management** – Track and maintain stock of items available in the vending machine.
2. **Transaction Handling** – Accept item requests and simulate payment collection.
3. **Item Dispensing** – Validate payment and simulate item dispensing.

**Technology Stack:** Java, Spring Boot, Microservices, Eureka Server, Spring Cloud Gateway, JWT Authentication, In-Memory Messaging
**Purpose:** Learning project to practice Java and Spring Framework concepts
**Environment:** Local development focused
**Duration:** 17 working days (3.5 weeks)
**Daily Hours:** 6 hours
**Total Effort:** 102 hours

## Updated Architecture Components

### Core Services

1. **Config Server** - Centralized configuration management
2. **Eureka Server** - Service discovery and registration
3. **API Gateway** - Authentication, authorization, and routing (replaces separate Auth Service)
4. **Inventory Service** - Stock management with event-driven updates
5. **Payment Service** - Payment processing
6. **Transaction Service** - Order orchestration with saga pattern
7. **Dispensing Service** - Item dispensing control
8. **Notification Service** - Alerts and notifications

### Supporting Infrastructure

- **Message Broker**: In-memory events with Spring Application Events (no external broker needed)
- **Database**: MySQL for all services (separate databases per service) - manual schema creation
- **Cache**: Simple in-memory caching with Spring Cache abstraction
- **Authentication**: Gateway-centric JWT with MySQL database for user management
- **Monitoring**: Spring AOP aspects for performance monitoring and thresholds
- **Logging**: Logback with structured logging policies and correlation IDs across all services

## Phase 1: Infrastructure & Authentication (Days 1-5)

### Day 1 - Requirements Analysis & Updated System Design

**Duration:** 7 hours

- Review and update functional requirements with messaging architecture
- Define authentication requirements (Gateway-centric JWT approach)
- Update system architecture to include event-driven patterns
- Plan hybrid synchronous/asynchronous communication patterns
- Design database schema with audit trail requirements
- Create updated component diagrams
- Plan messaging topology and event sourcing strategy

### Day 2 - Infrastructure Setup & Configuration

**Duration:** 6 hours

- Set up project structure with Maven multi-module setup
- Configure development environment (IDE, Maven, Java 17)
- **Set up MySQL databases for each service**:
  - Create separate databases (vending_config, vending_auth, vending_inventory, etc.)
  - Configure connection pooling with HikariCP
  - Create initial SQL scripts for manual schema setup
- Set up Config Server with local file-based configurations
- Configure Eureka Server for service discovery
- **Set up common logging configuration**:
  - Configure Logback with structured JSON logging
  - Define logging levels per service
  - Set up correlation ID generation and propagation
- Create Spring profiles for local development with MySQL

### Day 3 - API Gateway with Authentication & Common Logging

**Duration:** 6 hours

- **Create Spring Cloud Gateway** with basic routing
- **Implement simple JWT authentication system**:
  - JPA user management with admin_users table (MySQL database)
  - BCrypt password hashing
  - JWT token generation and validation
  - Basic role-based access control (ADMIN, USER)
- **Configure route security**:
  - Public routes for customer operations
  - Protected routes for admin operations
- **Implement correlation ID management**:
  - Generate correlation IDs for each request
  - Propagate correlation IDs to downstream services
  - Add correlation ID to all log entries
- **Implement basic user management endpoints**:
  - Admin login endpoint
  - Simple user creation for testing
- **Test authentication flows with Postman**

### Day 4 - Service Discovery & Gateway Integration

**Duration:** 7 hours

- **Complete Gateway routing configuration**:
  - Service discovery integration with Eureka
  - Dynamic route configuration
  - Load balancing setup
- **Implement user context propagation**:
  - Add X-User-Id, X-User-Role, X-Username headers
  - Create header-based authorization for downstream services
- **Add comprehensive error handling**:
  - Authentication error responses
  - Authorization failure handling
  - Circuit breaker integration
- **Implement health checks and monitoring**
- **Create admin user seeding for development**

### Day 5 - Simple Event System & Service Communication

**Duration:** 6 hours

- **Implement Spring Application Events for local messaging**:
  - Create custom event classes (InventoryUpdateEvent, PaymentEvent, etc.)
  - Use @EventListener for asynchronous event processing
  - Simple event publishing with ApplicationEventPublisher
- **Design basic event schema**:
  - InventoryUpdateEvent: Stock level changes
  - TransactionEvent: Purchase completions
  - PaymentEvent: Payment status updates
- **Create base event handling infrastructure**:
  - Abstract event publisher service
  - Event listener base classes
  - Simple correlation ID tracking
- **Test basic event publishing and consumption**
- **Focus on learning Spring Events and async processing**

## Phase 2: Core Business Services (Days 6-12)

### Day 6 - Inventory Service with Event-Driven Architecture

**Duration:** 7 hours

- **Create inventory service with dual communication patterns**:
  - Synchronous endpoints for real-time stock checks
  - Asynchronous event processing for stock updates
- **Implement core entities**: Product, Stock, StockTransaction
- **Create synchronous APIs**:
  - `GET /api/inventory/availability/{itemId}` (public)
  - `POST /api/admin/inventory/items` (admin only)
  - `PUT /api/admin/inventory/stock/{itemId}` (admin only)
- **Implement event consumers**:
  - Listen to `item-dispensed` events
  - Update stock levels asynchronously
  - Publish `low-stock-alert` events
- **Add header-based authorization** (X-User-Role validation)
- **Implement caching for frequent stock checks**
- **Write unit tests for both sync and async operations**

### Day 7 - Inventory Service Enhancement & Event Publishing

**Duration:** 7 hours

- **Complete inventory event publishing**:
  - Publish inventory-update events after stock changes
  - Implement idempotent event processing
  - Add event versioning and backward compatibility
- **Implement advanced inventory features**:
  - Batch stock updates with event aggregation
  - Stock reservation for transaction processing
  - Inventory reporting with audit trail
- **Add comprehensive validation and error handling**
- **Implement inventory analytics endpoints**
- **Integration testing with message broker**
- **Performance testing for high-volume events**

### Day 8 - Payment Service with Transaction Events

**Duration:** 7 hours

- **Create payment service architecture**:
  - Payment entities (Transaction, PaymentMethod, PaymentDetails)
  - Multiple payment method support (cash, card, digital)
  - Synchronous payment processing for immediate validation
- **Implement payment processing workflow**:
  - Payment validation and authorization
  - Payment capture and confirmation
  - Refund processing with compensation events
- **Event integration**:
  - Publish `payment-processed` events
  - Listen to `transaction-cancelled` events for refunds
- **Security implementation**:
  - PCI DSS compliance considerations
  - Sensitive data encryption
  - Audit logging for all payment operations
- **Error handling and retry mechanisms**
- **Unit testing with mock payment providers**

### Day 9 - Transaction Service with Saga Pattern

**Duration:** 7 hours

- **Implement transaction orchestration service**:
  - Transaction entities (Order, OrderItem, TransactionState)
  - Saga pattern for distributed transaction management
  - State machine for transaction lifecycle
- **Create synchronous transaction coordination**:
  - Inventory availability check
  - Payment processing coordination
  - Dispensing initiation
- **Implement saga compensation logic**:
  - Inventory reservation rollback
  - Payment refund initiation
  - Failed transaction cleanup
- **Event publishing and consumption**:
  - Publish transaction lifecycle events
  - Coordinate with other services via events
- **Add circuit breaker and timeout handling**
- **Implement transaction audit trail**

### Day 10 - Transaction Service Event Integration

**Duration:** 7 hours

- **Complete event-driven transaction flows**:
  - Listen to payment completion events
  - Handle dispensing success/failure events
  - Implement transaction completion logic
- **Advanced saga features**:
  - Saga state persistence
  - Recovery from partial failures
  - Long-running transaction support
- **Transaction reporting and analytics**:
  - Real-time transaction status
  - Transaction history with search
  - Performance metrics and KPIs
- **Inter-service communication optimization**:
  - Feign client configuration with circuit breakers
  - Service-to-service authentication via headers
- **Comprehensive error scenarios testing**

### Day 11 - Dispensing Service with Hardware Simulation

**Duration:** 7 hours

- **Create dispensing service with event integration**:
  - Dispensing entities (DispensingAttempt, HardwareStatus, MaintenanceRecord)
  - Hardware interface simulation
  - Dispensing verification logic
- **Implement dispensing workflow**:
  - Receive dispensing requests from transaction service
  - Validate dispensing conditions
  - Execute dispensing operation
  - Verify successful dispensing
- **Event publishing**:
  - Publish `item-dispensed` events on success
  - Publish `dispensing-failed` events on failure
- **Hardware simulation features**:
  - Configurable success/failure rates
  - Jam detection simulation
  - Maintenance mode support
- **Safety checks and error handling**
- **Unit testing with various dispensing scenarios**

### Day 12 - Notification Service & System Integration

**Duration:** 7 hours

- **Create notification service**:
  - Multi-channel notification support (email, SMS, push, in-app)
  - Template-based notification generation
  - Notification history and delivery tracking
- **Event-driven notification processing**:
  - Listen to all system events for notification triggers
  - Low stock alerts to administrators
  - Transaction receipts to customers
  - System maintenance notifications
- **Admin dashboard notifications**:
  - Real-time system status updates
  - Alert management and acknowledgment
  - Notification preferences and routing
- **Integration with all services**:
  - Test complete event flow end-to-end
  - Verify correlation ID propagation
  - Check error handling across services
- **Performance testing of message processing**

## Phase 3: Advanced Features & Integration (Days 13-17)

### Day 13 - AOP Performance Monitoring & Logging Enhancement

**Duration:** 6 hours

- **Implement comprehensive AOP aspects**:
  - Method execution timing with detailed metrics
  - Database operation monitoring
  - Service-to-service call monitoring
  - Memory usage tracking aspects
- **Set up performance thresholds**:
  - Configurable thresholds per service and operation type
  - Automatic alerting for threshold violations
  - Performance degradation detection
- **Enhance logging infrastructure**:
  - Structured JSON logging for all services
  - Context-aware logging with business operation details
  - Log aggregation patterns for easier debugging
- **Create performance monitoring dashboard endpoints**:
  - Real-time performance metrics via Actuator
  - Service health indicators with performance data
  - Custom metrics for business operations
- **End-to-end transaction performance testing**

### Day 14 - Error Handling & Resilience Patterns

**Duration:** 7 hours

- **Implement advanced error handling**:
  - Global exception handling across all services
  - Structured error responses
  - Error correlation and tracking
- **Resilience pattern implementation**:
  - Retry mechanisms with exponential backoff
  - Bulkhead isolation for critical resources
  - Timeout configurations
- **Dead letter queue processing**:
  - Failed event handling
  - Manual retry mechanisms
  - Error analysis and reporting
- **Chaos engineering tests**:
  - Service failure simulation
  - Network partition testing
  - Database connection failure handling
- **Recovery scenario testing**

### Day 15 - Security Enhancement & Audit Trail

**Duration:** 7 hours

- **Enhanced security implementation**:
  - API rate limiting per user role
  - Request/response encryption for sensitive data
  - Advanced JWT features (refresh tokens, token revocation)
- **Comprehensive audit trail**:
  - All admin operations logging
  - Event sourcing for complete transaction history
  - Audit report generation
- **Security testing**:
  - Authentication bypass attempts
  - Authorization escalation testing
  - SQL injection and XSS protection
- **Compliance features**:
  - GDPR compliance for customer data
  - PCI DSS compliance for payment data
  - Data retention policies
- **Security monitoring and alerting**

### Day 16 - Performance Optimization & Caching

**Duration:** 7 hours

- **Performance optimization**:
  - Database query optimization
  - Connection pool tuning
  - JVM performance tuning
- **Caching implementation**:
  - Redis integration for frequently accessed data
  - Cache invalidation strategies
  - Distributed caching for inventory data
- **Message broker optimization**:
  - Queue configuration tuning
  - Consumer scaling strategies
  - Message batching optimization
- **Load testing**:
  - Concurrent transaction processing
  - High-volume event processing
  - System resource utilization
- **Performance monitoring setup**

### Day 17 - Integration Testing & Quality Assurance

**Duration:** 7 hours

- **Comprehensive integration testing**:
  - End-to-end transaction scenarios
  - Error scenario testing
  - Performance regression testing
- **Contract testing implementation**:
  - API contract validation
  - Event schema validation
  - Service compatibility testing
- **Security penetration testing**:
  - Authentication security testing
  - Authorization bypass testing
  - Data encryption validation
- **System reliability testing**:
  - Service restart scenarios
  - Network failure recovery
  - Database failover testing
- **Documentation of test results and fixes**

## Phase 4: Testing & Documentation (Days 15-17)

### Day 15 - Security Enhancement & Audit Trail

**Duration:** 6 hours

- **Enhanced security implementation**:
  - API rate limiting per user role
  - Advanced JWT features (token refresh, validation)
  - Input validation and sanitization
- **Comprehensive audit trail**:
  - All admin operations logging with correlation IDs
  - Event sourcing for complete transaction history
  - Audit report generation endpoints
- **Security testing**:
  - Authentication bypass testing
  - Authorization escalation testing
  - Input validation testing
- **Local security monitoring**:
  - Failed login attempt tracking
  - Suspicious activity detection
  - Security event logging with structured format

### Day 16 - Performance Optimization & Local Testing

**Duration:** 6 hours

- **Performance optimization**:
  - Database query optimization and analysis
  - Connection pool tuning for local MySQL
  - JVM performance tuning for development
- **Caching implementation**:
  - In-memory caching for frequently accessed data
  - Cache invalidation strategies
  - Cache hit/miss metrics with AOP
- **AOP performance monitoring enhancement**:
  - Advanced performance metrics collection
  - Business operation timing analysis
  - Memory usage monitoring
- **Load testing with simple tools**:
  - Concurrent transaction testing with simple scripts
  - Performance threshold validation
  - System resource utilization monitoring

### Day 17 - Integration Testing & Final Documentation

**Duration:** 6 hours

- **Comprehensive integration testing**:
  - End-to-end transaction scenarios testing
  - Error scenario testing and validation
  - Service communication testing
- **Testing framework implementation**:
  - Unit tests for all critical components
  - Integration tests with TestContainers (MySQL)
  - Mock testing for external dependencies
- **Final system validation**:
  - Complete transaction flow testing
  - Performance validation against learning objectives
  - Security compliance verification for local environment
- **Project documentation creation**:
  - API documentation with examples
  - Architecture decision records for learning
  - Setup and running guides
  - Learning outcomes documentation
  - Future enhancement suggestions for continued learning

## Updated Port Allocation

### Local Development Environment

```plain
- Config Server: 8888
- Eureka Server: 8761
- API Gateway: 8080 (includes authentication)
- Inventory Service: 8081
- Payment Service: 8082
- Transaction Service: 8083
- Dispensing Service: 8084
- Notification Service: 8085
- MySQL: 3306 (multiple databases)
```

### Database Structure (Manual Setup)

```plain
- vending_config     (Config Server data)
- vending_auth       (Users and authentication)
- vending_inventory  (Products and stock)
- vending_payment    (Payment transactions)
- vending_transaction (Orders and orchestration)
- vending_dispensing (Hardware control)
- vending_notification (Alerts and messages)
```

### Learning Focus Areas

```plain
- Spring Boot fundamentals and auto-configuration
- Spring Data JPA with MySQL database
- Manual database schema creation and management
- Spring Cloud Gateway and service routing
- JWT authentication with Spring Security
- Spring Application Events for messaging
- Spring AOP for cross-cutting concerns (performance monitoring)
- Structured logging with Logback and correlation IDs
- Performance monitoring and threshold management
- Microservices communication patterns
- RESTful API design and testing
```

## Key Technology Updates

### Authentication Stack

- **Spring Cloud Gateway** with standard (non-reactive) approach for simplicity
- **Spring Security** with JWT support
- **Spring Data JPA** for database access
- **BCrypt** for password hashing
- **MySQL Database** for user storage (manual schema setup)

### Monitoring & Performance Stack

- **Spring AOP** for performance monitoring aspects
- **Custom annotations** (@PerformanceMonitor, @LogExecution)
- **Performance thresholds** with configurable limits
- **Method execution timing** and slow operation detection

### Messaging Stack

- **Spring Application Events** for in-memory messaging
- **@Async and @EventListener** for asynchronous processing
- **Simple event sourcing** for audit trail
- **CompletableFuture** for advanced async patterns

### Logging Stack

- **Logback** with structured JSON logging
- **Correlation IDs** for request tracing across services
- **Service-specific log levels** (DEBUG, INFO, WARN, ERROR)
- **Business operation logging** for audit trails
- **Performance metrics logging** with execution times

### Learning Stack

- **Maven** for dependency management
- **MySQL** for relational database practice
- **HikariCP** for connection pooling
- **Spring Boot Actuator** for health checks
- **Spring Boot DevTools** for hot reloading
- **Postman** for API testing

## Success Criteria Updates

### Authentication Requirements

- JWT-based authentication with 8-hour token expiry
- Role-based access control (SUPER_ADMIN, ADMIN)
- Gateway-centric security with header propagation
- Admin user management with hierarchical permissions
- Complete audit trail for all admin operations

### Messaging Requirements

- Hybrid synchronous/asynchronous communication
- Event-driven inventory updates
- Saga pattern for distributed transactions
- Circuit breaker pattern for resilience
- At-least-once delivery with idempotent processing

### Performance Requirements (Local Learning Environment)

- API responses under 2 seconds (relaxed for local development)
- Event processing within reasonable time for learning
- Support for basic concurrent testing (10-20 requests)
- Smooth development experience with hot reloading
- Fast service startup times for iterative development

### Learning Requirements

- Clean, well-documented code for learning reference
- Comprehensive unit tests to practice testing frameworks
- Simple but realistic business logic implementation
- Good separation of concerns and SOLID principles
- Practical examples of Spring Framework features
- AOP implementation for performance monitoring
- Structured logging with correlation IDs
- Security implementation with JWT and role-based access

### Local Development Requirements

- Simple monitoring with Spring Boot Actuator and custom metrics
- Structured logging for debugging and learning
- Easy service restart and testing workflows
- Clear documentation for learning purposes and future reference
- Simple configuration management with Spring profiles
- Performance monitoring with configurable thresholds
- Manual database setup for better understanding of schema design

## Risk Mitigation Updates

### Authentication Risks

- **JWT secret management**: Environment-specific secrets
- **Token expiry handling**: Automatic refresh mechanisms
- **Role escalation**: Strict permission validation

### Messaging Risks

- **Event ordering**: Message sequence guarantees
- **Duplicate processing**: Idempotent event handlers
- **Dead letter handling**: Failed message recovery

### Integration Risks

- **Service dependencies**: Circuit breaker implementation
- **Data consistency**: Saga compensation patterns
- **Performance degradation**: Load testing and optimization
