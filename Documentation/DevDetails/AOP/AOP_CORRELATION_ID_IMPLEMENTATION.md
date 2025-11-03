# AOP Correlation ID Implementation Guide

## Overview

This document describes the complete implementation of correlation ID tracking across the Vending Machine microservices system, enabling distributed tracing through HTTP requests and Kafka message flows.

## Architecture

### Correlation ID Flow

```plaintext
HTTP Request (X-Correlation-ID header)
    ↓
API Gateway → Transaction Service Controller (setCorrelationId)
    ↓
Transaction Service → Kafka Producer (adds to headers)
    ↓
Kafka Message (X-Correlation-ID header)
    ↓
Kafka Consumer → Inventory/Payment/Dispensing Services (setCorrelationId)
    ↓
AOP Aspects log with Correlation ID
    ↓
clearCorrelationId() in finally blocks
```

## Components

### 1. CorrelationIdUtil (`common-library`)

**Location**: `common-library/src/main/java/com/vendingmachine/common/util/CorrelationIdUtil.java`

**Purpose**: Centralized correlation ID management using MDC (Mapped Diagnostic Context)

**Key Methods**:

```java
public static String generateCorrelationId()          // UUID generation
public static void setCorrelationId(String id)        // Set in MDC
public static String getCorrelationId()                // Get from MDC or generate
public static void clearCorrelationId()                // Clean up MDC
```

**Constants**:

- `CORRELATION_ID_KEY = "correlationId"` - MDC key
- `CORRELATION_ID_HEADER = "X-Correlation-ID"` - HTTP/Kafka header name

### 2. Updated AuditAspect

**Location**: `common-library/src/main/java/com/vendingmachine/common/aop/aspect/AuditAspect.java`

**Enhancement**: All audit logs now include correlation ID

**Log Format**:

```bash
[AUDIT] START - Operation: PURCHASE_TRANSACTION
|- Correlation ID: 7f8c1234-5678-90ab-cdef-1234567890ab
|- User: admin
|- Method: TransactionController.purchase
|- Parameters: [PurchaseRequestDTO(items=[...])]
```

**Applied in 3 advice methods**:

- `@Before` - Operation start
- `@AfterReturning` - Successful completion
- `@AfterThrowing` - Exception handling

## Implementation Details

### Transaction Controller

**File**: `transaction-service/src/main/java/com/vendingmachine/transaction/transaction/TransactionController.java`

**Implementation**:

```java
@PostMapping("/purchase")
@Auditable(operation = "PURCHASE_TRANSACTION", entityType = "Transaction",
           logParameters = true, logResult = true)
@ExecutionTime(operation = "Purchase Request", warningThreshold = 3000, detailed = true)
public ResponseEntity<TransactionDTO> purchase(
        @Valid @RequestBody PurchaseRequestDTO request,
        @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {

    CorrelationIdUtil.setCorrelationId(correlationId);

    try {
        TransactionDTO transaction = transactionService.purchase(request);
        return ResponseEntity.ok(transaction);
    } finally {
        CorrelationIdUtil.clearCorrelationId();
    }
}
```

**Endpoints with AOP**:

1. `POST /api/transaction/purchase` - Create transaction (3s threshold)
2. `GET /api/transaction/all` - Get all transactions (2s threshold)
3. `GET /api/transaction/status/{status}` - Filter by status (1.5s threshold)
4. `GET /api/transaction/{id}` - Get by ID (1s threshold)
5. `GET /api/transaction/summary` - Summary report (2s threshold)

### Kafka Event Consumer

**File**: `transaction-service/src/main/java/com/vendingmachine/transaction/kafka/TransactionEventConsumer.java`

**Payment Event Consumer**:

```java
@KafkaListener(topics = "payment-events", groupId = "transaction-service-group")
@Transactional
@Auditable(operation = "CONSUME_PAYMENT_EVENT", entityType = "PaymentEvent", logParameters = true)
@ExecutionTime(operation = "Process Payment Event", warningThreshold = 2000, detailed = true)
public void consumePaymentEvent(
        PaymentEvent event,
        @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key,
        @Header(value = "X-Correlation-ID", required = false) String correlationId) {

    CorrelationIdUtil.setCorrelationId(correlationId);

    try {
        // Process payment event logic
    } finally {
        CorrelationIdUtil.clearCorrelationId();
    }
}
```

**Dispensing Event Consumer**:

```java
@KafkaListener(topics = "dispensing-events", groupId = "transaction-service-group")
@Transactional
@Auditable(operation = "CONSUME_DISPENSING_EVENT", entityType = "DispensingEvent", logParameters = true)
@ExecutionTime(operation = "Process Dispensing Event", warningThreshold = 2000, detailed = true)
public void consumeDispensingEvent(
        DispensingEvent event,
        @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key,
        @Header(value = "X-Correlation-ID", required = false) String correlationId) {

    CorrelationIdUtil.setCorrelationId(correlationId);

    try {
        // Process dispensing event logic
    } finally {
        CorrelationIdUtil.clearCorrelationId();
    }
}
```

### Kafka Event Producer

**File**: `transaction-service/src/main/java/com/vendingmachine/transaction/kafka/KafkaEventService.java`

**Implementation**:

```java
@Auditable(operation = "PUBLISH_TRANSACTION_EVENT", entityType = "TransactionEvent", logParameters = true)
@ExecutionTime(operation = "Publish Transaction Event", warningThreshold = 1000, detailed = true)
public void publishTransactionEvent(TransactionEvent event) {
    try {
        String correlationId = CorrelationIdUtil.getCorrelationId();

        Message<TransactionEvent> message = MessageBuilder
            .withPayload(event)
            .setHeader(KafkaHeaders.TOPIC, "transaction-events")
            .setHeader(KafkaHeaders.KEY, event.getEventId())
            .setHeader("X-Correlation-ID", correlationId)  // Propagate correlation ID
            .build();

        kafkaTemplate.send(message);
        log.info("Published event {} with correlation ID: {}",
                event.getEventId(), correlationId);
    } catch (Exception e) {
        log.error("Failed to publish event", e);
        throw new RuntimeException("Failed to publish event", e);
    }
}
```

## Usage Patterns

### Pattern 1: HTTP Request Handler

```java
public ResponseEntity<T> endpoint(
        @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {

    CorrelationIdUtil.setCorrelationId(correlationId);

    try {
        // Business logic
    } finally {
        CorrelationIdUtil.clearCorrelationId();
    }
}
```

### Pattern 2: Kafka Consumer

```java
@KafkaListener(topics = "some-topic")
public void consumeEvent(
        Event event,
        @Header(value = "X-Correlation-ID", required = false) String correlationId) {

    CorrelationIdUtil.setCorrelationId(correlationId);

    try {
        // Process event
    } finally {
        CorrelationIdUtil.clearCorrelationId();
    }
}
```

### Pattern 3: Kafka Producer

```java
public void publishEvent(Event event) {
    String correlationId = CorrelationIdUtil.getCorrelationId();

    Message<Event> message = MessageBuilder
        .withPayload(event)
        .setHeader(KafkaHeaders.TOPIC, "topic-name")
        .setHeader("X-Correlation-ID", correlationId)
        .build();

    kafkaTemplate.send(message);
}
```

## Log Output Examples

### Controller Log

```bash
[AUDIT] START - Operation: PURCHASE_TRANSACTION
|- Correlation ID: 7f8c1234-5678-90ab-cdef-1234567890ab
|- User: admin
|- Method: TransactionController.purchase
|- Parameters: [PurchaseRequestDTO(items=[Item(productId=1, quantity=2)])]

[METRICS] EXECUTION TIME REPORT
|- Correlation ID: 7f8c1234-5678-90ab-cdef-1234567890ab
|- Operation: Purchase Request
|- Method: TransactionController.purchase
|- Execution Time: 2847ms
|_ Status: ⚠️ WARNING (Threshold: 3000ms)

[AUDIT] SUCCESS - Operation: PURCHASE_TRANSACTION
|- Correlation ID: 7f8c1234-5678-90ab-cdef-1234567890ab
|- User: admin
|- Method: TransactionController.purchase
|_ Result: TransactionDTO(id=123, status=PENDING)
```

### Kafka Consumer Log

```bash
[AUDIT] START - Operation: CONSUME_PAYMENT_EVENT
|- Correlation ID: 7f8c1234-5678-90ab-cdef-1234567890ab
|- User: SYSTEM
|- Method: TransactionEventConsumer.consumePaymentEvent
|- Parameters: [PaymentEvent(eventId=payment-123, status=SUCCESS)]

[METRICS] EXECUTION TIME REPORT
|- Correlation ID: 7f8c1234-5678-90ab-cdef-1234567890ab
|- Operation: Process Payment Event
|- Method: TransactionEventConsumer.consumePaymentEvent
|- Execution Time: 1523ms
|_ Status: ✓ NORMAL (Threshold: 2000ms)
```

### Kafka Producer Log

```bash
[AUDIT] START - Operation: PUBLISH_TRANSACTION_EVENT
|- Correlation ID: 7f8c1234-5678-90ab-cdef-1234567890ab
|- User: SYSTEM
|- Method: KafkaEventService.publishTransactionEvent
|- Parameters: [TransactionEvent(eventId=txn-processing-123)]

Published transaction event: txn-processing-123 with correlation ID: 7f8c1234-5678-90ab-cdef-1234567890ab

[METRICS] EXECUTION TIME REPORT
|- Correlation ID: 7f8c1234-5678-90ab-cdef-1234567890ab
|- Operation: Publish Transaction Event
|_ Execution Time: 234ms
```

## End-to-End Tracing Example

A single purchase transaction generates logs across all services with the **same correlation ID**:

```plaintext
1. API Gateway: X-Correlation-ID: abc-123 → Transaction Service

2. Transaction Service (Controller):
   [AUDIT] PURCHASE_TRANSACTION | Correlation ID: abc-123

3. Transaction Service → Kafka (inventory-events):
   [AUDIT] PUBLISH_TRANSACTION_EVENT | Correlation ID: abc-123

4. Inventory Service (Consumer):
   [AUDIT] CONSUME_INVENTORY_EVENT | Correlation ID: abc-123

5. Inventory Service → Kafka (payment-events):
   [AUDIT] PUBLISH_PAYMENT_EVENT | Correlation ID: abc-123

6. Payment Service (Consumer):
   [AUDIT] CONSUME_PAYMENT_EVENT | Correlation ID: abc-123

7. Transaction Service (Consumer):
   [AUDIT] CONSUME_PAYMENT_EVENT | Correlation ID: abc-123

8. Dispensing Service (Consumer):
   [AUDIT] CONSUME_DISPENSING_EVENT | Correlation ID: abc-123
```

**Log Analysis**: You can grep logs by correlation ID to see the entire transaction flow:

```bash
grep "abc-123" logs/*.log | sort
```

## Best Practices

### ✅ DO

1. **Always use try-finally**: Ensure `clearCorrelationId()` is called to prevent MDC leaks
2. **Accept correlation ID as optional**: Generate if not provided
3. **Propagate through Kafka headers**: Use `X-Correlation-ID` header in all messages
4. **Log correlation ID in all audit logs**: Enables distributed tracing
5. **Use consistent header name**: Always use `X-Correlation-ID`

### ❌ DON'T

1. **Don't forget to clear MDC**: Threads are reused, stale IDs will leak
2. **Don't use different header names**: Standardize on `X-Correlation-ID`
3. **Don't make correlation ID required**: Allow system to generate if missing
4. **Don't log correlation ID manually**: AuditAspect handles this automatically

## Testing Correlation ID Flow

### Test 1: HTTP Request with Correlation ID

```bash
curl -X POST http://localhost:8080/api/transaction/purchase \
  -H "Authorization: Bearer <JWT>" \
  -H "X-Correlation-ID: test-correlation-123" \
  -H "Content-Type: application/json" \
  -d '{"items":[{"productId":1,"quantity":2}]}'
```

**Expected**: All logs for this transaction show `Correlation ID: test-correlation-123`

### Test 2: HTTP Request without Correlation ID

```bash
curl -X POST http://localhost:8080/api/transaction/purchase \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{"items":[{"productId":1,"quantity":2}]}'
```

**Expected**: System generates UUID correlation ID automatically

### Test 3: Kafka Message Flow

```bash
# Publish message to Kafka with correlation ID
kafka-console-producer --broker-list localhost:9092 --topic payment-events \
  --property "parse.key=true" \
  --property "key.separator=:" \
  --property "headers=X-Correlation-ID:kafka-test-456"
```

**Expected**: Consumer logs show `Correlation ID: kafka-test-456`

## Next Steps

### Other Services to Update

1. **Inventory Service**:

   - Add AOP to `InventoryController`
   - Update Kafka consumers/producers with correlation ID

2. **Payment Service**:

   - Add AOP to `PaymentController`
   - Update Kafka consumers/producers

3. **Dispensing Service**:

   - Add AOP to `DispensingController`
   - Update Kafka consumers/producers

4. **API Gateway**:

   - Create filter to generate correlation ID for incoming requests
   - Propagate correlation ID to downstream services

5. **Centralized Logging**:
   - Configure Logback to include correlation ID in log pattern
   - Use ELK/Splunk to query logs by correlation ID

## Logback Configuration

Add correlation ID to log pattern in `application.properties`:

```properties
logging.pattern.console=%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} [CorrelationId:%X{correlationId}] - %msg%n
logging.pattern.file=%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} [CorrelationId:%X{correlationId}] - %msg%n
```

**Result**: Every log line automatically includes `[CorrelationId:abc-123]`

## Summary

✅ **Implemented**:

- CorrelationIdUtil with MDC management
- AuditAspect with correlation ID logging
- TransactionController with correlation ID handling (5 endpoints)
- Kafka consumers with correlation ID extraction (payment, dispensing)
- Kafka producer with correlation ID propagation
- Complete try-finally patterns for MDC cleanup

✅ **Benefits**:

- Full distributed tracing across microservices
- Easy log correlation for debugging
- Visual tree format logs with correlation context
- Automatic correlation ID generation
- Thread-safe MDC implementation

📋 **Next Actions**:

- Extend to inventory, payment, dispensing services
- Add API Gateway correlation ID filter
- Configure Logback with correlation ID pattern
- Set up centralized logging with correlation ID indexing
