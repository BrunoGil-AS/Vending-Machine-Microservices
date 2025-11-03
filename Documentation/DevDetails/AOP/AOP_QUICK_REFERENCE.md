# AOP Quick Reference Guide

## Quick Start

### 1. Add AOP Annotations to Controller

```java
import com.vendingmachine.common.aop.annotation.Auditable;
import com.vendingmachine.common.aop.annotation.ExecutionTime;
import com.vendingmachine.common.util.CorrelationIdUtil;

@PostMapping("/endpoint")
@Auditable(operation = "OPERATION_NAME", entityType = "EntityName",
           logParameters = true, logResult = true)
@ExecutionTime(operation = "Operation Description", warningThreshold = 2000, detailed = true)
public ResponseEntity<DTO> endpoint(
        @RequestBody DTO request,
        @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {

    CorrelationIdUtil.setCorrelationId(correlationId);

    try {
        // Business logic
        return ResponseEntity.ok(result);
    } finally {
        CorrelationIdUtil.clearCorrelationId();
    }
}
```

### 2. Add AOP Annotations to Service

```java
@Auditable(operation = "OPERATION_NAME", entityType = "EntityName",
           logParameters = true, logResult = true)
@ExecutionTime(operation = "Operation Description", warningThreshold = 1500, detailed = true)
public DTO serviceMethod(RequestDTO request) {
    // Business logic - correlation ID already in MDC from controller
    return result;
}
```

### 3. Add AOP to Kafka Consumer

```java
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;

@KafkaListener(topics = "topic-name", groupId = "service-group")
@Transactional
@Auditable(operation = "CONSUME_EVENT_NAME", entityType = "EventType", logParameters = true)
@ExecutionTime(operation = "Process Event Description", warningThreshold = 2000, detailed = true)
public void consumeEvent(
        Event event,
        @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key,
        @Header(value = "X-Correlation-ID", required = false) String correlationId) {

    CorrelationIdUtil.setCorrelationId(correlationId);

    try {
        // Process event
    } finally {
        CorrelationIdUtil.clearCorrelationId();
    }
}
```

### 4. Add AOP to Kafka Producer

```java
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

@Auditable(operation = "PUBLISH_EVENT_NAME", entityType = "EventType", logParameters = true)
@ExecutionTime(operation = "Publish Event Description", warningThreshold = 1000, detailed = true)
public void publishEvent(Event event) {
    try {
        String correlationId = CorrelationIdUtil.getCorrelationId();

        Message<Event> message = MessageBuilder
            .withPayload(event)
            .setHeader(KafkaHeaders.TOPIC, "topic-name")
            .setHeader(KafkaHeaders.KEY, event.getId())
            .setHeader("X-Correlation-ID", correlationId)
            .build();

        kafkaTemplate.send(message);
        log.info("Published event {} with correlation ID: {}", event.getId(), correlationId);
    } catch (Exception e) {
        log.error("Failed to publish event", e);
        throw new RuntimeException("Failed to publish event", e);
    }
}
```

## Annotation Parameters

### @ExecutionTime

| Parameter          | Type    | Required | Default | Description                         |
| ------------------ | ------- | -------- | ------- | ----------------------------------- |
| `operation`        | String  | No       | ""      | Operation name for logs             |
| `warningThreshold` | int     | No       | 1000    | Milliseconds threshold for warnings |
| `detailed`         | boolean | No       | false   | Enable detailed performance data    |

**Example**:

```java
@ExecutionTime(operation = "Purchase Transaction", warningThreshold = 3000, detailed = true)
```

### @Auditable

| Parameter       | Type    | Required | Default | Description               |
| --------------- | ------- | -------- | ------- | ------------------------- |
| `operation`     | String  | **YES**  | -       | Operation name (REQUIRED) |
| `entityType`    | String  | No       | ""      | Entity type being audited |
| `logParameters` | boolean | No       | false   | Log method parameters     |
| `logResult`     | boolean | No       | false   | Log return value          |

**Example**:

```java
@Auditable(operation = "PURCHASE_TRANSACTION", entityType = "Transaction",
           logParameters = true, logResult = true)
```

## Recommended Thresholds

| Layer              | Operation Type         | Threshold (ms) |
| ------------------ | ---------------------- | -------------- |
| **Controller**     | Simple GET             | 1000           |
| **Controller**     | Complex GET with joins | 2000           |
| **Controller**     | POST/PUT/DELETE        | 2000-3000      |
| **Service**        | Database query         | 500-1000       |
| **Service**        | External API call      | 1500-2000      |
| **Service**        | Complex business logic | 2000-3000      |
| **Kafka Consumer** | Event processing       | 2000           |
| **Kafka Producer** | Event publishing       | 1000           |

## Log Format Examples

### Audit Log - Start

```bash
[AUDIT] START - Operation: PURCHASE_TRANSACTION
|- Correlation ID: abc-123-def-456
|- User: admin
|- Method: TransactionController.purchase
|- Parameters: [PurchaseRequestDTO(items=[...])]
```

### Audit Log - Success

```bash
[AUDIT] SUCCESS - Operation: PURCHASE_TRANSACTION
|- Correlation ID: abc-123-def-456
|- User: admin
|- Method: TransactionController.purchase
|_ Result: TransactionDTO(id=123, status=PENDING)
```

### Audit Log - Error

```bash
[AUDIT] ERROR - Operation: PURCHASE_TRANSACTION
|- Correlation ID: abc-123-def-456
|- User: admin
|- Method: TransactionController.purchase
|_ Exception: InsufficientStockException: Product 5 out of stock
```

### Performance Log - Normal

```bash
[METRICS] EXECUTION TIME REPORT
|- Correlation ID: abc-123-def-456
|- Operation: Purchase Transaction
|- Method: TransactionController.purchase
|- Execution Time: 1847ms
|_ Status: ✓ NORMAL (Threshold: 3000ms)
```

### Performance Log - Warning

```bash
[METRICS] EXECUTION TIME REPORT
|- Correlation ID: abc-123-def-456
|- Operation: Complex Query
|- Method: TransactionService.getTransactionSummary
|- Execution Time: 3241ms
|_ Status: ⚠️ WARNING (Threshold: 2000ms)
```

## Common Patterns

### Pattern 1: REST Endpoint (Full Instrumentation)

```java
@PostMapping("/resource")
@Auditable(operation = "CREATE_RESOURCE", entityType = "Resource",
           logParameters = true, logResult = true)
@ExecutionTime(operation = "Create Resource", warningThreshold = 2000, detailed = true)
public ResponseEntity<ResourceDTO> create(
        @Valid @RequestBody ResourceDTO request,
        @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {

    CorrelationIdUtil.setCorrelationId(correlationId);
    try {
        ResourceDTO result = service.create(request);
        return ResponseEntity.ok(result);
    } finally {
        CorrelationIdUtil.clearCorrelationId();
    }
}
```

### Pattern 2: Service Method (Performance Only)

```java
@ExecutionTime(operation = "Check Stock", warningThreshold = 500)
public boolean checkStock(Long productId, int quantity) {
    // Fast database query - no audit needed
    return stockRepository.isAvailable(productId, quantity);
}
```

### Pattern 3: Service Method (Audit + Performance)

```java
@Auditable(operation = "PROCESS_PAYMENT", entityType = "Payment",
           logParameters = true, logResult = true)
@ExecutionTime(operation = "Process Payment", warningThreshold = 1500, detailed = true)
@Transactional
public PaymentDTO processPayment(PaymentRequest request) {
    // Critical business logic - full instrumentation
    return paymentGateway.charge(request);
}
```

### Pattern 4: Kafka Consumer (Event Processing)

```java
@KafkaListener(topics = "events", groupId = "consumer-group")
@Transactional
@Auditable(operation = "CONSUME_EVENT", entityType = "Event", logParameters = true)
@ExecutionTime(operation = "Process Event", warningThreshold = 2000, detailed = true)
public void consume(
        Event event,
        @Header(value = "X-Correlation-ID", required = false) String correlationId) {

    CorrelationIdUtil.setCorrelationId(correlationId);
    try {
        // Check deduplication
        if (processedEventRepository.existsByEventId(event.getId())) {
            log.warn("Event {} already processed", event.getId());
            return;
        }

        // Process event
        handleEvent(event);

        // Mark as processed
        processedEventRepository.save(new ProcessedEvent(event.getId()));
    } finally {
        CorrelationIdUtil.clearCorrelationId();
    }
}
```

### Pattern 5: Kafka Producer (Event Publishing)

```java
@Auditable(operation = "PUBLISH_EVENT", entityType = "Event", logParameters = true)
@ExecutionTime(operation = "Publish Event", warningThreshold = 1000)
public void publish(Event event) {
    try {
        String correlationId = CorrelationIdUtil.getCorrelationId();

        Message<Event> message = MessageBuilder
            .withPayload(event)
            .setHeader(KafkaHeaders.TOPIC, "events")
            .setHeader(KafkaHeaders.KEY, event.getId())
            .setHeader("X-Correlation-ID", correlationId)
            .build();

        kafkaTemplate.send(message);
    } catch (Exception e) {
        log.error("Failed to publish event", e);
        throw new RuntimeException("Failed to publish event", e);
    }
}
```

## Testing Checklist

### Unit Tests

- [ ] Controller endpoints accept `X-Correlation-ID` header
- [ ] Correlation ID set in MDC at request start
- [ ] Correlation ID cleared in finally block
- [ ] Service methods execute with correlation context
- [ ] Kafka consumers extract correlation ID from headers
- [ ] Kafka producers add correlation ID to headers

### Integration Tests

- [ ] Send request with correlation ID → verify in logs
- [ ] Send request without correlation ID → verify auto-generation
- [ ] Kafka message flow preserves correlation ID
- [ ] End-to-end flow: HTTP → Service → Kafka → Consumer
- [ ] Performance thresholds trigger warnings
- [ ] Audit logs contain all required fields

### Performance Tests

- [ ] Normal execution logs ✓ NORMAL status
- [ ] Slow execution logs ⚠️ WARNING status
- [ ] Thresholds appropriate for operation complexity
- [ ] No performance degradation from AOP overhead

## Troubleshooting

### Issue: Correlation ID not appearing in logs

**Check**:

1. `CorrelationIdUtil.setCorrelationId()` called at request/event start
2. `@Auditable` annotation present on method
3. AuditAspect loaded (check Spring context)
4. MDC not cleared too early

**Solution**:

```java
// WRONG - MDC cleared before aspects run
try {
    CorrelationIdUtil.setCorrelationId(id);
    doWork();
    CorrelationIdUtil.clearCorrelationId(); // TOO EARLY
} catch (Exception e) {
    // handle
}

// CORRECT - MDC cleared in finally
try {
    CorrelationIdUtil.setCorrelationId(id);
    doWork();
} finally {
    CorrelationIdUtil.clearCorrelationId(); // CORRECT
}
```

### Issue: Correlation ID leaking between requests

**Cause**: MDC not cleared after request processing

**Solution**: Always use try-finally pattern:

```java
try {
    CorrelationIdUtil.setCorrelationId(correlationId);
    // logic
} finally {
    CorrelationIdUtil.clearCorrelationId(); // CRITICAL
}
```

### Issue: Kafka consumer not extracting correlation ID

**Check**:

1. Header parameter added: `@Header(value = "X-Correlation-ID", required = false)`
2. Header name matches producer: `X-Correlation-ID`
3. Kafka message includes header (check producer)

**Solution**:

```java
// Producer
.setHeader("X-Correlation-ID", correlationId)

// Consumer
@Header(value = "X-Correlation-ID", required = false) String correlationId
```

### Issue: Performance warnings flooding logs

**Cause**: Threshold set too low for operation

**Solution**: Adjust threshold based on operation complexity:

```java
// Database query - fast
@ExecutionTime(operation = "Get Product", warningThreshold = 500)

// External API call - slower
@ExecutionTime(operation = "Payment Gateway", warningThreshold = 2000)

// Complex report - slowest
@ExecutionTime(operation = "Generate Report", warningThreshold = 5000)
```

## Best Practices Summary

### ✅ DO

1. **Always use try-finally for MDC cleanup**
2. **Set realistic performance thresholds**
3. **Log parameters for audit operations**
4. **Propagate correlation ID through all layers**
5. **Use detailed flag for complex operations**
6. **Accept correlation ID as optional parameter**

### ❌ DON'T

1. **Don't forget to clear MDC** (causes leaks)
2. **Don't log sensitive data** (passwords, tokens)
3. **Don't set thresholds too low** (log spam)
4. **Don't make correlation ID required** (breaks clients)
5. **Don't use different header names** (inconsistency)
6. **Don't skip finally blocks** (MDC pollution)

## Reference Files

- **Implementation Guide**: `AOP_CORRELATION_ID_IMPLEMENTATION.md`
- **Complete Summary**: `AOP_IMPLEMENTATION_COMPLETE.md`
- **Log Examples**: `AOP_LOG_FORMAT_EXAMPLE.md`
- **System Design**: `AOP_LOGGING_SYSTEM.md`

## Support

For issues or questions:

1. Check existing documentation in `Documentation/` folder
2. Review transaction-service implementation as reference
3. Verify common-library AOP components are compiled
4. Test with simple endpoint before complex flows
