# Vending Machine Control System - Updated Development Plan

## Project Overview

**Project Goal:**

A simplified microservices-based vending machine control system for local development and learning.

### Core Functionalities

1. **Inventory Management** – Track and maintain stock of items
2. **Transaction Handling** – Accept item requests and process payments
3. **Item Dispensing** – Validate payment and simulate dispensing
4. **Administrative Functions** – Stock management and system monitoring

**Technology Stack:** Java 17, Spring Boot, Microservices, Eureka Server, Spring Cloud Gateway, JWT Authentication, Apache Kafka, MySQL
**Purpose:** Learning project to practice microservices architecture
**Environment:** Local development only
**Duration:** 17 working days (3.5 weeks)
**Daily Hours:** 9 hours
**Total Effort:** 153 hours

## Updated Architecture

### Core Services (Mandatory)

1. **Config Server** - Local file-based configuration management
2. **Eureka Server** - Service discovery and registration
3. **API Gateway** - JWT authentication and routing
4. **Inventory Service** - Stock management
5. **Payment Service** - Simulated payment processing
6. **Transaction Service** - Purchase flow orchestration
7. **Dispensing Service** - Item dispensing simulation
8. **Notification Service** - Alert logging and storage

### Infrastructure Components

- **Message Broker**: Apache Kafka with Zookeeper (local)
- **Database**: Single MySQL server with separate databases per service
- **Authentication**: Gateway-only JWT with MySQL storage
- **Service Discovery**: Eureka Server for all services

## Kafka Topics Design

```yaml
Topics:
  - transaction-events: Transaction lifecycle events
  - payment-events: Payment processing events
  - inventory-events: Stock level changes
  - dispensing-events: Item dispensing results
  - notification-events: System alerts and notifications
```

## Database Structure

```yaml
MySQL Databases (Manual Schema Creation):
  - vending_config: Config server data
  - vending_auth: Users and JWT tokens
  - vending_inventory: Products and stock levels
  - vending_payment: Payment transactions
  - vending_transaction: Orders and purchase history
  - vending_dispensing: Dispensing operations
  - vending_notification: System notifications
```

## Port Allocation

```yaml
Infrastructure:
  - Config Server: 8888
  - Eureka Server: 8761
  - API Gateway: 8080
  - Kafka: 9092
  - Zookeeper: 2181
  - MySQL: 3306

Services:
  - Inventory Service: 8081
  - Payment Service: 8082
  - Transaction Service: 8083
  - Dispensing Service: 8084
  - Notification Service: 8085
```

---

## **Development Timeline**

### **Phase 1: Infrastructure Setup (Days 1-4)**

#### **Day 1 - Environment Setup & Configuration**

_Tasks:_

- Set up Maven multi-module project structure
- Configure local development environment (Java 17, MySQL, Kafka)
- Install and configure Kafka + Zookeeper locally
- Create MySQL databases for each service
- Set up basic project dependencies and Spring Boot versions
- Create common libraries for shared DTOs and utilities

_Deliverables:_

- Working development environment
- Maven project structure
- Local Kafka and MySQL running
- Basic project skeleton

#### **Day 2 - Config Server & Eureka Setup**

_Tasks:_

- Implement Config Server with local application.properties files
- Configure separate property files for each service
- Set up Eureka Server with basic configuration
- Create service registration configurations
- Test service discovery functionality
- Configure logging patterns for all services

_Deliverables:_

- Working Config Server
- Working Eureka Server
- Service registration templates
- Basic logging configuration

#### **Day 3 - API Gateway with JWT Authentication**

_Tasks:_

- Create Spring Cloud Gateway with basic routing
- Implement JWT authentication system:
  - User entity with SUPER_ADMIN and ADMIN roles
  - BCrypt password hashing
  - JWT token generation and validation (8-hour expiry)
  - MySQL database integration for user storage
- Configure protected and public routes
- Implement user context header propagation
- Create admin login endpoint

_Deliverables:_

- Working API Gateway
- JWT authentication system
- User management database schema
- Login functionality

### **Day 4 – API Gateway Security Integration & Role-Based Routing**

_Tasks:_

- **Security Configuration**

  - Implement `SecurityConfig` class for JWT validation.
  - Configure JWT filter for all protected routes.
  - Enforce role-based access control:
    - `SUPER_ADMIN` for system and user management.
    - `ADMIN` for operational endpoints (inventory, transaction monitoring, notifications).
  - Integrate password hashing (BCrypt) for admin users.
  - Validate JWT token signature, expiration, and user status.

- **Routing Configuration**

  - Route authenticated requests to respective microservices.
  - Add user context headers (`X-User-Id`, `X-User-Role`, `X-Username`) for downstream services.
  - Define conditional routing for role-based endpoints.
  - Implement fallback strategies for unauthorized or expired token access.

- **Integration Tasks**
  - Update gateway routes in `application.yml` with protected admin routes.
  - Ensure public routes bypass authentication.
  - Test JWT authentication, role enforcement, and header propagation.

_Deliverables:_

- Fully secured API Gateway with JWT authentication.
- Role-based endpoint protection configured.
- Conditional routing to microservices based on authentication and authorization.
- Integration tests validating access control, token validation, and user context propagation.

#### **Day 5 - Gateway Integration & Testing**

_Tasks:_

- Complete gateway routing to downstream services
- Implement service discovery integration with Eureka
- Add comprehensive error handling for authentication
- Create user management endpoints (create, update, delete users)
- Implement request/response logging
- Create Postman collection for authentication testing

_Deliverables:_

- Complete gateway functionality
- User management system
- Authentication testing suite
- Documentation for API usage

### **Phase 2: Core Business Services (Days 5-10)**

#### **Day 6 - Inventory Service Foundation**

_Tasks:_

- Create Inventory Service with Eureka registration
- Design and implement core entities:
  - Product (id, name, price, description)
  - Stock (productId, quantity, minThreshold)
- Implement basic REST endpoints:
  - GET /api/inventory/products (public)
  - GET /api/inventory/availability/{productId} (public)
  - POST /api/admin/inventory/products (admin)
  - PUT /api/admin/inventory/stock/{productId} (admin)
- Set up MySQL database connection and manual schema
- Implement basic validation and error handling

_Deliverables:_

- Working Inventory Service
- Product and stock management
- Database schema and sample data
- Basic API endpoints

#### **Day 7 - Inventory Service with Kafka Integration**

_Tasks:_

- Integrate Kafka producer for inventory events
- Implement stock update event publishing
- Create Kafka consumer for dispensing events (stock reduction)
- Add low stock threshold monitoring
- Implement inventory event handlers:
  - Stock level changes
  - Product availability updates
  - Low stock alerts
- Add comprehensive logging for all operations

_Deliverables:_

- Kafka-integrated inventory service
- Event-driven stock management
- Low stock monitoring
- Complete inventory functionality

#### **Day 8 - Payment Service Implementation**

_Tasks:_

- Create Payment Service with core entities:
  - PaymentTransaction (id, amount, method, status)
  - PaymentMethod enum (CASH, CREDIT_CARD, DEBIT_CARD)
- Implement payment processing simulation:
  - Cash payment validation
  - Card payment simulation (success/failure rates)
  - Payment status tracking
- Create payment endpoints:
  - POST /api/payment/process (public)
  - GET /api/admin/payment/transactions (admin)
- Integrate Kafka producer for payment events

_Deliverables:_

- Working Payment Service
- Simulated payment processing
- Payment event publishing
- Payment transaction history

#### **Day 9 - Transaction Service Foundation**

_Tasks:_

- Create Transaction Service with orchestration logic
- Design transaction entities:
  - Transaction (id, customerId, items, totalAmount, status)
  - TransactionItem (productId, quantity, price)
- Implement transaction workflow:
  - Inventory availability check
  - Payment processing coordination
  - Transaction status management
- Create transaction endpoints:
  - POST /api/transaction/purchase (public)
  - GET /api/admin/transaction/history (admin)
- Add basic error handling and rollback logic

_Deliverables:_

- Working Transaction Service
- Purchase orchestration
- Transaction management
- Basic workflow implementation

#### **Day 10 - Transaction Service Kafka Integration**

_Tasks:_

- Integrate Kafka consumers for payment and dispensing events
- Implement transaction state management based on events
- Add transaction completion logic
- Create transaction event publishers
- Implement simple compensation logic for failed transactions
- Add transaction history and reporting features

_Deliverables:_

- Event-driven transaction processing
- Transaction state management
- Failure handling and compensation
- Complete transaction workflow

#### **Day 11 - Dispensing Service Implementation**

_Tasks:_

- Create Dispensing Service with hardware simulation
- Design dispensing entities:
  - DispensingOperation (id, transactionId, productId, status)
  - HardwareStatus (operational status simulation)
- Implement dispensing simulation:
  - Configurable success/failure rates
  - Hardware jam simulation
  - Dispensing verification
- Create Kafka consumer for dispensing requests
- Implement Kafka producer for dispensing results
- Add dispensing history and monitoring

_Deliverables:_

- Working Dispensing Service
- Hardware simulation
- Event-driven dispensing
- Operation monitoring

### **Phase 3: System Integration & Enhancement (Days 11-14)**

#### **Day 12 - Notification Service & Event Integration**

_Tasks:_

- Create Notification Service with Kafka integration
- Implement notification entities:
  - Notification (id, type, message, timestamp, status)
  - NotificationType enum (LOW_STOCK, TRANSACTION_FAILED, etc.)
- Set up Kafka consumers for all notification events:
  - Inventory alerts
  - Transaction notifications
  - Payment failures
  - Dispensing issues
- Implement notification logging and storage
- Create admin endpoints for notification management

_Deliverables:_

- Working Notification Service
- Complete event consumption
- Notification storage and retrieval
- System-wide alert handling

#### **Day 13 - End-to-End Integration Testing**

_Tasks:_

- Test complete purchase flow from inventory check to dispensing
- Verify all Kafka event flows work correctly
- Test failure scenarios and error handling
- Validate user authentication and authorization
- Test admin operations across all services
- Performance testing with concurrent requests
- Fix integration issues and improve error handling

_Deliverables:_

- Fully integrated system
- Working end-to-end flows
- Validated error handling
- Performance baseline

#### **Day 14 - System Monitoring & Observability**

_Tasks:_

- Add Spring Boot Actuator to all services
- Implement health checks for each service
- Add custom metrics for business operations:
  - Transaction success rates
  - Inventory levels
  - Payment processing times
  - Dispensing success rates
- Create monitoring endpoints for admin dashboard
- Implement correlation ID tracking across services
- Add structured logging for better debugging

_Deliverables:_

- Comprehensive monitoring
- Health check endpoints
- Business metrics tracking
- Improved observability

#### **Day 15 - Security & Data Validation**

_Tasks:_

- Enhance input validation across all services
- Implement request rate limiting at gateway level
- Add data sanitization for user inputs
- Enhance JWT security with proper secret management
- Implement audit logging for admin operations
- Add security headers and CORS configuration
- Test security measures and fix vulnerabilities

_Deliverables:_

- Enhanced security implementation
- Input validation and sanitization
- Audit logging
- Security testing results

### **Phase 4: Testing & Final Integration (Days 15-17)**

#### **Day 16 - Comprehensive Testing Suite**

_Tasks:_

- Create unit tests for all service layers
- Implement integration tests using local MySQL
- Create Kafka integration tests with test topics
- Build comprehensive Postman collection for all APIs
- Test various failure scenarios:
  - Service unavailability
  - Database connection failures
  - Kafka broker issues
- Document test scenarios and results

_Deliverables:_

- Complete test suite
- Integration test coverage
- API testing collection
- Failure scenario documentation

#### **Day 17 - Performance Optimization & Documentation**

_Tasks:_

- Optimize database queries and connections
- Tune Kafka producer/consumer configurations
- Implement caching for frequently accessed data
- Optimize service startup times
- Create comprehensive API documentation
- Write service setup and configuration guides
- Document Kafka topic schemas and event flows
- Create troubleshooting guide

_Deliverables:_

- Optimized system performance
- Complete documentation
- Setup and deployment guides
- Troubleshooting documentation

#### **Day 18 - Final Integration & Demo Preparation**

_Tasks:_

- Final end-to-end testing of all scenarios
- Create demo data and sample scenarios
- Prepare demo script for system showcase
- Final bug fixes and performance tuning
- Create project presentation and learning outcomes summary
- Document lessons learned and future enhancements
- Prepare handover documentation

_Deliverables:_

- Production-ready demo system
- Demo materials and scripts
- Project documentation
- Learning outcomes summary

---

## Technical Specifications

### Authentication & Security

- **JWT Token Expiry**: 8 hours
- **Supported Roles**: SUPER_ADMIN, ADMIN
- **Password Hashing**: BCrypt with salt
- **Security Headers**: CORS, CSRF protection
- **Rate Limiting**: 100 requests/minute per user

### Kafka Configuration

```yaml
Event Schema:
  transaction-events:
    - transaction.created
    - transaction.completed
    - transaction.failed
  payment-events:
    - payment.initiated
    - payment.completed
    - payment.failed
  inventory-events:
    - stock.updated
    - stock.low
    - product.added
  dispensing-events:
    - dispensing.requested
    - dispensing.completed
    - dispensing.failed
  notification-events:
    - notification.created
    - notification.sent
```

### Database Schema Guidelines

- **Primary Keys**: Auto-increment integers
- **Timestamps**: CreatedAt, UpdatedAt for all entities
- **Soft Deletes**: Where applicable
- **Indexes**: On frequently queried fields
- **Constraints**: Foreign keys and data validation

### API Response Format

```json
{
  "success": true,
  "data": {},
  "message": "Operation completed successfully",
  "timestamp": "2024-01-01T10:00:00Z",
  "correlationId": "uuid"
}
```

---

## Success Criteria

### Functional Requirements

- Complete purchase flow from product selection to dispensing
- Admin authentication and user management
- Real-time inventory tracking and updates
- Simulated payment processing for cash and card
- Item dispensing simulation with failure scenarios
- System-wide notification and alert handling

### Technical Requirements

- All services registered with Eureka
- Configuration managed through Config Server
- JWT authentication at gateway level
- Kafka-based event-driven communication
- MySQL database per service with manual schemas
- Comprehensive logging and monitoring
- REST API with proper error handling

### Performance Requirements

- API response times under 2 seconds
- Support for 50+ concurrent users
- Database queries optimized for common operations
- Kafka message processing under 100ms
- Service startup time under 30 seconds

### Quality Requirements

- 80%+ test coverage for business logic
- Comprehensive API documentation
- Clean, maintainable code following SOLID principles
- Proper error handling and user feedback
- Security best practices implemented

---

## Risk Mitigation

### Technical Risks

- **Kafka Setup Complexity**: Provide detailed setup scripts and documentation
- **Service Communication**: Implement proper timeout and retry mechanisms
- **Database Consistency**: Use proper transaction boundaries
- **JWT Security**: Implement proper secret management and token validation

### Development Risks

- **Time Management**: Daily progress tracking and scope adjustment
- **Integration Issues**: Early and frequent integration testing
- **Learning Curve**: Focus on core concepts first, advanced features later
- **Environment Issues**: Detailed setup documentation and troubleshooting guides

---

## Future Enhancements (Out of Scope)

- Real payment gateway integration
- Advanced caching with Redis
- Container orchestration with Docker/Kubernetes
- Advanced monitoring with Prometheus/Grafana
- Message persistence and replay capabilities
- Advanced security features (OAuth2, refresh tokens)
- Multi-channel notification delivery
- Real hardware integration
- Advanced analytics and reporting
