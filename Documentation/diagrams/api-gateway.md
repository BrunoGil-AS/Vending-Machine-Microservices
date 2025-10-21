# API Gateway Diagrams

## Table of Contents

- [Service Context Diagram](#service-context-diagram)
- [Component Diagram](#component-diagram)
- [Authentication Flow](#authentication-flow)
- [Authorization Matrix](#authorization-matrix)
- [Entity Relationship Diagram](#entity-relationship-diagram)
- [Sequence Diagrams](#sequence-diagrams)

---

## Service Context Diagram

```mermaid
graph TB
    subgraph "External Clients"
        CUSTOMER[Customer/End User]
        ADMIN[Administrator]
    end

    subgraph "API Gateway - Port 8080<br/>Entry Point & Security"
        AUTH_FILTER[JWT Authentication Filter]
        AUTHZ_FILTER[Authorization Filter]
        ROUTER[Route Configuration]
        USER_SERVICE[User Service<br/>Auth Logic]
        USER_REPO[User Repository]
    end

    subgraph "Microservices (Eureka Registered)"
        INVENTORY[Inventory Service<br/>8081]
        PAYMENT[Payment Service<br/>8082]
        TRANSACTION[Transaction Service<br/>8083]
        DISPENSING[Dispensing Service<br/>8084]
        NOTIFICATION[Notification Service<br/>8085]
    end

    subgraph "Infrastructure"
        EUREKA[Eureka Server<br/>8761]
        DB[(MySQL<br/>vending_auth)]
    end

    CUSTOMER -->|HTTP Request| AUTH_FILTER
    ADMIN -->|HTTP Request| AUTH_FILTER

    AUTH_FILTER --> USER_SERVICE
    USER_SERVICE --> USER_REPO
    USER_REPO <--> DB

    AUTH_FILTER --> AUTHZ_FILTER
    AUTHZ_FILTER --> ROUTER

    ROUTER -->|Route /inventory/**| INVENTORY
    ROUTER -->|Route /payment/**| PAYMENT
    ROUTER -->|Route /transaction/**| TRANSACTION
    ROUTER -->|Route /dispensing/**| DISPENSING
    ROUTER -->|Route /notification/**| NOTIFICATION

    ROUTER --> EUREKA

    style AUTH_FILTER fill:#ffccbc
    style AUTHZ_FILTER fill:#ffccbc
```

---

## Component Diagram

```mermaid
graph TB
    subgraph "Gateway Filter Chain"
        PRE_FILTER[Pre-Filters<br/>Logging, CORS]
        AUTH_FILTER[JWT Auth Filter]
        AUTHZ_FILTER[Authorization Filter]
        POST_FILTER[Post-Filters<br/>Response Headers]
    end

    subgraph "Authentication Module"
        USER_SERVICE[User Service]
        JWT_UTIL[JWT Token Util<br/>8hr Expiry]
        PASSWORD_ENCODER[BCrypt Password Encoder]
    end

    subgraph "Authorization Module"
        ROLE_CHECKER[Role-Based Access Control]
        PERMISSION_VALIDATOR[Permission Validator]
    end

    subgraph "Routing Module"
        ROUTE_CONFIG[Route Configuration]
        LOAD_BALANCER[Load Balancer]
        EUREKA_CLIENT[Eureka Client]
    end

    subgraph "User Management"
        USER_CONTROLLER[User Controller<br/>CRUD Operations]
        USER_REPO[User Repository]
    end

    subgraph "Domain Model"
        USER[User Entity<br/>Username, Password, Role]
        ROLE[Role Enum<br/>SUPER_ADMIN, ADMIN]
    end

    PRE_FILTER --> AUTH_FILTER
    AUTH_FILTER --> USER_SERVICE
    AUTH_FILTER --> JWT_UTIL

    USER_SERVICE --> PASSWORD_ENCODER
    USER_SERVICE --> USER_REPO

    AUTH_FILTER --> AUTHZ_FILTER
    AUTHZ_FILTER --> ROLE_CHECKER
    ROLE_CHECKER --> PERMISSION_VALIDATOR

    AUTHZ_FILTER --> ROUTE_CONFIG
    ROUTE_CONFIG --> LOAD_BALANCER
    LOAD_BALANCER --> EUREKA_CLIENT

    ROUTE_CONFIG --> POST_FILTER

    USER_CONTROLLER --> USER_SERVICE
    USER_REPO <--> USER
    USER --> ROLE

    style AUTH_FILTER fill:#ffccbc
    style AUTHZ_FILTER fill:#ffccbc
    style JWT_UTIL fill:#fff9c4
```

---

## Authentication Flow

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant Gateway as API Gateway
    participant AuthFilter as JWT Auth Filter
    participant UserService as User Service
    participant JwtUtil as JWT Utility
    participant DB as MySQL Database
    participant Backend as Backend Service

    User->>Gateway: POST /auth/login<br/>{username, password}
    Gateway->>UserService: authenticate(credentials)

    UserService->>DB: findByUsername(username)
    DB-->>UserService: User entity

    UserService->>UserService: Verify BCrypt password

    alt Password Valid
        UserService->>JwtUtil: generateToken(user)
        JwtUtil->>JwtUtil: Create JWT<br/>8hr expiry<br/>Claims: userId, role
        JwtUtil-->>UserService: JWT token
        UserService-->>Gateway: AuthResponse + token
        Gateway-->>User: 200 OK<br/>{token, userId, role}
    else Password Invalid
        UserService-->>Gateway: Authentication failed
        Gateway-->>User: 401 Unauthorized
    end

    Note over User: User makes authenticated request

    User->>Gateway: GET /api/admin/inventory/**<br/>Header: Authorization: Bearer {token}
    Gateway->>AuthFilter: Extract token

    AuthFilter->>JwtUtil: validateToken(token)

    alt Token Valid
        JwtUtil-->>AuthFilter: Claims (userId, role)
        AuthFilter->>AuthFilter: Add headers:<br/>X-User-Id, X-User-Role, X-Username
        AuthFilter->>Backend: Forward request + headers
        Backend-->>Gateway: Response
        Gateway-->>User: 200 OK
    else Token Invalid/Expired
        AuthFilter-->>Gateway: Token validation failed
        Gateway-->>User: 401 Unauthorized
    end
```

---

## Authorization Matrix

### Role Permissions

| Endpoint Pattern              | HTTP Method  | SUPER_ADMIN | ADMIN | Public |
| ----------------------------- | ------------ | ----------- | ----- | ------ |
| `/auth/login`                 | POST         | ✅          | ✅    | ✅     |
| `/auth/register`              | POST         | ✅          | ❌    | ❌     |
| `/api/transaction/purchase`   | POST         | ✅          | ✅    | ✅     |
| `/api/inventory/products`     | GET          | ✅          | ✅    | ✅     |
| `/api/admin/inventory/**`     | ALL          | ✅          | ✅    | ❌     |
| `/api/admin/payment/**`       | ALL          | ✅          | ✅    | ❌     |
| `/api/admin/transaction/**`   | ALL          | ✅          | ✅    | ❌     |
| `/api/admin/dispensing/**`    | ALL          | ✅          | ✅    | ❌     |
| `/api/admin/notifications/**` | ALL          | ✅          | ✅    | ❌     |
| `/api/admin/users/**`         | GET, PUT     | ✅          | ✅    | ❌     |
| `/api/admin/users/**`         | POST, DELETE | ✅          | ❌    | ❌     |

### Route Configuration

```yaml
Routes:
  - id: inventory-service
    uri: lb://inventory-service
    predicates: Path=/api/inventory/**, /api/admin/inventory/**
    filters: AuthenticationFilter

  - id: payment-service
    uri: lb://payment-service
    predicates: Path=/api/payment/**, /api/admin/payment/**
    filters: AuthenticationFilter

  - id: transaction-service
    uri: lb://transaction-service
    predicates: Path=/api/transaction/**, /api/admin/transaction/**
    filters: AuthenticationFilter

  - id: dispensing-service
    uri: lb://dispensing-service
    predicates: Path=/api/dispensing/**, /api/admin/dispensing/**
    filters: AuthenticationFilter

  - id: notification-service
    uri: lb://notification-service
    predicates: Path=/api/admin/notifications/**
    filters: AuthenticationFilter
```

---

## Entity Relationship Diagram

```mermaid
erDiagram
    USERS {
        BIGINT id PK "Auto-increment"
        VARCHAR(50) username UK "Unique username"
        VARCHAR(255) password "BCrypt hashed"
        VARCHAR(20) role "SUPER_ADMIN, ADMIN"
        TIMESTAMP created_at "Registration time"
        TIMESTAMP updated_at "Update time"
    }
```

### Database Schema

```sql
CREATE DATABASE vending_auth;

CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Default admin user
INSERT INTO users (username, password, role)
VALUES ('admin', '$2a$10$...', 'SUPER_ADMIN');
```

---

## Sequence Diagrams

### User Registration (SUPER_ADMIN Only)

```mermaid
sequenceDiagram
    autonumber
    actor SuperAdmin
    participant Gateway
    participant UserController
    participant UserService
    participant PasswordEncoder
    participant UserRepo
    participant DB

    SuperAdmin->>Gateway: POST /auth/register<br/>Header: Authorization: Bearer {token}
    Gateway->>Gateway: Validate JWT<br/>Check SUPER_ADMIN role

    alt SUPER_ADMIN
        Gateway->>UserController: register(request)
        UserController->>UserService: createUser(dto)

        UserService->>UserRepo: existsByUsername(username)
        UserRepo-->>UserService: false

        UserService->>PasswordEncoder: encode(password)
        PasswordEncoder-->>UserService: BCrypt hash

        UserService->>UserRepo: save(user)
        UserRepo->>DB: INSERT
        DB-->>UserRepo: User entity

        UserRepo-->>UserService: Saved user
        UserService-->>UserController: UserDTO
        UserController-->>Gateway: 201 Created
        Gateway-->>SuperAdmin: User created
    else Not SUPER_ADMIN
        Gateway-->>SuperAdmin: 403 Forbidden
    end
```

### Failed Authentication

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant Gateway
    participant UserService
    participant DB

    User->>Gateway: POST /auth/login<br/>{username, password}
    Gateway->>UserService: authenticate(credentials)

    UserService->>DB: findByUsername(username)

    alt User Not Found
        DB-->>UserService: null
        UserService-->>Gateway: UserNotFoundException
        Gateway-->>User: 401 Invalid credentials
    else Wrong Password
        DB-->>UserService: User entity
        UserService->>UserService: Verify password<br/>BCrypt mismatch
        UserService-->>Gateway: BadCredentialsException
        Gateway-->>User: 401 Invalid credentials
    end
```

### Token Expiration Handling

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant Gateway
    participant AuthFilter
    participant JwtUtil

    Note over User: Token issued 9 hours ago (expired)

    User->>Gateway: GET /api/admin/inventory/products<br/>Authorization: Bearer {expired_token}
    Gateway->>AuthFilter: Extract token

    AuthFilter->>JwtUtil: validateToken(token)
    JwtUtil->>JwtUtil: Check expiration<br/>exp claim < current time
    JwtUtil-->>AuthFilter: ExpiredJwtException

    AuthFilter-->>Gateway: Token expired
    Gateway-->>User: 401 Unauthorized<br/>"JWT token expired"

    Note over User: User must login again
```

---

## API Endpoints

### Public Endpoints

#### User Login

- **Endpoint**: `POST /auth/login`
- **Auth**: None
- **Request**:

```json
{
  "username": "admin",
  "password": "admin123"
}
```

- **Response**:

```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "userId": 1,
  "username": "admin",
  "role": "SUPER_ADMIN",
  "expiresIn": 28800
}
```

### Admin Endpoints

#### Register New User

- **Endpoint**: `POST /auth/register`
- **Auth**: JWT (SUPER_ADMIN only)
- **Request**:

```json
{
  "username": "manager1",
  "password": "secure123",
  "role": "ADMIN"
}
```

#### Get All Users

- **Endpoint**: `GET /api/admin/users`
- **Auth**: JWT (ADMIN or SUPER_ADMIN)

#### Update User

- **Endpoint**: `PUT /api/admin/users/{id}`
- **Auth**: JWT (ADMIN or SUPER_ADMIN)

#### Delete User

- **Endpoint**: `DELETE /api/admin/users/{id}`
- **Auth**: JWT (SUPER_ADMIN only)

---

## Security Configuration

### JWT Configuration

```properties
jwt.secret=your-256-bit-secret-key
jwt.expiration=28800000  # 8 hours in milliseconds
jwt.header=Authorization
jwt.prefix=Bearer
```

### Password Encoding

- **Algorithm**: BCrypt
- **Strength**: 10 rounds
- **Salt**: Auto-generated per password

### CORS Configuration

```java
@Bean
public CorsWebFilter corsWebFilter() {
    return new CorsWebFilter(exchange -> {
        allowedOrigins: ["http://localhost:3000"]
        allowedMethods: ["GET", "POST", "PUT", "DELETE", "OPTIONS"]
        allowedHeaders: ["*"]
        allowCredentials: true
        maxAge: 3600
    });
}
```

---

## Performance Characteristics

- **JWT Validation**: < 10ms
- **Password Verification**: < 100ms (BCrypt)
- **Route Resolution**: < 5ms
- **Eureka Lookup**: < 20ms
- **Total Gateway Overhead**: < 150ms

---

## Conclusion

API Gateway provides centralized authentication, authorization, and routing for all microservices. It implements JWT-based security with role-based access control and integrates with Eureka for dynamic service discovery.
