# Vending Machine Control System

A microservices-based vending machine control system built with Spring Boot, Spring Cloud, Apache Kafka, and MySQL.

## 🏗️ Architecture Overview

### Core Services

- **Config Server** (Port 8888) - Centralized configuration management
- **Eureka Server** (Port 8761) - Service discovery and registration
- **API Gateway** (Port 8080) - Authentication and routing
- **Inventory Service** (Port 8081) - Product and stock management
- **Payment Service** (Port 8082) - Payment processing simulation
- **Transaction Service** (Port 8083) - Purchase orchestration
- **Dispensing Service** (Port 8084) - Item dispensing simulation
- **Notification Service** (Port 8085) - System alerts and notifications

### Infrastructure

- **Apache Kafka** (Port 9092) - Event-driven messaging
- **Zookeeper** (Port 2181) - Kafka coordination
- **MySQL** (Port 3306) - Database per service

## 📋 Prerequisites

- Java 17 or higher
- Spring Boot 3.5.6
- Maven 3.8 or higher
- MySQL 8.0 or higher
- Apache Kafka 3.5 or higher (with Zookeeper)
- At least 6GB RAM available

## 🚀 Quick Start

### 1. Clone the Repository

```bash
git clone <repository-url>
cd vending-machine-system
```

### 2. Set Up MySQL Databases

```bash
mysql -u root -p < scripts/create-databases.sql
```

Or manually create databases:

```sql
CREATE DATABASE vending_config;
CREATE DATABASE vending_auth;
CREATE DATABASE vending_inventory;
CREATE DATABASE vending_payment;
CREATE DATABASE vending_transaction;
CREATE DATABASE vending_dispensing;
CREATE DATABASE vending_notification;
```

### 3. Start Kafka and Zookeeper

```bash
# Start Zookeeper
bin/zookeeper-server-start.sh config/zookeeper.properties

# Start Kafka (in a new terminal)
bin/kafka-server-start.sh config/server.properties
```

### 4. Build the Project

```bash
# Using Maven
mvn clean install

# Or using the build script
chmod +x build.sh
./build.sh
```

### 5. Start Services

Start services in this order:

```bash
# 1. Config Server
java -jar config-server/target/config-server-1.0.0-SNAPSHOT.jar

# 2. Eureka Server (wait 30 seconds)
java -jar eureka-server/target/eureka-server-1.0.0-SNAPSHOT.jar

# 3. API Gateway (wait 30 seconds)
java -jar api-gateway/target/api-gateway-1.0.0-SNAPSHOT.jar

# 4. Business Services (can be started in parallel)
java -jar inventory-service/target/inventory-service-1.0.0-SNAPSHOT.jar
java -jar payment-service/target/payment-service-1.0.0-SNAPSHOT.jar
java -jar transaction-service/target/transaction-service-1.0.0-SNAPSHOT.jar
java -jar dispensing-service/target/dispensing-service-1.0.0-SNAPSHOT.jar
java -jar notification-service/target/notification-service-1.0.0-SNAPSHOT.jar
```

## 📁 Project Structure

```plaintext
vending-machine-system/
├── common-library/          # Shared DTOs and utilities
├── config-server/           # Configuration management
├── eureka-server/           # Service discovery
├── api-gateway/             # API Gateway with JWT auth
├── inventory-service/       # Inventory management
├── payment-service/         # Payment processing
├── transaction-service/     # Transaction orchestration
├── dispensing-service/      # Item dispensing
├── notification-service/    # Notifications and alerts
├── pom.xml                  # Root POM
├── build.sh                 # Build script
└── README.md               # This file
```

## 🔧 Configuration

### Database Configuration

Each service has its own database. Update `application.properties` in each service:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/vending_<service>
spring.datasource.username=root
spring.datasource.password=your_password
```

### Kafka Configuration

Default configuration uses localhost:9092. Update if needed:

```properties
spring.kafka.bootstrap-servers=localhost:9092
```

## 🧪 Testing

### Run All Tests

```bash
mvn test
```

### Run Tests for Specific Service

```bash
cd inventory-service
mvn test
```

### Using Postman

Import the Postman collection from `postman/vending-machine-collection.json`

## 📊 Monitoring

### Eureka Dashboard

<http://localhost:8761>

### Actuator Endpoints

- Config Server: <http://localhost:8888/actuator/health>
- Eureka Server: <http://localhost:8761/actuator/health>
- API Gateway: <http://localhost:8080/actuator/health>
- Inventory Service: <http://localhost:8081/actuator/health>

## 🔐 Authentication

### Default Admin User

Create an admin user via API Gateway:

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "admin123",
    "role": "SUPER_ADMIN"
  }'
```

### Login

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "admin123"
  }'
```

Use the returned JWT token in subsequent requests:

```bash
curl -X GET http://localhost:8080/api/admin/inventory/products \
  -H "Authorization: Bearer <your-jwt-token>"
```

## 📝 API Endpoints

### Public Endpoints

- `GET /api/inventory/products` - List all products
- `POST /api/transaction/purchase` - Make a purchase
- `POST /api/auth/login` - Admin login

### Admin Endpoints (Requires JWT)

- `POST /api/admin/inventory/products` - Add new product
- `PUT /api/admin/inventory/stock/{productId}` - Update stock
- `GET /api/admin/transaction/history` - View transaction history
- `GET /api/admin/payment/transactions` - View payment transactions
- `POST /api/admin/users` - Create admin user

## 🔄 Kafka Topics

- `transaction-events` - Transaction lifecycle events
- `payment-events` - Payment processing events
- `inventory-events` - Stock level changes
- `dispensing-events` - Item dispensing results
- `notification-events` - System alerts and notifications



## 📚 Documentation

- [Development Plan](docs/development-plan.md)



## 👥 Authors

- Bruno Gil Ramirez
