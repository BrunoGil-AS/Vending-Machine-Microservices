# 📄 Architecture Changes for Vending Machine Control System

## 1. Overview

This document describes the changes made to the **original development plan and architecture** of the Vending Machine Control System. The goal is to align the system design with updated project requirements and development constraints.

---

## 2. Key Changes

### 2.1 Configuration Management

- **Original Plan**: Config Server backed by external Git repository and YAML files.
- **Change**:

  - **Spring Cloud Config Server** is retained (mandatory requirement).
  - Configuration will be stored in **local `application.properties` files** rather than external Git repos.
  - This reduces complexity and makes the system easier to run in a local, single-developer environment.

---

### 2.2 Service Discovery

- **Original Plan**: Eureka Server for dynamic service discovery.
- **Change**:

  - **Eureka Server** remains mandatory.
  - All microservices will register with Eureka.
  - API Gateway will use Eureka for routing.
  - No advanced load-balancing or resilience patterns included.

---

### 2.3 Messaging Backbone

- **Original Plan**: In-memory event handling using **Spring Application Events**.
- **Change**:

  - **Replaced with Apache Kafka** as the messaging backbone.
  - Local Kafka + Zookeeper will run alongside MySQL.
  - Microservices will use **`spring-kafka`** to produce and consume events.
  - Event payloads will be JSON for simplicity.
  - This enables realistic pub/sub with topics, partitions, and consumer groups.

---

### 2.4 Databases

- **Original Plan**: MySQL databases for each service, with optional schema migration tools (Flyway).
- **Change**:

  - Still one **MySQL server** with **separate databases per service**.
  - **Manual schema creation only** (via `.sql` scripts).
  - No Flyway or Liquibase tools to avoid unnecessary complexity.

---

### 2.5 Orchestration & Transactions

- **Original Plan**: Transaction Service with **Saga pattern** for orchestration, including compensation logic and saga persistence.
- **Change**:

  - **Saga orchestration removed**.
  - Transaction Service will orchestrate flows using **Kafka events** in a simpler synchronous + async hybrid approach.
  - Compensation logic simplified: rollback handled directly within services if failures occur.
  - No saga state machine or persistence layer required.

---

### 2.6 Authentication & Security

- **Original Plan**: JWT authentication via API Gateway, with role-based access (SUPER_ADMIN, ADMIN).
- **Change**:

  - **No change in concept**, but simplified implementation:

    - Authentication data stored in **MySQL (`vending_auth`)**.
    - Admin login issues JWT tokens with **8-hour expiry**.
    - Gateway validates JWT and propagates user context headers (`X-User-Id`, `X-User-Role`, `X-Username`).

  - Only the Gateway handles authentication logic; business services remain trust-based.

---

### 2.7 Notification Service

- **Original Plan**: Full-featured notification service with multi-channel support (email, SMS, push).
- **Change**:

  - Service remains in the architecture.
  - Implementation will be **minimal**: consumes Kafka topics and logs notifications.
  - Multi-channel delivery postponed to future enhancements.

---

### 2.8 Testing

- **Original Plan**: Integration tests with **TestContainers** (Docker).
- **Change**:

  - TestContainers removed (Docker not available).
  - Testing strategy:

    - Unit tests (JUnit + Mockito).
    - Integration tests using **local MySQL** and **Kafka test topics**.
    - Manual end-to-end testing with Postman collection.

---

## 3. Updated Architecture Diagram (Conceptual)

**Core Services:**

- Config Server → Eureka Server → API Gateway (JWT)
- Inventory Service
- Payment Service
- Transaction Service
- Dispensing Service
- Notification Service

**Messaging Backbone:**

- Apache Kafka with topics:

  - `transaction-events`
  - `payment-events`
  - `inventory-events`
  - `dispensing-events`
  - `notification-events`

**Databases:**

- One MySQL server with multiple databases:

  - `vending_config`
  - `vending_auth`
  - `vending_inventory`
  - `vending_payment`
  - `vending_transaction`
  - `vending_dispensing`
  - `vending_notification`

---

## 4. Summary of Benefits

- **Simpler configuration** with `application.properties` instead of Git-backed configs.
- **Realistic event-driven system** with Kafka, supporting producers/consumers and topics.
- **Reduced complexity** by removing saga orchestration, audit trails, and multi-channel notifications.
- **Lean setup** compatible with a single-developer local environment.
- **Scalable foundation**: Kafka-based events and Eureka allow future expansion if needed.
