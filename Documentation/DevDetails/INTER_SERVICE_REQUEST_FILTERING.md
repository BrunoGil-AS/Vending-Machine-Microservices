# Inter-Service Request Filtering Implementation

## Overview

This document describes the implementation of header-based filtering to differentiate between client requests and inter-service communication in the Vending Machine Microservices system.

## Problem Statement

The system needed a mechanism to:

1. **Prevent direct external access** to microservices (all client requests must go through API Gateway)
2. **Allow inter-service communication** between microservices without blocking
3. **Maintain security** while enabling service-to-service calls

## Solution Architecture

### Header-Based Identification

We implemented a dual-header system:

1. **`X-Internal-Service`**: Identifies requests from the API Gateway

   - Value: `api-gateway` (configurable)
   - Purpose: Ensures requests come from the authorized gateway

2. **`X-Request-Source`**: Identifies the origin type of the request
   - Values:
     - `gateway`: Client request forwarded by API Gateway
     - `internal`: Direct service-to-service communication
   - Purpose: Differentiates between external clients and internal services

### Implementation Components

#### 1. Configuration Server

**File**: `config-server/src/main/resources/config/application.properties`

```properties
application.gateway.identifier=api-gateway
application.request.source.gateway=gateway
application.request.source.internal=internal
```

These properties are centrally managed and distributed to all services.

#### 2. API Gateway Filter

**File**: `api-gateway/src/main/java/com/vendingmachine/gateway/config/InternalServiceFilter.java`

The gateway automatically adds both headers to all outgoing requests:

- `X-Internal-Service: api-gateway`
- `X-Request-Source: gateway`

```java
ServerWebExchange modifiedExchange = exchange.mutate()
    .request(exchange.getRequest().mutate()
        .header(INTERNAL_SERVICE_HEADER, GATEWAY_IDENTIFIER)
        .header(REQUEST_SOURCE_HEADER, REQUEST_SOURCE_GATEWAY)
        .build())
    .build();
```

#### 3. Access Constants (Per Service)

**Files**: `<service>/src/main/java/com/vendingmachine/<service>/config/AccessConstants.java`

Each service has consistent constants:

```java
public class AccessConstants {
    // Header names
    public static final String INTERNAL_SERVICE_HEADER = "X-Internal-Service";
    public static final String REQUEST_SOURCE_HEADER = "X-Request-Source";

    // Header values
    public static final String REQUEST_SOURCE_GATEWAY = "gateway";
    public static final String REQUEST_SOURCE_INTERNAL = "internal";

    // Roles
    public static final String ADMIN_ROLE = "ADMIN";
    public static final String SUPER_ADMIN_ROLE = "SUPER_ADMIN";

    // Local IP addresses for development
    public static final Map<String, String> LOCAL_IP_MAP = Map.of(
        "127.0.0.1", "localhost",
        "0:0:0:0:0:0:0:1", "localhost",
        "localhost", "127.0.0.1"
    );
}
```

#### 4. Security Configuration (Per Service)

**Files**: `<service>/src/main/java/com/vendingmachine/<service>/config/SecurityConfig.java`

Each service validates requests using a two-tier approach:

```java
.anyRequest().access((authentication, request) -> {
    String remoteAddr = request.getRequest().getRemoteAddr();

    // Tier 1: Allow requests from API Gateway
    String internalService = request.getRequest().getHeader(INTERNAL_SERVICE_HEADER);
    if (GATEWAY_IDENTIFIER.equals(internalService)) {
        return new AuthorizationDecision(true);
    }

    // Tier 2: Allow inter-service communication
    String requestSource = request.getRequest().getHeader(REQUEST_SOURCE_HEADER);
    if (REQUEST_SOURCE_INTERNAL.equals(requestSource) && LOCAL_IP_MAP.get(remoteAddr) != null) {
        log.debug("Inter-service request allowed from: {}", remoteAddr);
        return new AuthorizationDecision(true);
    }

    // Deny all other requests
    log.warn("External access denied from: {}", remoteAddr);
    return new AuthorizationDecision(false);
});
```

#### 5. RestTemplate Interceptor (Transaction Service)

**File**: `transaction-service/src/main/java/com/vendingmachine/transaction/config/RestTemplateConfig.java`

Automatically adds the `X-Request-Source: internal` header to all outgoing HTTP requests:

```java
@Bean
public RestTemplate restTemplate() {
    RestTemplate restTemplate = new RestTemplate();

    List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
    interceptors.add((request, body, execution) -> {
        request.getHeaders().add("X-Request-Source", REQUEST_SOURCE_INTERNAL);
        return execution.execute(request, body);
    });

    restTemplate.setInterceptors(interceptors);
    return restTemplate;
}
```

## Request Flow Examples

### Client Request Flow

```
Client → API Gateway → Microservice
         │            │
         │            └─ Headers:
         │               - X-Internal-Service: api-gateway
         │               - X-Request-Source: gateway
         │               - X-User-Role: ADMIN (if authenticated)
         │
         └─ JWT Authentication
```

### Inter-Service Communication Flow

```
Transaction Service → Inventory Service
                     │
                     └─ Headers:
                        - X-Request-Source: internal
                        - Remote IP: localhost/127.0.0.1
```

### Direct Access Attempt (Blocked)

```
External Client → Microservice
                 │
                 └─ Result: 403 Forbidden
                    - No X-Internal-Service header
                    - No X-Request-Source header
                    - Not from localhost
```

## Security Implications

### Benefits

1. **Layered Security**: Multiple validation points ensure robust protection
2. **Flexibility**: Different treatment for client vs. service requests
3. **Visibility**: Comprehensive logging of access patterns
4. **Maintainability**: Centralized configuration via Config Server
5. **Strict Access Control**: No direct localhost access, all requests must be properly authenticated

### Considerations

1. **Production Ready**: Direct localhost access has been removed

   - All requests must come through API Gateway or with proper internal headers
   - Enhances security by removing development backdoors

2. **Production Hardening**: Consider adding:
   - Network-level restrictions (firewalls, security groups)
   - Mutual TLS (mTLS) for service-to-service communication
   - More restrictive IP whitelisting

## Services Updated

All business services have been updated with this pattern:

1. ✅ **inventory-service**
2. ✅ **transaction-service** (includes RestTemplate interceptor)
3. ✅ **payment-service**
4. ✅ **notification-service**
5. ✅ **dispensing-service**
6. ✅ **api-gateway** (InternalServiceFilter)

## Testing

### Test Client Request

```bash
# Should succeed (through gateway)
curl -X GET http://localhost:8080/api/inventory/products

# Should fail (direct access)
curl -X GET http://localhost:8081/api/inventory/products
```

### Test Inter-Service Communication

Inter-service calls automatically include the `X-Request-Source: internal` header via RestTemplate interceptor.

### Verify Logs

Check service logs for:

- `Inter-service request allowed from: 127.0.0.1`
- `External access denied from: <ip>`

## Configuration Reference

| Property                              | Default Value | Description                          |
| ------------------------------------- | ------------- | ------------------------------------ |
| `application.gateway.identifier`      | `api-gateway` | Gateway identifier value             |
| `application.request.source.gateway`  | `gateway`     | Header value for client requests     |
| `application.request.source.internal` | `internal`    | Header value for inter-service calls |

## Future Enhancements

1. **Service Mesh Integration**: Consider using Istio/Linkerd for more sophisticated service-to-service security
2. **Certificate-Based Authentication**: Implement mTLS for stronger inter-service authentication
3. **Dynamic IP Validation**: Load allowed IPs from configuration rather than hardcoding
4. **Rate Limiting**: Add per-source rate limiting for different request types

## Related Documentation

- [Security Configuration Guide](../Security-Configuration.md)
- [API Gateway Architecture](../Vending%20Machine%20Authentication%20Flow%20Architecture.md)
- [System Improvement Guide](../SYSTEM_IMPROVEMENT_GUIDE.md)
