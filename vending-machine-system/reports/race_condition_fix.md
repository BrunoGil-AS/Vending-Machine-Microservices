# Race Condition Fix - Payment Event Processing

## Problem Identified

### Issue

A **race condition** was occurring where the `PaymentEvent` arrived at the `TransactionEventConsumer` **before** the transaction was saved to the database, causing "Transaction X not found" errors.

### Timeline of Events (Before Fix)

```bash
1. [HTTP Thread] TransactionService.purchase() called
2. [HTTP Thread] Transaction created in memory (not saved yet)
3. [HTTP Thread] processPayment() calls payment-service synchronously
4. [Payment Service] Processes payment and IMMEDIATELY publishes PaymentEvent to Kafka
5. [Kafka Thread] TransactionEventConsumer receives PaymentEvent
6. [Kafka Thread] ❌ ERROR: Transaction not found (not yet in database)
7. [HTTP Thread] Transaction saved to database
8. [HTTP Thread] Published txn-start event
```

### Root Cause

The synchronous HTTP call to `payment-service` published the `PaymentEvent` immediately, but the originating transaction wasn't yet committed to the database. The Kafka consumer was faster than the database commit.

## Solution Implemented

### Changes Made

#### 1. **PaymentService.java** - Conditional Event Publishing

```java
// New overloaded method with publishEvent flag
public PaymentTransaction processPaymentForTransaction(
    TransactionEvent transactionEvent,
    PaymentRequest paymentRequest,
    boolean publishEvent) {

    // ... payment processing logic ...

    // Only publish event when processing from Kafka (async flow)
    if (publishEvent) {
        publishPaymentEvent(transaction);
    }
}
```

**Key points:**

- HTTP endpoint calls: `publishEvent = false` (synchronous flow)
- Kafka listener calls: `publishEvent = true` (asynchronous flow)

#### 2. **TransactionService.java** - Publish Processing Event After Payment

```java
if ("SUCCESS".equals(event.getStatus())) {
    transaction.setStatus(TransactionStatus.PROCESSING);
    transaction = transactionRepository.save(transaction);

    // ✅ Publish PROCESSING event to trigger dispensing
    TransactionEvent processingEvent = new TransactionEvent(
        "txn-processing-" + transaction.getId() + "-" + System.currentTimeMillis(),
        transaction.getId(),
        "PROCESSING",
        transaction.getTotalAmount().doubleValue(),
        System.currentTimeMillis()
    );
    kafkaEventService.publishTransactionEvent(processingEvent);
}
```

### New Flow (After Fix)

#### Synchronous Purchase Flow

```bash
1. [HTTP Thread] TransactionService.purchase() called
2. [HTTP Thread] Transaction saved to database → gets ID
3. [HTTP Thread] processPayment() calls payment-service with transaction ID
4. [Payment Service] Processes payment (NO Kafka event published)
5. [Payment Service] Returns result synchronously
6. [HTTP Thread] Transaction updated to PROCESSING and saved
7. [HTTP Thread] ✅ Publishes TransactionEvent with "PROCESSING" status
8. [Dispensing Service] Receives PROCESSING event → starts dispensing
```

#### Asynchronous Event Flow (Legacy/Alternative)

```plaintext
1. TransactionService publishes "STARTED" event
2. PaymentService receives event via Kafka
3. PaymentService processes payment
4. PaymentService publishes PaymentEvent (publishEvent = true)
5. TransactionService receives PaymentEvent
6. TransactionService updates to PROCESSING
7. TransactionService publishes PROCESSING event
8. DispensingService starts dispensing
```

## Benefits

1. **No Race Condition**: Transaction is committed before any Kafka events are published
2. **Clear Separation**: Synchronous (HTTP) vs Asynchronous (Kafka) flows are distinct
3. **Proper Event Ordering**: PROCESSING event only published after payment success
4. **Idempotency**: Duplicate event handling still works via ProcessedEvent table

## Testing Instructions

### 1. Clear Databases

```sql
DELETE FROM vending_transaction.transactions;
DELETE FROM vending_transaction.processed_events;
DELETE FROM vending_payment.payment_transactions;
```

### 2. Restart Services

```bash
cd vending-machine-system
./stop-services.sh
./start-services.sh
```

### 3. Run Test

```bash
cd scripts/python
python customer_flow_test.py
```

### 4. Expected Logs (Transaction Service)

```bash
INFO  Payment processing result: true (status: SUCCESS)
INFO  Published transaction event: txn-start-1-...
INFO  Purchase transaction initiated: 1
INFO  Received payment event: ... for transaction 1
INFO  Payment successful for transaction 1, moving to PROCESSING
INFO  Published PROCESSING event for transaction 1 to trigger dispensing
```

### 5. Expected Database State

- **transactions**: status = `PROCESSING` or `COMPLETED`
- **payment_transactions**: status = `SUCCESS`
- **dispensing_operations**: status = `SUCCESS`
- **NO** "Transaction not found" errors in logs

## Files Modified

1. `payment-service/src/main/java/com/vendingmachine/payment/payment/PaymentService.java`
2. `payment-service/src/main/java/com/vendingmachine/payment/kafka/TransactionEventConsumer.java`
3. `transaction-service/src/main/java/com/vendingmachine/transaction/kafka/TransactionEventConsumer.java`

## Date

October 17, 2025
