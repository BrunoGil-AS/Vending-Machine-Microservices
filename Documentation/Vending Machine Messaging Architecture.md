# Vending Machine Messaging Architecture

## Synchronous Flow (Real-time Transaction Processing)

```plain
Customer Purchase Request
         ↓
API Gateway (OAuth2 validation)
         ↓
Transaction Service
         ↓ (Sync REST call)
Inventory Service.checkAvailability()
         ↓ (Immediate response)
Transaction Service.processPayment()
         ↓ (Sync REST call)
Payment Service.processPayment()
         ↓ (Sync REST call)
Dispensing Service.dispenseItem()
```

## Asynchronous Flow (Event-Driven Updates)

```plain
Successful Item Dispensing
         ↓
Kafka Event: "item-dispensed"
         ↓
Inventory Service (Consumer)
         ↓ (Stock level check)
[If stock < threshold]
         ↓
Kafka Event: "low-stock-alert"
         ↓
Notification Service (Consumer)
         ↓
Admin Alert Sent
```

## Service Communication Patterns

### 1. Transaction Processing (Synchronous)

- **Pattern**: Request-Response with Circuit Breaker
- **Timeout**: 3 seconds max per service call
- **Retry**: 3 attempts with exponential backoff
- **Fallback**: Return cached data or graceful degradation

### 2. Inventory Updates (Asynchronous)

- **Pattern**: Event Sourcing with Kafka
- **Topics**:
  - `inventory-updates`: Stock level changes
  - `inventory-alerts`: Low stock notifications
  - `transaction-events`: Purchase completions
- **Guarantee**: At-least-once delivery with idempotent consumers

### 3. Error Handling & Compensation

- **Saga Pattern**: For distributed transaction management
- **Dead Letter Queue**: For failed event processing
- **Compensating Actions**: Rollback inventory reservations

## Key Components

### Inventory Service

```java
@Service
public class InventoryService {

    // Synchronous - for transaction processing
    @Transactional(readOnly = true)
    public boolean isAvailable(String itemId, int quantity) {
        return stockRepository.findByItemId(itemId)
                .map(stock -> stock.getQuantity() >= quantity)
                .orElse(false);
    }

    // Asynchronous - for event processing
    @KafkaListener(topics = "item-dispensed")
    @Transactional
    public void updateStockAfterDispensing(ItemDispensedEvent event) {
        Stock stock = stockRepository.findByItemId(event.getItemId());
        stock.decreaseQuantity(event.getQuantity());
        stockRepository.save(stock);

        // Check if low stock alert needed
        if (stock.getQuantity() <= stock.getThreshold()) {
            publishLowStockAlert(stock);
        }
    }
}
```

### Transaction Service

```java
@Service
public class TransactionService {

    @Autowired
    private InventoryServiceClient inventoryClient;

    @CircuitBreaker(name = "inventory-service", fallbackMethod = "fallbackTransaction")
    @Transactional
    public TransactionResult processTransaction(TransactionRequest request) {
        // Synchronous call for immediate consistency
        if (!inventoryClient.reserveItem(request.getItemId(), request.getQuantity())) {
            throw new InsufficientStockException("Item not available");
        }

        // Continue with payment processing...
        PaymentResult paymentResult = paymentService.processPayment(request.getPayment());

        if (paymentResult.isSuccessful()) {
            // Async event for inventory update
            publishItemDispensedEvent(request);
        }

        return TransactionResult.success();
    }
}
```

## Benefits of This Hybrid Approach

### Synchronous Benefits

- **Immediate Consistency**: Prevents overselling
- **Real-time Validation**: Instant feedback to customers
- **Simple Debugging**: Easier to trace transaction flows
- **Data Integrity**: Strong consistency for critical operations

### Asynchronous Benefits

- **System Resilience**: Services can operate independently
- **Scalability**: Better handling of high-volume operations
- **Loose Coupling**: Services don't block each other
- **Event Audit Trail**: Complete history of system events

## Monitoring & Observability

### Metrics to Track

- Synchronous call latency (< 500ms target)
- Kafka message processing lag (< 1 second target)
- Circuit breaker status and failure rates
- Inventory consistency across services

### Health Checks

- Service-to-service connectivity
- Kafka broker connectivity
- Database connection pools
- Cache hit rates

This architecture ensures your vending machine system maintains data consistency for critical transactions while leveraging asynchronous processing for scalability and resilience.
