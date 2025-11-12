# Phase 2: Kafka Optimization - Implementation Plan

## Overview

**Goal**: Transform Kafka architecture from complex multi-topic system to streamlined domain-centric approach while migrating critical operations to synchronous HTTP.

**Duration**: Weeks 3-4 (2 weeks)
**Priority**: 🚀 **HIGH**
**Risk Level**: High (due to messaging migration)
**Expected Benefits**:

- 70% latency reduction (1000ms → 300ms)
- 80% complexity reduction (5 topics → 1 topic)
- Improved system reliability and debugging

---

## Current State Analysis

### Existing Architecture Problems

**Topic Proliferation** (5 Topics):

```plaintext
   transaction-events    → Transaction lifecycle
   payment-events       → Payment processing
   inventory-events     → Stock updates
   dispensing-events    → Hardware operations
   notification-events  → System alerts
```

**Excessive Message Hops**:

```plaintext
   Transaction → Kafka → Payment → Kafka → Transaction → Kafka → Dispensing
```

**Latency Issues**:

- Current transaction time: ~1000ms
- 80% spent in Kafka message routing
- Unnecessary async operations for critical path

---

## Target Architecture

### New Topic Structure

```plaintext
vending-machine-domain-events → All business domain events
vending-machine-dlq          → Dead letter queue
```

### Communication Pattern

```plaintext
Critical Path (Sync HTTP):
Transaction → HTTP → Payment (immediate response)
           → HTTP → Dispensing (immediate response)

Non-Critical (Async Kafka):
           → Kafka → Notifications
           → Kafka → Analytics
           → Kafka → Stock Updates
```

---

## Implementation Tasks

### Task 1: Create DomainEvent Model

**Priority**: 🔴 **CRITICAL** (Foundation for all other tasks)

**Objective**: Create unified event structure in common-library

**Steps**:

1. **Design DomainEvent Class**:

   ```java
   @Data
   @Builder
   @NoArgsConstructor
   @AllArgsConstructor
   public class DomainEvent {
       private String eventId;
       private String eventType;
       private String aggregateId;  // transactionId
       private String aggregateType; // TRANSACTION, PAYMENT, etc.
       private Long timestamp;
       private String payload; // JSON string
       private Map<String, String> metadata;

       // Factory methods for type safety
       public static DomainEvent transactionCreated(Long transactionId, String payload);
       public static DomainEvent paymentCompleted(Long transactionId, String payload);
   }
   ```

2. **Create Factory Methods**: One for each event type
3. **Add Serialization**: JSON support with ObjectMapper
4. **Build Common Library**: Deploy updated version

**Acceptance Criteria**:

- ✅ DomainEvent class compiles and builds
- ✅ Factory methods create properly formatted events
- ✅ JSON serialization/deserialization works
- ✅ All services can access updated common-library

**Time Estimate**: 1 day

---

### Task 2: Consolidate Kafka Topics

**Priority**: 🔴 **CRITICAL**

**Objective**: Replace 5 topics with 1 unified domain events topic

**Steps**:

1. **Create New Topic Configuration**:

   ```java
   @Bean
   public NewTopic domainEventsTopic() {
       return TopicBuilder.name("vending-machine-domain-events")
               .partitions(3) // Partition by transaction ID
               .replicas(1) // Single replica for development
               .config("retention.ms", "604800000") // 7 days
               .build();
   }
   ```

2. **Implement Dual Publishing**:

   - Publish to both old and new topics simultaneously
   - Ensure no event loss during migration
   - Log publishing to both topics for monitoring

3. **Update Event Publishers**:
   - Modify all services to use DomainEvent format
   - Maintain backward compatibility with old events
   - Add partition key strategy (use transactionId)

**Migration Strategy**:

- **Week 1**: Deploy dual publishing (both topics active)
- **Week 2**: Migrate consumers to new topic
- **Week 3**: Disable old topic publishing
- **Week 4**: Remove old topics

**Acceptance Criteria**:

- ✅ New topic created and accessible
- ✅ Events published to both old and new topics
- ✅ Zero event loss during dual publishing
- ✅ Event ordering maintained per transaction

**Time Estimate**: 2 days

---

### Task 3: Migrate to Synchronous HTTP

**Priority**: 🔴 **CRITICAL** (Core latency improvement)

**Objective**: Replace Kafka with HTTP for real-time operations

**Steps**:

1. **Create TransactionOrchestrator**:

   ```java
   @Service
   @RequiredArgsConstructor
   public class TransactionOrchestrator {

       @CircuitBreaker(name = "inventory-service")
       private boolean checkInventorySync(Long productId, Integer quantity);

       @CircuitBreaker(name = "payment-service")
       private PaymentResponse processPaymentSync(Transaction transaction);

       @CircuitBreaker(name = "dispensing-service")
       private DispensingResponse dispenseItemSync(Transaction transaction);
   }
   ```

2. **Decision Matrix Implementation**:

   | Operation       | Communication   | Reason                    |
   | --------------- | --------------- | ------------------------- |
   | Check inventory | **Sync HTTP**   | Need immediate response   |
   | Process payment | **Sync HTTP**   | Transaction consistency   |
   | Dispense item   | **Sync HTTP**   | Immediate feedback needed |
   | Update stock    | **Async Kafka** | Eventually consistent OK  |
   | Notifications   | **Async Kafka** | Fire-and-forget           |

3. **Update Service Clients**:
   - Add circuit breakers to all HTTP calls
   - Implement fallback methods
   - Add retry mechanisms with exponential backoff

**Acceptance Criteria**:

- ✅ Critical path uses synchronous HTTP calls
- ✅ Circuit breakers protect against service failures
- ✅ Transaction latency < 300ms (target)
- ✅ Fallback mechanisms work correctly

**Time Estimate**: 3 days

---

### Task 4: Create Event Routing System

**Priority**: 🟡 **MEDIUM**

**Objective**: Implement intelligent event routing in unified consumer

**Steps**:

1. **Create Unified Consumer**:

   ```java
   @KafkaListener(topics = "vending-machine-domain-events")
   public void consumeDomainEvent(DomainEvent event) {
       switch (event.getEventType()) {
           case "PAYMENT_COMPLETED":
               paymentEventHandler.handle(event);
               break;
           case "ITEM_DISPENSED":
               dispensingEventHandler.handle(event);
               break;
           // Route based on event type
       }
   }
   ```

2. **Create Event Handlers**:

   - Separate handler class for each event type
   - Maintain service isolation
   - Add proper error handling and logging

3. **Implement Event Filtering**:
   - Services only process relevant events
   - Reduce unnecessary processing
   - Maintain backward compatibility

**Acceptance Criteria**:

- ✅ Events route to correct handlers
- ✅ Services only process relevant events
- ✅ Error handling prevents event loss
- ✅ Performance improvement measurable

**Time Estimate**: 2 days

---

### Task 5: Optimize Event Deduplication

**Priority**: 🟡 **MEDIUM**

**Objective**: Improve deduplication with Redis cache-first strategy

**Steps**:

1. **Implement Redis Caching**:

   ```java
   @Service
   public class EventDeduplicationService {

       private final RedisTemplate<String, String> redisTemplate;

       public boolean isEventProcessed(String eventId, String eventType) {
           String cacheKey = "processed:event:" + eventType + ":" + eventId;

           // Check Redis first (fast)
           if (redisTemplate.hasKey(cacheKey)) {
               return true;
           }

           // Fallback to database (slower)
           boolean processed = processedEventRepository.exists(eventId, eventType);

           // Update cache for future
           if (processed) {
               redisTemplate.opsForValue().set(cacheKey, "1", Duration.ofHours(24));
           }

           return processed;
       }
   }
   ```

2. **Cache Strategy**:
   - Redis as primary cache (sub-millisecond lookup)
   - Database as fallback (eventual consistency)
   - 24-hour TTL for cache entries

**Acceptance Criteria**:

- ✅ Deduplication checks < 5ms (vs current 50ms)
- ✅ Cache hit rate > 80%
- ✅ Zero duplicate event processing
- ✅ Fallback to database works correctly

**Time Estimate**: 1 day

---

## Implementation Schedule

### Week 3: Foundation & Topic Migration

**Monday**:

- [x] Task 1: Create DomainEvent model
- [x] Update common-library and deploy

**Tuesday-Wednesday**:

- [ ] Task 2: Create new topic configuration
- [ ] Implement dual publishing mechanism

**Thursday-Friday**:

- [ ] Task 2: Deploy dual publishing
- [ ] Monitor both topics for consistency
- [ ] Begin Task 3: Design TransactionOrchestrator

### Week 4: HTTP Migration & Optimization

**Monday-Tuesday**:

- [ ] Task 3: Implement synchronous HTTP calls
- [ ] Add circuit breakers to all HTTP clients

**Wednesday**:

- [ ] Task 3: Performance testing and optimization
- [ ] Task 4: Begin event routing system

**Thursday**:

- [ ] Task 4: Complete event routing
- [ ] Task 5: Redis deduplication implementation

**Friday**:

- [ ] Integration testing
- [ ] Performance benchmarking
- [ ] Prepare for deployment

---

## Risk Management

### High Risks

#### 1. Event Loss During Migration

**Mitigation**:

- Dual publishing ensures no events lost
- Comprehensive monitoring during migration
- Rollback capability within 30 minutes

#### 2. Performance Degradation

**Mitigation**:

- Load testing before production deployment
- Circuit breakers prevent cascade failures
- Gradual rollout (10% → 50% → 100%)

### Medium Risks

#### 3. Service Dependency Failures

**Mitigation**:

- Circuit breakers on all HTTP calls
- Fallback mechanisms implemented
- Comprehensive retry logic

#### 4. Redis Cache Failures

**Mitigation**:

- Database fallback always available
- Cache failures don't break functionality
- Redis clustering for high availability

---

## Success Metrics

### Primary KPIs

- **Latency Reduction**: < 300ms transaction time (from 1000ms)
- **Topic Consolidation**: 5 topics → 1 topic
- **Error Rate**: < 2% failed transactions
- **Event Processing**: 60% faster deduplication

### Secondary Metrics

- Circuit breaker effectiveness (< 1% false positives)
- Cache hit rate (> 80%)
- Event ordering maintained
- Zero event loss during migration

---

## Testing Strategy

### Performance Testing

- **Load Test**: 200 concurrent users
- **Stress Test**: 500 transactions/second
- **Latency Test**: P95 latency < 400ms

### Migration Testing

- **Dual Publishing**: Verify events in both topics
- **Consumer Migration**: Zero event loss
- **Rollback Test**: < 30 minutes recovery

### Integration Testing

- **End-to-End Transaction Flow**
- **Circuit Breaker Activation**
- **Event Routing Accuracy**

---

## Deployment Strategy

### Phase 1: Infrastructure (Week 3)

1. Deploy new topic configuration
2. Deploy dual publishing mechanism
3. Monitor for 48 hours

### Phase 2: Consumer Migration (Week 4)

1. Deploy new consumers (consume from both topics)
2. Monitor for 48 hours
3. Gradually disable old consumers

### Phase 3: HTTP Migration (Week 4)

1. Deploy TransactionOrchestrator
2. A/B test (10% traffic to new flow)
3. Gradual rollout based on metrics

### Rollback Triggers

- Event loss detected
- Latency increase > 20%
- Error rate > 5%
- Circuit breaker malfunction

---

## Next Steps

1. **Today**: Start Task 1 - Create DomainEvent model
2. **This Week**: Complete foundation and topic setup
3. **Next Week**: HTTP migration and optimization
4. **Week 5**: Begin Phase 3 (Refund System)

Ready to start implementation? I'll begin with creating the DomainEvent model in the common-library.
