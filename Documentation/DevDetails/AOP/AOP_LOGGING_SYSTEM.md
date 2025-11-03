# AOP Logging System - Vending Machine Microservices

## Overview

This project implements a comprehensive AOP (Aspect-Oriented Programming) logging system based on the pattern from the e-commerce order service. The system provides:

- **Performance Monitoring** - Track execution time and identify bottlenecks
- **Audit Trail** - Log critical business operations for compliance
- **Domain-Specific Logging** - Specialized logging for transaction operations
- **Structured Logs** - Consistent, parsable log format for monitoring systems

## Architecture

### Package Structure

```plaintext
common-library/
└── src/main/java/com/vendingmachine/common/aop/
    ├── annotation/
    │   ├── ExecutionTime.java    # Performance monitoring annotation
    │   └── Auditable.java         # Audit trail annotation
    └── aspect/
        ├── ExecutionTimeAspect.java   # Performance monitoring aspect
        └── AuditAspect.java            # Audit trail aspect

transaction-service/
└── src/main/java/com/vendingmachine/transaction/aop/
    └── TransactionOperationAspect.java  # Domain-specific aspect
```

## Annotations

### @ExecutionTime

Measures method execution time and logs performance metrics.

**Usage:**

```java
@ExecutionTime(
    operation = "purchase",           // Operation name for logs
    warningThreshold = 2000,          // Warn if exceeds 2000ms
    detailed = true                   // Include detailed timing info
)
public TransactionDTO purchase(PurchaseRequestDTO request) {
    // Method implementation
}
```

**Parameters:**

- `operation` (String, optional): Custom operation name. Defaults to method name.
- `warningThreshold` (long, default: 1000): Execution time threshold in milliseconds for warnings.
- `detailed` (boolean, default: false): Enable detailed logging with start/end timestamps.

**Log Output:**

```bash
[PERFORMANCE-REPORT] 2025-11-03 14:04:19.123
|- Operation: purchase
|- Class: TransactionService
|- Method: purchase
|- Execution Time: 1543 ms
|- Status: SUCCESS
|- Start Time: 2025-11-03 14:04:17.580
|- End Time: 2025-11-03 14:04:19.123
|_ Performance: NORMAL

[METRICS] METRICS|operation=purchase|class=TransactionService|method=purchase|execution_time_ms=1543|success=true|timestamp=2025-11-03 14:04:19.123
```

### @Auditable

Logs critical business operations with before/after details.

**Usage:**

```java
@Auditable(
    operation = "Purchase Transaction",  // Human-readable operation description
    entityType = "Transaction",          // Type of entity being modified
    logParameters = true,                // Log method parameters
    logResult = true                     // Log method result
)
public TransactionDTO purchase(PurchaseRequestDTO request) {
    // Method implementation
}
```

**Parameters:**

- `operation` (String, required): Description of the operation being audited.
- `entityType` (String, optional): Type of entity being accessed/modified.
- `logParameters` (boolean, default: true): Log input parameters (sensitive data is masked).
- `logResult` (boolean, default: false): Log operation result.

**Log Output:**

```bash
[AUDIT-START] 2025-11-03 14:04:17
|- User: ANONYMOUS
|- Operation: Purchase Transaction
|- Entity Type: Transaction
|- Class: TransactionService
|- Method: purchase
|- Parameters: PurchaseRequestDTO[...]

[AUDIT-SUCCESS] 2025-11-03 14:04:19
|- Operation: Purchase Transaction
|- Status: SUCCESS
|_ Result: TransactionDTO
```

## Domain-Specific Aspects

### TransactionOperationAspect

Provides specialized logging for transaction-related operations with automatic validation.

**Features:**

- Logs all controller method invocations
- Tracks critical transaction modifications (purchase, compensate)
- Validates business rules (items required, payment info present)
- Detailed error logging with execution time

**Pointcuts:**

- `transactionControllerMethods()` - All TransactionController methods
- `transactionServiceMethods()` - All TransactionService methods
- `transactionModificationMethods()` - Critical operations (purchase, compensate)

**Log Output:**

```bash
[TRANSACTION-MODIFICATION-START] 2025-11-03 14:04:17
|- Operation: purchase
|_ Critical operation initiated

[TRANSACTION-MODIFICATION-SUCCESS] 2025-11-03 14:04:19
|- Operation: purchase
|- Duration: 1543 ms
|_ Operation completed successfully

[TRANSACTION-SERVICE] Method: purchase completed - Result: TransactionDTO
```

## Applied Annotations in TransactionService

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    // Critical business operation - full monitoring
    @Transactional
    @Auditable(operation = "Purchase Transaction", entityType = "Transaction",
               logParameters = true, logResult = true)
    @ExecutionTime(operation = "purchase", warningThreshold = 2000, detailed = true)
    public TransactionDTO purchase(PurchaseRequestDTO request) { ... }

    // Compensation operation - audit + performance
    @Transactional
    @Auditable(operation = "Compensate Transaction", entityType = "Transaction",
               logParameters = true)
    @ExecutionTime(operation = "compensateTransaction", warningThreshold = 1500, detailed = true)
    public void compensateTransaction(Long transactionId, String reason) { ... }

    // Internal critical operations - performance only
    @ExecutionTime(operation = "checkInventoryAvailability", warningThreshold = 500)
    private boolean checkInventoryAvailability(List<PurchaseItemDTO> items) { ... }

    @ExecutionTime(operation = "processPayment", warningThreshold = 800)
    private boolean processPayment(Long transactionId, PaymentInfo paymentInfo,
                                   BigDecimal amount) { ... }

    @ExecutionTime(operation = "refundPayment", warningThreshold = 800)
    private boolean refundPayment(Long transactionId, BigDecimal amount) { ... }

    // Analytics operation
    @ExecutionTime(operation = "getTransactionSummary", warningThreshold = 1000)
    public TransactionSummaryDTO getTransactionSummary() { ... }
}
```

## Security & Privacy

### Sensitive Data Masking

The `AuditAspect` automatically masks sensitive information:

- Parameters/fields containing: `password`, `credential`, `secret`, `payment`
- Logged as: `[SENSITIVE_DATA]`
- Long strings truncated to 200 characters

**Example:**

```java
// Input parameter
PaymentInfo { cardNumber: "4111111111111111", cvv: "123" }

// Logged as
Parameters: [SENSITIVE_DATA]
```

## Log Levels

| Level     | Use Case                              | Example                                               |
| --------- | ------------------------------------- | ----------------------------------------------------- |
| **INFO**  | Normal operation logs                 | Performance within threshold, successful audits       |
| **WARN**  | Performance issues, business warnings | Execution time exceeds threshold, validation warnings |
| **ERROR** | Operation failures                    | Transaction errors, service exceptions                |
| **DEBUG** | Detailed validation info              | Business rule checks, validation passes               |

## Integration with Monitoring Systems

### Metrics Log Format

The `ExecutionTimeAspect` generates structured metrics suitable for log aggregation:

```bash
[METRICS] METRICS|operation=purchase|class=TransactionService|method=purchase|execution_time_ms=1543|success=true|timestamp=2025-11-03 14:04:19.123
```

These can be:

- Parsed by log aggregators (ELK, Splunk, etc.)
- Sent to metrics systems (Prometheus, Micrometer)
- Used for performance dashboards

## Performance Thresholds

Default thresholds configured per operation type:

| Operation                    | Threshold (ms) | Rationale                         |
| ---------------------------- | -------------- | --------------------------------- |
| `purchase`                   | 2000           | Complex multi-service transaction |
| `compensateTransaction`      | 1500           | Refund + state updates            |
| `checkInventoryAvailability` | 500            | Simple HTTP call                  |
| `processPayment`             | 800            | External payment gateway          |
| `refundPayment`              | 800            | External payment gateway          |
| `getTransactionSummary`      | 1000           | Database aggregation              |

## Best Practices

### When to Use @ExecutionTime

✅ **USE:**

- Public service methods
- Methods calling external services
- Database-heavy operations
- Complex business logic

❌ **AVOID:**

- Simple getters/setters
- Private utility methods
- Very frequent operations (may impact performance)

### When to Use @Auditable

✅ **USE:**

- State-changing operations (create, update, delete)
- Financial transactions
- Security-sensitive operations
- Compliance-required operations

❌ **AVOID:**

- Read-only queries
- Internal helper methods
- High-frequency operations

### Combining Annotations

Both annotations can be combined for critical operations:

```java
@Auditable(operation = "Critical Operation", entityType = "Entity")
@ExecutionTime(operation = "criticalOp", warningThreshold = 1000, detailed = true)
public void criticalOperation() {
    // Both audit trail and performance monitoring
}
```

## Extension to Other Services

To apply AOP logging to other services:

1. **Add AOP dependency** (if not present):

   ```xml
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter-aop</artifactId>
   </dependency>
   ```

2. **Import annotations**:

   ```java
   import com.vendingmachine.common.aop.annotation.Auditable;
   import com.vendingmachine.common.aop.annotation.ExecutionTime;
   ```

3. **Apply to service methods**:

   ```java
   @ExecutionTime(operation = "operationName", warningThreshold = 1000)
   @Auditable(operation = "Operation Description", entityType = "Entity")
   public void yourMethod() { ... }
   ```

4. **Create domain-specific aspect** (optional):

   ```java
   @Aspect
   @Component
   @Slf4j
   public class YourServiceOperationAspect {
       // Define pointcuts and advice specific to your service
   }
   ```

## Testing Considerations

- AOP aspects are automatically applied in integration tests
- For unit tests, you may want to disable aspects or test them separately
- Performance thresholds should be adjusted based on test environment

## Future Enhancements

- [ ] Integration with Micrometer for direct metric export
- [ ] Custom annotations for rate limiting
- [ ] Correlation ID tracking across services
- [ ] Automatic SLA violation alerts
- [ ] Performance regression detection
