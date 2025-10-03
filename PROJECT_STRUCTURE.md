# Vending Machine System - Project Structure

## Complete Directory Structure

````plaintext
VENDING MACHINE/
├── .gitignore
├── PROJECT_STRUCTURE.md
├── README.md
├── Documentation/
└── vending-machine-system/
    ├── pom.xml                          # Root POM with dependency management
    ├── build.sh                         # Build script
    ├── start-services.sh                # Service startup script
    ├── stop-services.sh                 # Service shutdown script
    │
    ├── scripts/                         # Utility scripts
    │   └── create-databases.sql         # Database creation script
    │
    ├── logs/                            # Service logs (created at runtime)
    │   ├── config-server.log
    │   ├── eureka-server.log
    │   └── ...
    │
    ├── common-library/                  # Shared library module
    │   ├── pom.xml
    │   └── src/
    │       ├── main/
    │       │   ├── java/
    │       │   │   └── com/vendingmachine/common/
    │       │   │       ├── dto/         # Data Transfer Objects
    │       │   │       ├── exception/   # Custom exceptions
    │       │   │       ├── util/        # Utility classes
    │       │   │       └── constant/    # Constants
    │       │   └── resources/
    │       └── test/
    │           └── java/
    │
    ├── config-server/                   # Configuration Server
    │   ├── pom.xml
    │   └── src/
    │       ├── main/
    │       │   ├── java/
    │       │   │   └── com/vendingmachine/config/
    │       │   │       └── ConfigServerApplication.java
    │       │   └── resources/
    │       │       ├── application.properties
    │       │       └── config/          # Service configurations
    │       │           ├── inventory-service.properties
    │       │           ├── payment-service.properties
    │       │           ├── transaction-service.properties
    │       │           ├── dispensing-service.properties
    │       │           └── notification-service.properties
    │       └── test/
    │
    ├── eureka-server/                   # Service Discovery
    │   ├── pom.xml
    │   └── src/
    │       ├── main/
    │       │   ├── java/
    │       │   │   └── com/vendingmachine/eureka/
    │       │   │       └── EurekaServerApplication.java
    │       │   └── resources/
    │       │       └── application.properties
    │       └── test/
    │
    ├── api-gateway/                     # API Gateway with Authentication (Feature-Based)
    │   ├── pom.xml
    │   └── src/
    │       ├── main/
    │       │   ├── java/
    │       │   │   └── com/vendingmachine/gateway/
    │       │   │       ├── ApiGatewayApplication.java
    │       │   │       ├── config/          # Gateway configuration
    │       │   │       ├── exception/       # Custom exceptions
    │       │   │       └── User/            # User feature module
    │       │   │           ├── Auth/
    │       │   │           ├── DTO/
    │       │   │           ├── JWT/
    │       │   │           └── Login/
    │       │   └── resources/
    │       │       ├── application.properties
    │       │       └── db/
    │       │           └── init-auth-database.sql
    │       └── test/
    │
    ├── inventory-service/               # Inventory Management Service (Feature-Based)
    │   ├── pom.xml
    │   └── src/
    │       ├── main/
    │       │   ├── java/
    │       │   │   └── com/vendingmachine/inventory/
    │       │   │       ├── InventoryServiceApplication.java
    │       │   │       ├── product/         # Product feature
    │       │   │       ├── stock/           # Stock feature
    │       │   │       ├── kafka/           # Kafka producers/consumers
    │       │   │       └── config/          # Service configuration
    │       │   └── resources/
    │       │       ├── application.properties
    │       │       └── db/
    │       └── test/
    │
    ├── payment-service/                 # Payment Processing Service (Feature-Based)
    │   ├── pom.xml
    │   └── src/
    │       ├── main/
    │       │   ├── java/
    │       │   │   └── com/vendingmachine/payment/
    │       │   │       ├── PaymentServiceApplication.java
    │       │   │       ├── payment/         # Payment feature
    │       │   │       ├── kafka/           # Kafka producers/consumers
    │       │   │       └── config/          # Service configuration
    │       │   └── resources/
    │       │       └── application.properties
    │       └── test/
    │
    ├── transaction-service/             # Transaction Orchestration Service (Feature-Based)
    │   ├── pom.xml
    │   └── src/
    │       ├── main/
    │       │   ├── java/
    │       │   │   └── com/vendingmachine/transaction/
    │       │   │       ├── TransactionServiceApplication.java
    │       │   │       ├── transaction/     # Transaction feature
    │       │   │       ├── kafka/           # Kafka producers/consumers
    │       │   │       └── config/          # Service configuration
    │       │   └── resources/
    │       │       └── application.properties
    │       └── test/
    │
    ├── dispensing-service/              # Item Dispensing Service (Feature-Based)
    │   ├── pom.xml
    │   └── src/
    │       ├── main/
    │       │   ├── java/
    │       │   │   └── com/vendingmachine/dispensing/
    │       │   │       ├── DispensingServiceApplication.java
    │       │   │       ├── dispensing/      # Dispensing feature
    │       │   │       ├── kafka/           # Kafka producers/consumers
    │       │   │       └── config/          # Service configuration
    │       │   └── resources/
    │       │       └── application.properties
    │       └── test/
    │
    └── notification-service/            # Notification Service (Feature-Based)
        ├── pom.xml
        └── src/
            ├── main/
            │   ├── java/
            │   │   └── com/vendingmachine/notification/
            │   │       ├── NotificationServiceApplication.java
            │   │       ├── notification/    # Notification feature
            │   │       ├── kafka/           # Kafka consumers
            │   │       └── config/          # Service configuration
            │   └── resources/
            │       └── application.properties
            └── test/
    ```

    ## Module Descriptions

    ### Infrastructure Modules

    #### 1. **common-library**

    Shared code and utilities used across all services.

    **Package Structure:**

    ```plaintext
    common/
    ├── dto/                 # Shared Data Transfer Objects
    │   ├── ApiResponse.java
    │   ├── ProductDTO.java
    │   ├── TransactionDTO.java
    │   ├── PaymentDTO.java
    │   └── ErrorResponse.java
    ├── exception/           # Custom Exception Classes
    │   ├── BusinessException.java
    │   ├── ResourceNotFoundException.java
    │   ├── ValidationException.java
    │   └── ServiceException.java
    ├── util/                # Utility Classes
    │   ├── DateUtils.java
    │   ├── ValidationUtils.java
    │   └── JsonUtils.java
    └── constant/            # Constants and Enums
        ├── PaymentMethod.java
        ├── TransactionStatus.java
        ├── NotificationType.java
        └── ApiConstants.java
```

### 2. **config-server** (Port 8888)

Centralized configuration management using Spring Cloud Config.

**Key Features:**

- Local file-based configuration
- Service-specific property files
- Configuration refresh capability

**Directory Structure:**

```plaintext
config-server/
└── src/main/resources/
    ├── application.properties       # Config server settings
    └── config/                      # Service configurations
        ├── inventory-service.properties
        ├── payment-service.properties
        ├── transaction-service.properties
        ├── dispensing-service.properties
        └── notification-service.properties
```

#### 3. **eureka-server** (Port 8761)

Service discovery and registration using Netflix Eureka.

**Key Features:**

- Service registration
- Health monitoring
- Load balancing metadata
- Service instance discovery

#### 4. **api-gateway** (Port 8080)

Single entry point with authentication and routing. Now structured by feature.

**Key Components (Feature-Based):**

```plaintext
gateway/
├── config/                  # Gateway configuration (routes, etc.)
│   └── GatewayConfig.java
├── exception/               # Custom exception classes
└── User/                    # User feature module
    ├── AdminUser.java
    ├── AdminUserRepository.java
    ├── Auth/
    │   ├── AuthController.java  # Handles user registration/authentication
    │   └── AuthService.java
    ├── DTO/                   # User-related DTOs
    ├── JWT/
    │   ├── JwtAuthenticationFilter.java # JWT validation filter
    │   └── JwtUtil.java         # JWT generation and validation utility
    └── Login/
        └── DTO/
            ├── LoginRequest.java
            └── LoginResponse.java
```

### Business Service Modules

#### 5. **inventory-service** (Port 8081)

Manages product catalog and stock levels.

**Responsibilities:**

- Product CRUD operations
- Stock level tracking
- Low stock alerts
- Inventory event publishing

**Key Components (Feature-Based):**

```plaintext
inventory/
├── product/
│   ├── ProductController.java
│   ├── ProductService.java
│   ├── Product.java
│   └── ProductRepository.java
├── stock/
│   ├── StockController.java
│   ├── StockService.java
│   ├── Stock.java
│   └── StockRepository.java
└── kafka/
    ├── InventoryEventProducer.java
    └── DispensingEventConsumer.java
```

**Database Tables:**

- `products` - Product catalog
- `stock` - Stock levels and thresholds

#### 6. **payment-service** (Port 8082)

Simulates payment processing for cash and card payments.

**Responsibilities:**

- Payment processing simulation
- Transaction logging
- Payment status tracking
- Payment event publishing

**Key Components (Feature-Based):**

```plaintext
payment/
├── payment/
│   ├── PaymentController.java
│   ├── PaymentService.java
│   ├── PaymentSimulationService.java
│   ├── PaymentTransaction.java
│   └── PaymentTransactionRepository.java
└── kafka/
    └── PaymentEventProducer.java
```

**Database Tables:**

- `payment_transactions` - Payment records

#### 7. **transaction-service** (Port 8083)

Orchestrates the complete purchase flow.

**Responsibilities:**

- Purchase flow coordination
- Inventory availability checks
- Payment coordination
- Transaction state management
- Compensation logic

**Key Components (Feature-Based):**

```plaintext
transaction/
├── transaction/
│   ├── TransactionController.java
│   ├── TransactionService.java
│   ├── TransactionOrchestrationService.java
│   ├── Transaction.java
│   ├── TransactionItem.java
│   ├── TransactionRepository.java
│   └── TransactionItemRepository.java
└── kafka/
    ├── TransactionEventProducer.java
    ├── PaymentEventConsumer.java
    └── DispensingEventConsumer.java
```

**Database Tables:**

- `transactions` - Order records
- `transaction_items` - Order line items

#### 8. **dispensing-service** (Port 8084)

Simulates hardware dispensing operations.

**Responsibilities:**

- Dispensing simulation
- Hardware status simulation
- Success/failure rate configuration
- Dispensing result reporting

**Key Components (Feature-Based):**

```plaintext
dispensing/
├── dispensing/
│   ├── DispensingController.java
│   ├── DispensingService.java
│   ├── HardwareSimulationService.java
│   ├── DispensingOperation.java
│   └── DispensingOperationRepository.java
└── kafka/
    ├── DispensingEventConsumer.java
    └── DispensingEventProducer.java
```

**Database Tables:**

- `dispensing_operations` - Dispensing records

#### 9. **notification-service** (Port 8085)

Handles system-wide notifications and alerts.

**Responsibilities:**

- Event consumption from all services
- Notification storage
- Alert management
- System status notifications

**Key Components (Feature-Based):**

```plaintext
notification/
├── notification/
│   ├── NotificationController.java
│   ├── NotificationService.java
│   ├── Notification.java
│   └── NotificationRepository.java
└── kafka/
    ├── InventoryEventConsumer.java
    ├── PaymentEventConsumer.java
    ├── TransactionEventConsumer.java
    └── DispensingEventConsumer.java
```

**Database Tables:**

- `notifications` - System notifications

## Build and Startup Order

### Build Order

1. **common-library** (must be built first)
2. Infrastructure services (parallel):
   - config-server
   - eureka-server
   - api-gateway
3. Business services (parallel):
   - inventory-service
   - payment-service
   - transaction-service
   - dispensing-service
   - notification-service

### Startup Order

1. **config-server** → Wait 30 seconds
2. **eureka-server** → Wait 30 seconds
3. **api-gateway** → Wait 30 seconds
4. Business services (can start in parallel):
   - inventory-service
   - payment-service
   - transaction-service
   - dispensing-service
   - notification-service

## Port Allocation Summary

| Service              | Port | Purpose                      |
| -------------------- | ---- | ---------------------------- |
| Config Server        | 8888 | Configuration management     |
| Eureka Server        | 8761 | Service discovery            |
| API Gateway          | 8080 | Entry point & authentication |
| Inventory Service    | 8081 | Inventory management         |
| Payment Service      | 8082 | Payment processing           |
| Transaction Service  | 8083 | Transaction orchestration    |
| Dispensing Service   | 8084 | Item dispensing              |
| Notification Service | 8085 | Notifications                |
| MySQL                | 3306 | Database                     |
| Kafka                | 9092 | Message broker               |
| Zookeeper            | 2181 | Kafka coordination           |

## Database Allocation

| Service              | Database             | Tables                |
| -------------------- | -------------------- | --------------------- |
| Config Server        | vending_config       | Config data           |
| API Gateway          | vending_auth         | Users, tokens         |
| Inventory Service    | vending_inventory    | Products, stock       |
| Payment Service      | vending_payment      | Payment transactions  |
| Transaction Service  | vending_transaction  | Transactions, items   |
| Dispensing Service   | vending_dispensing   | Dispensing operations |
| Notification Service | vending_notification | Notifications         |

## Kafka Topics

| Topic               | Partitions | Purpose               |
| ------------------- | ---------- | --------------------- |
| transaction-events  | 3          | Transaction lifecycle |
| payment-events      | 3          | Payment processing    |
| inventory-events    | 3          | Stock changes         |
| dispensing-events   | 3          | Dispensing results    |
| notification-events | 3          | System alerts         |

## Maven Commands Reference

```bash
# Validate project structure
mvn validate

# Clean build
mvn clean install

# Build without tests
mvn clean install -DskipTests

# Run tests only
mvn test

# Package specific module
cd inventory-service && mvn clean package

# Update dependencies
mvn versions:display-dependency-updates

# Dependency tree
mvn dependency:tree

# Run specific service
java -jar inventory-service/target/inventory-service-1.0.0-SNAPSHOT.jar
```

## Important Notes

1. **Common Library**: Must be built and installed before any other service
2. **Service Dependencies**: All business services depend on common-library
3. **Configuration**: Each service gets config from config-server
4. **Service Discovery**: All services register with Eureka
5. **Authentication**: Only API Gateway handles JWT authentication
6. **Database**: Each service has its own database (database-per-service pattern)
7. **Messaging**: All inter-service communication uses Kafka events
8. **Logging**: All logs go to `logs/` directory with service-specific files
````