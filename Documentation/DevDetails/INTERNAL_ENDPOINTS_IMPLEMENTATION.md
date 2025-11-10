# Internal Endpoints Implementation

## Overview

This document describes the implementation of dedicated internal endpoints for inter-service communication, separating them from admin endpoints to maintain cleaner security boundaries.

## Problem Statement

Previously, the `dispensing-service` needed to call an admin endpoint (`/api/admin/transaction/{id}/items`) to retrieve transaction items. This created a security concern as we had to allow inter-service communication to admin endpoints, which are intended for authenticated admin users only.

## Solution

Created dedicated internal endpoints (`/api/internal/**`) that are:

- Only accessible via inter-service communication (with `X-Request-Source: internal` header)
- Completely separate from admin endpoints
- Secured by their own validation rules

## Changes Made

### 1. New Controller: InternalTransactionController

**File:** `transaction-service/src/main/java/com/vendingmachine/transaction/transaction/InternalTransactionController.java`

```java
@RestController
@RequestMapping("/api/internal/transaction")
public class InternalTransactionController {

    /**
     * Get transaction items for dispensing service
     * Protected by X-Request-Source: internal header
     */
    @GetMapping("/{id}/items")
    public ResponseEntity<List<TransactionItemDTO>> getTransactionItems(@PathVariable Long id)

    /**
     * Get full transaction details for other services
     * Protected by X-Request-Source: internal header
     */
    @GetMapping("/{id}")
    public ResponseEntity<TransactionDTO> getTransaction(@PathVariable Long id)
}
```

**Purpose:**

- Provides endpoints specifically designed for inter-service communication
- Separates internal service needs from admin functionality
- Returns the same data but with appropriate security context

### 2. Updated Dispensing Service Client

**File:** `dispensing-service/src/main/java/com/vendingmachine/dispensing/kafka/TransactionEventConsumer.java`

**Before:**

```java
String url = transactionServiceUrl + "/api/admin/transaction/" + transactionId + "/items";
```

**After:**

```java
// Use internal endpoint for inter-service communication
String url = transactionServiceUrl + "/api/internal/transaction/" + transactionId + "/items";
```

### 3. Enhanced Security Configuration

**File:** `transaction-service/src/main/java/com/vendingmachine/transaction/config/SecurityConfig.java`

Added new security rule for internal endpoints (checked BEFORE admin endpoints):

```java
.requestMatchers("/api/internal/**").access((authentication, request) -> {
    String remoteAddr = request.getRequest().getRemoteAddr();

    // Internal endpoints only accessible via inter-service communication
    String requestSource = request.getRequest().getHeader(REQUEST_SOURCE_HEADER);
    if (REQUEST_SOURCE_INTERNAL.equals(requestSource) && LOCAL_IP_MAP.get(remoteAddr) != null) {
        log.debug("Inter-service request allowed to internal endpoint from: {}", remoteAddr);
        return new AuthorizationDecision(true);
    }

    log.warn("Request to internal endpoint without valid X-Request-Source header");
    return new AuthorizationDecision(false);
})
```

**Admin endpoints remain unchanged** - they still require:

1. `X-Internal-Service: api-gateway` header (from gateway)
2. Admin role (`ADMIN` or `SUPER_ADMIN`)

## Security Model

### Three-Tier Endpoint Structure

1. **Public Endpoints** (`/api/transaction/**`)

   - Accessible through API Gateway
   - May require authentication based on specific endpoint
   - Examples: `/api/transaction/purchase`, `/api/transaction/{id}`

2. **Internal Endpoints** (`/api/internal/**`)

   - Only accessible with `X-Request-Source: internal` header
   - Used exclusively for inter-service communication
   - No role-based authorization required
   - Examples: `/api/internal/transaction/{id}/items`

3. **Admin Endpoints** (`/api/admin/**`)
   - Require both gateway header AND admin role
   - Used by administrators through authenticated requests
   - Examples: `/api/admin/transaction/{id}/items`

### Header Validation Flow

```plaintext
Request → SecurityConfig
    ↓
Check RequestMatcher pattern
    ↓
/actuator/health → ALLOW (public)
    ↓
/api/internal/** → Check X-Request-Source: internal + localhost
    ↓
/api/admin/** → Check X-Internal-Service: api-gateway + Admin role
    ↓
Other /api/** → Check X-Internal-Service: api-gateway OR X-Request-Source: internal
```

## Benefits

1. **Clear Separation of Concerns**

   - Internal endpoints for services
   - Admin endpoints for authenticated users
   - Public endpoints for general access

2. **Enhanced Security**

   - Admin endpoints remain strictly protected
   - No overlap between internal and admin access rules
   - Each endpoint type has its own validation logic

3. **Better Maintainability**

   - Easy to identify purpose of each endpoint
   - Clear naming convention: `/api/internal/**` vs `/api/admin/**`
   - Simpler security configuration

4. **Scalability**
   - Easy to add more internal endpoints as needed
   - Pattern can be replicated across all services
   - No need to modify admin endpoint security for new inter-service needs

## RestTemplate Configuration

The `dispensing-service` and `transaction-service` both have `RestTemplateConfig` that automatically adds the required header:

```java
@Configuration
public class RestTemplateConfig {
    @Bean
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add((request, body, execution) -> {
            request.getHeaders().add("X-Request-Source", "internal");
            return execution.execute(request, body);
        });
        return restTemplate;
    }
}
```

This ensures all HTTP calls from these services automatically include the `X-Request-Source: internal` header.

## Testing

To test the implementation:

1. **Start all services:**

   ```bash
   ./start-services.sh
   ```

2. **Trigger a purchase transaction** (this will eventually trigger dispensing)

3. **Check logs:**

   - `transaction-service/logs/transaction-service.log` - Should show "Inter-service request allowed to internal endpoint"
   - `dispensing-service/logs/dispensing-service.log` - Should show successful retrieval of transaction items

4. **Try direct access** (should fail):

   ```bash
   curl http://localhost:8083/api/internal/transaction/1/items
   # Expected: 403 Forbidden
   ```

5. **Try via gateway** (should also fail - internal endpoints not routed through gateway):

   ```bash
   curl -H "Authorization: Bearer <token>" http://localhost:8080/api/transaction-service/api/internal/transaction/1/items
   # Expected: 404 or 403
   ```

## Future Enhancements

1. **Add Internal Endpoints to Other Services**

   - Inventory Service: `/api/internal/product/{id}`
   - Payment Service: `/api/internal/payment/{id}`
   - Notification Service: `/api/internal/notification/{id}`

2. **Create Shared Interface**

   - Define common patterns for internal endpoints
   - Standardize response formats

3. **Add Metrics**
   - Track inter-service communication frequency
   - Monitor internal endpoint usage

## Related Documentation

- [Inter-Service Request Filtering](./INTER_SERVICE_REQUEST_FILTERING.md) - Overview of header-based filtering system
- [Security Configuration](../../Documentation/Security-Configuration.md) - General security setup
- [AOP Logging System](./AOP/AOP_LOGGING_SYSTEM.md) - Request logging and correlation

## Conclusion

The implementation of dedicated internal endpoints provides a clean architectural pattern for inter-service communication while maintaining strict security boundaries. This approach enhances both security and maintainability of the microservices system.
