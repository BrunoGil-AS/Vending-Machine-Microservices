# AOP Log Format Examples - Visual Tree Structure

## Overview

The AOP logging system now uses a visual tree structure format that makes it easy to read and track operations in the logs, even when there are thousands of log entries.

## Format Structure

All logs use a tree-like structure with these symbols:

- `[TAG]` - Log category/type
- `|-` - Tree branch (property/detail)
- `|_` - Tree end (last property or summary)

This format makes it easy to:

- ✅ Visually scan logs
- ✅ Identify transaction boundaries
- ✅ Track operation flow
- ✅ Spot errors quickly

---

## Example 1: Complete Purchase Transaction Flow

### Audit Start

```bash
[AUDIT-START] 2025-11-03 14:23:45
|- User: ANONYMOUS
|- Operation: Purchase Transaction
|- Entity Type: Transaction
|- Class: TransactionService
|- Method: purchase
|- Parameters: PurchaseRequestDTO[items=2, paymentMethod=CARD]
```

### Transaction Modification Start

```bash
[TRANSACTION-MODIFICATION-START] 2025-11-03 14:23:45
|- Operation: purchase
|_ Critical operation initiated
```

### Performance Report - Check Inventory

```bash
[PERFORMANCE-REPORT] 2025-11-03 14:23:45.234
|- Operation: checkInventoryAvailability
|- Class: TransactionService
|- Method: checkInventoryAvailability
|- Execution Time: 156 ms
|- Status: SUCCESS
|_ Performance: NORMAL

[METRICS] METRICS|operation=checkInventoryAvailability|class=TransactionService|method=checkInventoryAvailability|execution_time_ms=156|success=true|timestamp=2025-11-03 14:23:45.234
```

### Performance Report - Process Payment

```bash
[PERFORMANCE-REPORT] 2025-11-03 14:23:45.678
|- Operation: processPayment
|- Class: TransactionService
|- Method: processPayment
|- Execution Time: 423 ms
|- Status: SUCCESS
|_ Performance: NORMAL

[METRICS] METRICS|operation=processPayment|class=TransactionService|method=processPayment|execution_time_ms=423|success=true|timestamp=2025-11-03 14:23:45.678
```

### Transaction Modification Success

```bash
[TRANSACTION-MODIFICATION-SUCCESS] 2025-11-03 14:23:46
|- Operation: purchase
|- Duration: 1543 ms
|_ Operation completed successfully
```

### Service Completion

```bash
[TRANSACTION-SERVICE] Method: purchase completed
|_ Result: TransactionDTO
```

### Performance Report - Complete Operation

```bash
[PERFORMANCE-REPORT] 2025-11-03 14:23:46.543
|- Operation: purchase
|- Class: TransactionService
|- Method: purchase
|- Execution Time: 1543 ms
|- Status: SUCCESS
|- Start Time: 2025-11-03 14:23:45.000
|- End Time: 2025-11-03 14:23:46.543
|_ Performance: NORMAL

[METRICS] METRICS|operation=purchase|class=TransactionService|method=purchase|execution_time_ms=1543|success=true|timestamp=2025-11-03 14:23:46.543
```

### Audit Success

```bash
[AUDIT-SUCCESS] 2025-11-03 14:23:46
|- Operation: Purchase Transaction
|- Status: SUCCESS
|_ Result: TransactionDTO
```

---

## Example 2: Failed Transaction (Payment Declined)

### Audit - Start

```bash
[AUDIT-START] 2025-11-03 14:30:12
|- User: ANONYMOUS
|- Operation: Purchase Transaction
|- Entity Type: Transaction
|- Class: TransactionService
|- Method: purchase
|- Parameters: PurchaseRequestDTO[items=1, paymentMethod=CARD]
```

### Transaction Modification /Start

```bash
[TRANSACTION-MODIFICATION-START] 2025-11-03 14:30:12
|- Operation: purchase
|_ Critical operation initiated
```

### Performance Report - Check Inventory (Success)

```bash
[PERFORMANCE-REPORT] 2025-11-03 14:30:12.123
|- Operation: checkInventoryAvailability
|- Class: TransactionService
|- Method: checkInventoryAvailability
|- Execution Time: 98 ms
|- Status: SUCCESS
|_ Performance: NORMAL
```

### Performance Report - Process Payment (Failed)

```bash
[PERFORMANCE-REPORT] 2025-11-03 14:30:12.567
|- Operation: processPayment
|- Class: TransactionService
|- Method: processPayment
|- Execution Time: 345 ms
|- Status: ERROR
|- Exception: RuntimeException
|_ Performance: NORMAL
```

### Transaction Modification Error

```bash
[TRANSACTION-MODIFICATION-ERROR] 2025-11-03 14:30:12
|- Operation: purchase
|- Duration: 456 ms
|- Error: RuntimeException
|_ Message: Payment processing failed
```

### Service Error

```bash
[TRANSACTION-SERVICE-ERROR] 2025-11-03 14:30:12
|- Method: purchase
|- Exception: RuntimeException
|_ Message: Payment processing failed
```

### Audit Error

```bash
[AUDIT-ERROR] 2025-11-03 14:30:12
|- User: ANONYMOUS
|- Operation: Purchase Transaction
|- Status: ERROR
|- Exception: RuntimeException
|_ Message: Payment processing failed
```

---

## Example 3: Slow Performance Warning

### Performance Report - Slow Operation

```bash
[PERFORMANCE-REPORT] 2025-11-03 14:35:22.890
|- Operation: purchase
|- Class: TransactionService
|- Method: purchase
|- Execution Time: 2345 ms
|- Status: SUCCESS
|- Start Time: 2025-11-03 14:35:20.545
|- End Time: 2025-11-03 14:35:22.890
|_ WARNING: Execution time exceeded threshold (2000 ms)

[METRICS] METRICS|operation=purchase|class=TransactionService|method=purchase|execution_time_ms=2345|success=true|timestamp=2025-11-03 14:35:22.890
```

This warning is logged at WARN level, making it easy to spot performance issues in production.

---

## Example 4: Transaction Compensation Flow

### -Audit Start

```bash
[AUDIT-START] 2025-11-03 14:40:15
|- User: SYSTEM
|- Operation: Compensate Transaction
|- Entity Type: Transaction
|- Class: TransactionService
|- Method: compensateTransaction
|- Parameters: 12345, Dispensing failed
```

### Transaction Modification - Start

```bash
[TRANSACTION-MODIFICATION-START] 2025-11-03 14:40:15
|- Operation: compensateTransaction
|_ Critical operation initiated
```

### Performance Report - Refund Payment

```bash
[PERFORMANCE-REPORT] 2025-11-03 14:40:15.456
|- Operation: refundPayment
|- Class: TransactionService
|- Method: refundPayment
|- Execution Time: 567 ms
|- Status: SUCCESS
|_ Performance: NORMAL

[METRICS] METRICS|operation=refundPayment|class=TransactionService|method=refundPayment|execution_time_ms=567|success=true|timestamp=2025-11-03 14:40:15.456
```

### Transaction Modification - Success

```bash
[TRANSACTION-MODIFICATION-SUCCESS] 2025-11-03 14:40:15
|- Operation: compensateTransaction
|- Duration: 678 ms
|_ Operation completed successfully
```

### Performance Report - Complete

```bash
[PERFORMANCE-REPORT] 2025-11-03 14:40:15.678
|- Operation: compensateTransaction
|- Class: TransactionService
|- Method: compensateTransaction
|- Execution Time: 678 ms
|- Status: SUCCESS
|- Start Time: 2025-11-03 14:40:15.000
|- End Time: 2025-11-03 14:40:15.678
|_ Performance: NORMAL

[METRICS] METRICS|operation=compensateTransaction|class=TransactionService|method=compensateTransaction|execution_time_ms=678|success=true|timestamp=2025-11-03 14:40:15.678
```

### -Audit Success

```bash
[AUDIT-SUCCESS] 2025-11-03 14:40:15
|- Operation: Compensate Transaction
|- Status: SUCCESS
|_ Result: [Not logged]
```

---

## Example 5: Controller Entry Point

### Controller Log

```bash
[TRANSACTION-CONTROLLER] 2025-11-03 14:45:30
|- Method: purchase
|_ Args: 1
```

This is logged before any service processing begins, showing the HTTP request entry point.

---

## Benefits of This Format

### 1. Visual Hierarchy

Easy to see parent-child relationships and operation boundaries:

```bash
[AUDIT-START]           ← Start of operation
  |- User              ← Details
  |- Operation
  |_ Parameters         ← Last detail
[PERFORMANCE-REPORT]    ← Sub-operation
  |- Duration
  |_ Status
[AUDIT-SUCCESS]         ← End of operation
  |_ Result
```

### 2. Easy Filtering

Search for specific tags in your logs:

- `[AUDIT-START]` - All operation starts
- `[AUDIT-ERROR]` - All failed operations
- `[PERFORMANCE-REPORT].*WARNING` - All slow operations
- `[TRANSACTION-MODIFICATION]` - All critical transactions

### 3. Timeline Tracking

Timestamps show the flow of operations:

```bash
14:23:45.000 - Operation starts
14:23:45.234 - Inventory check completes
14:23:45.678 - Payment processing completes
14:23:46.543 - Operation completes
Total: ~1.5 seconds
```

### 4. Easy Debugging

When an error occurs, you can see exactly what happened:

- What operation failed
- What parameters were used
- How long it took
- What sub-operations succeeded/failed
- The exact error message

### 5. Production Monitoring

The structured format is perfect for:

- Log aggregation tools (ELK, Splunk)
- APM tools (New Relic, Datadog)
- Custom dashboards
- Alerting on specific patterns

---

## Log Levels

| Level | Use Case                                          |
| ----- | ------------------------------------------------- |
| INFO  | Normal operation flow, successful operations      |
| WARN  | Performance warnings, business warnings           |
| ERROR | Operation failures, exceptions                    |
| DEBUG | Detailed validation info (disabled in production) |

---

## Comparison: Old vs New Format

### ❌ Old Linear Format

```bash
2025-11-03 14:23:45 INFO  [TransactionService] Starting purchase
2025-11-03 14:23:45 INFO  [TransactionService] Checking inventory
2025-11-03 14:23:45 INFO  [InventoryClient] Items available
2025-11-03 14:23:45 INFO  [TransactionService] Processing payment
2025-11-03 14:23:46 INFO  [PaymentClient] Payment successful
2025-11-03 14:23:46 INFO  [TransactionService] Purchase completed
```

Hard to track in logs with thousands of entries!

### ✅ New Tree Format

```bash
[AUDIT-START] 2025-11-03 14:23:45
|- User: ANONYMOUS
|- Operation: Purchase Transaction
|_ Parameters: [...]

[PERFORMANCE-REPORT] 2025-11-03 14:23:45.234
|- Operation: checkInventoryAvailability
|- Execution Time: 156 ms
|_ Status: SUCCESS

[PERFORMANCE-REPORT] 2025-11-03 14:23:45.678
|- Operation: processPayment
|- Execution Time: 423 ms
|_ Status: SUCCESS

[AUDIT-SUCCESS] 2025-11-03 14:23:46
|- Operation: Purchase Transaction
|_ Status: SUCCESS
```

Clear boundaries and relationships!

---

## Next Steps

1. **Run the application** and perform a purchase to see these logs in action
2. **Configure your log viewer** to highlight the different log tags
3. **Set up alerts** for `[AUDIT-ERROR]` and `WARNING` patterns
4. **Create dashboards** using the METRICS logs for performance tracking
