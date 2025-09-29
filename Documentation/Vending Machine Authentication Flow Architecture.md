# Vending Machine Authentication Flow Architecture

## Overview

The Vending Machine Control System implements a **Gateway-Centric Authentication Architecture** using JWT tokens for admin operations while keeping customer transactions completely anonymous. This approach balances security requirements with operational simplicity.

## Technology Stack

### Core Authentication Technologies

- **Spring Cloud Gateway**: API Gateway with WebFlux reactive programming
- **Spring Security**: Authentication and authorization framework
- **JWT (JSON Web Tokens)**: Stateless authentication tokens
- **Spring Boot OAuth2 Resource Server**: JWT token validation
- **R2DBC**: Reactive database connectivity for user management
- **BCrypt**: Password hashing and verification
- **MySQL Database** (Development)

### Supporting Technologies

- **Spring Boot Actuator**: Health checks and monitoring
- **Micrometer**: Metrics collection
- **Logback**: Structured logging with correlation IDs
- **Spring Cloud Config**: Centralized configuration management
- **Eureka Client**: Service discovery integration

## Architecture Components

### 1. API Gateway (Port 8080)

**Primary Responsibilities:**

- JWT token generation and validation
- Route-based security enforcement
- Admin user management (CRUD operations)
- Request routing to microservices
- User context propagation via headers

**Key Features:**

- Reactive WebFlux-based implementation
- Role-based access control (RBAC)
- Hierarchical admin permissions
- Public endpoint bypassing for customer operations

### 2. Authentication Database

**Schema Design:**

```sql
admin_users (
  id SERIAL PRIMARY KEY,
  username VARCHAR(50) UNIQUE NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  role VARCHAR(20) CHECK (role IN ('SUPER_ADMIN', 'ADMIN')),
  enabled BOOLEAN DEFAULT true,
  created_by BIGINT REFERENCES admin_users(id),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
)
```

### 3. Microservices Integration

**Header-Based User Context:**

- `X-User-Id`: Authenticated user identifier
- `X-User-Role`: User role for authorization
- `X-Username`: Username for audit logging

**Security Approach:**

- No authentication logic in business services
- Trust headers from authenticated gateway
- Focus on business logic and data validation

## Role Hierarchy and Permissions

### Role Definitions

1. **SUPER_ADMIN**

   - System-level administrative access
   - Can manage all ADMIN users
   - Full system configuration access
   - Cannot be deleted by other users
   - Complete vending machine control

2. **ADMIN**
   - Operational administrative access
   - Cannot create other users (only SUPER_ADMIN can)
   - Full inventory and transaction management
   - Complete business operations access
   - Hardware control and maintenance

### Permission Matrix

| Operation            | SUPER_ADMIN     | ADMIN |
| -------------------- | --------------- | ----- |
| User Management      | ✓ (ADMIN users) | ✗     |
| System Configuration | ✓               | ✓     |
| Inventory Management | ✓               | ✓     |
| Transaction Reports  | ✓               | ✓     |
| Hardware Control     | ✓               | ✓     |
| Financial Reports    | ✓               | ✓     |
| Machine Maintenance  | ✓               | ✓     |

## Authentication Flows

### 1. Admin Login Flow

```plain
Admin Web Interface → API Gateway
1. POST /auth/login {username, password}
2. Gateway validates credentials against database
3. Gateway generates JWT token (8-hour expiry)
4. Returns {token, tokenType, expiresIn}
```

### 2. Admin Operations Flow

```plain
Admin Request → API Gateway → Microservice
1. Request includes "Authorization: Bearer {jwt_token}"
2. Gateway validates JWT signature and expiry
3. Gateway extracts user context from JWT claims
4. Gateway adds user headers to forwarded request
5. Microservice processes with user context
```

### 3. Customer Transaction Flow

```plain
Customer Interface → API Gateway → Microservice
1. Public endpoints bypass authentication
2. Direct routing to business services
3. Anonymous transaction processing
4. No user context required
```

## Security Implementation Details

### JWT Token Structure

```plain
{
  "iss": "vending-machine-gateway",
  "sub": "admin_username",
  "iat": 1640995200,
  "exp": 1641023800,
  "userId": 123,
  "role": "ADMIN",
  "permissions": ["INVENTORY_WRITE", "REPORTS_READ"]
}
```

### Token Generation Configuration

```yaml
jwt:
  issuer: "vending-machine-gateway"
  expiry: PT8H # 8 hours
  secret: "${JWT_SECRET:default-secret-key-change-in-production}"
  algorithm: HS256
```

### Password Security

- **BCrypt hashing** with strength 12
- **Salt generation** per password
- **Password policy** enforcement (minimum 8 characters, complexity)
- **Account lockout** after 5 failed attempts

### Route Security Configuration

```yaml
public-routes:
  - /auth/login
  - /api/inventory/items
  - /api/transactions/purchase
  - /api/transactions/status/**
  - /actuator/health

protected-routes:
  - /auth/admin/** # Admin management
  - /api/admin/** # Administrative operations
  - /api/reports/** # Reporting endpoints
  - /actuator/** # System monitoring
```

## Error Handling and Security

### Authentication Errors

- **401 Unauthorized**: Invalid or expired JWT token
- **403 Forbidden**: Insufficient role permissions
- **400 Bad Request**: Invalid login credentials
- **429 Too Many Requests**: Rate limiting exceeded

### Security Headers

```yaml
security-headers:
  X-Content-Type-Options: nosniff
  X-Frame-Options: DENY
  X-XSS-Protection: "1; mode=block"
  Strict-Transport-Security: "max-age=31536000; includeSubDomains"
  Cache-Control: "no-cache, no-store, must-revalidate"
```

### Audit Logging

```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "correlationId": "req-123-456",
  "userId": 123,
  "username": "maintenance_user",
  "role": "MAINTENANCE",
  "action": "INVENTORY_UPDATE",
  "resource": "/api/admin/inventory/items/cola-500ml",
  "method": "PUT",
  "status": "SUCCESS",
  "ipAddress": "192.168.1.100",
  "userAgent": "Admin-Dashboard/1.0"
}
```

## High Availability and Scalability

### Gateway Scalability

- **Horizontal scaling**: Multiple gateway instances
- **Load balancing**: Round-robin or least-connections
- **Session management**: Stateless JWT approach
- **Database connection pooling**: Reactive R2DBC pool

### Performance Optimizations

- **In-memory caching**: User details and permissions
- **Connection reuse**: HTTP/2 support
- **Async processing**: Reactive streams throughout
- **Database optimization**: Indexed queries, prepared statements

### Monitoring and Observability

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,info,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

## Deployment Configuration

### Environment-Specific Configuration

```yaml
# application-local.yml
spring:
  r2dbc:
    url: r2dbc:h2:mem:///testdb
  security:
    jwt:
      secret: local-development-secret

# application-production.yml
spring:
  r2dbc:
    url: r2dbc:postgresql://db:5432/vending_machine
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  security:
    jwt:
      secret: ${JWT_SECRET}
```

---

## Benefits of This Architecture

### Security Benefits

- **Centralized authentication**: Single point of security enforcement
- **Stateless design**: Improved scalability and reliability
- **Role-based access**: Granular permission control
- **Audit compliance**: Complete activity logging
- **Token expiry**: Automatic session timeout

### Operational Benefits

- **Simplified microservices**: No authentication code in business logic
- **Easy user management**: Admin self-service capabilities
- **Monitoring integration**: Built-in security metrics
- **Development efficiency**: Single authentication codebase
- **Testing simplicity**: Mock authentication for service tests

### Maintenance Benefits

- **Centralized user management**: Single database for all users
- **Role hierarchy**: Logical permission inheritance
- **Configuration management**: Environment-specific security settings
- **Upgrade path**: Easy migration to OAuth2 if needed
- **Debugging**: Centralized authentication logs

## Future Enhancement Options

### Short-term Enhancements

- **Multi-factor authentication** (TOTP)
- **Password policy enforcement**
- **Session management** dashboard
- **Advanced audit reporting**

### Long-term Considerations

- **OAuth2 Authorization Server** migration
- **LDAP/Active Directory** integration
- **API rate limiting** per user
- **Advanced threat detection**

This architecture provides a robust, scalable authentication solution that meets the specific needs of a vending machine system while maintaining simplicity and development efficiency.
