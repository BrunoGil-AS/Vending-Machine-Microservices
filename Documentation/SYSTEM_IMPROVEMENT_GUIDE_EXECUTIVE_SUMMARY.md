# System Improvement Guide

---

## Overview

This document outlines three critical improvement areas for the vending machine microservices system:

1. **Fault Tolerance** - Making the system resilient to failures
2. **Kafka Optimization** - Reducing complexity and improving performance
3. **Refund System** - Proper compensatory actions and customer refunds

---

## 1. Fault Tolerance Enhancement

### Current Problems

❌ No circuit breaker protection - services wait indefinitely when dependencies fail  
❌ Limited retry logic - transient failures cause permanent transaction failures  
❌ No fallback strategies - system crashes instead of gracefully degrading  
❌ Missing distributed transaction coordination

### Proposed Solutions

#### 1.1 Circuit Breaker Pattern

**What It Does**: Automatically stops calling a failing service to prevent cascading failures

**Benefits**:

- Fail-fast instead of waiting on dead services (10s timeout → instant failure)
- Automatic recovery detection (service comes back online)
- Protects entire system from one failing service

**Implementation**: Use Resilience4j library with three states:

- **CLOSED** (normal): Requests pass through
- **OPEN** (service failing): Requests fail immediately
- **HALF-OPEN** (testing): Periodically check if service recovered

#### 1.2 Retry Mechanism with Exponential Backoff

**What It Does**: Automatically retry failed operations with increasing delays

**Benefits**:

- Handles transient network glitches
- Prevents overwhelming failing services
- 3 retries with smart delays: 500ms → 1000ms → 2000ms

**Example Scenario**: Network hiccup during payment → retry after 500ms → success

#### 1.3 Bulkhead Pattern

**What It Does**: Isolate resources for different services (separate thread pools)

**Benefits**:

- One slow service doesn't block others
- 10 threads for inventory, 20 for payment, 5 for dispensing
- Prevents resource exhaustion

**Analogy**: Like bulkheads on a ship - one compartment floods, others stay dry

#### 1.4 Database Resilience

**Current**: Single database connection pool
**Proposed**: Optimized HikariCP configuration + read replica strategy

**Benefits**:

- Better connection management (20 max, 5 minimum idle)
- Read-heavy queries go to replica (reduces load on primary)
- Connection leak detection

#### 1.5 Kafka Consumer Resilience

**What It Does**: Handle message processing failures gracefully

**Key Components**:

- **Dead Letter Queue (DLQ)**: Failed messages go to special topic for manual review
- **Automatic Retries**: 3 attempts with 1-second delays
- **Manual Acknowledgment**: Only mark message as processed after successful handling

**Benefits**:

- No message loss
- Failed messages don't block others
- Audit trail of failures

---

## 2. Kafka Layer Optimization

### _Current Problems_

❌ **5 separate Kafka topics** → operational nightmare  
❌ **6 network hops** for one purchase → 800-1000ms latency  
❌ **Event fragmentation** → hard to trace transactions  
❌ **No guaranteed ordering** → payment might arrive before transaction creation

### The Core Issue: Too Many Topics

**Current Architecture**:

```plaintext
transaction-events
payment-events
inventory-events
dispensing-events
notification-events
```

**Problems**:

- Each topic needs monitoring, partition management, consumer groups
- One purchase generates events across 4-5 topics
- Debugging requires checking all 5 topics and correlating timestamps

### Proposed Solution A: Domain-Centric Topics (RECOMMENDED)

**New Architecture**:

```plaintext
vending-machine-domain-events  ← All business events (single topic!)
vending-machine-notifications  ← Emails/SMS only
vending-machine-dlq           ← Failed messages
```

**How It Works**:

- All events go to one topic
- Events have a `type` field: TRANSACTION_CREATED, PAYMENT_COMPLETED, etc.
- Consumers filter by event type in code
- Partition by `transactionId` ensures ordering

**Benefits**:

| Metric               | Before (5 Topics) | After (1 Topic) | Improvement |
| -------------------- | ----------------- | --------------- | ----------- |
| Topics to monitor    | 5                 | 1               | 80% less    |
| Consumer groups      | 5                 | 1               | 80% less    |
| Event ordering       | ❌ No guarantee   | ✅ Guaranteed   | Perfect     |
| Debugging            | Check 5 places    | Check 1 place   | 5x faster   |
| Operational overhead | High              | Low             | Significant |

### Proposed Solution B: Sync HTTP for Critical Operations

**Current Flow** (Too many Kafka hops):

```plaintext
Transaction Service → Kafka → Payment → Kafka → Transaction → Kafka → Dispensing
Time: 800-1000ms | Hops: 6 | Complexity: High
```

**Optimized Flow** (Direct HTTP calls):

```plaintext
Transaction Service → HTTP → Inventory (check availability)
                   → HTTP → Payment (process payment)
                   → HTTP → Dispensing (dispense item)
                   → Kafka → Notifications (async, fire-and-forget)
Time: 200-300ms | Hops: 3 | Complexity: Low
```

**Decision Matrix - When to Use Each:**

| Operation          | Use             | Reason                                         |
| ------------------ | --------------- | ---------------------------------------------- |
| Check inventory    | **Sync HTTP**   | Need immediate answer before charging customer |
| Process payment    | **Sync HTTP**   | Must know payment succeeded before dispensing  |
| Dispense item      | **Sync HTTP**   | Customer waiting at machine                    |
| Send email receipt | **Async Kafka** | Customer already has item, email can wait      |
| Update stock count | **Async Kafka** | Background task, eventual consistency OK       |
| Analytics          | **Async Kafka** | Background processing                          |

**Rule of Thumb**: If customer is waiting → Sync HTTP. If background work → Async Kafka.

### Proposed Solution C: Two-Tier Event Deduplication

**Problem**: Kafka delivers messages "at least once" → duplicates happen

**Current Approach**:

- Database query for every event (20ms)
- High database load

**Optimized Approach**:

- **Tier 1**: Redis cache (1ms lookup)
- **Tier 2**: Database (20ms, fallback only)

**Performance Improvement**:

```plaintext
Database Only: 60ms for 3 events
Redis + DB:    22ms for 3 events
Improvement:   63% faster
```

**How It Works**:

1. Check Redis first (99% hit rate after warmup)
2. If not in Redis, check database
3. Save to both when marking processed
4. Cache expires after 24 hours

---

## 3. Compensatory Actions & Refund System

### **Current Problems**

❌ Basic refund logic - simple REST call, no tracking  
❌ No refund state management  
❌ No partial refund support  
❌ No idempotency - duplicate refund requests charge customer twice  
❌ No audit trail

### Proposed Solution: Comprehensive Refund System

#### 3.1 Saga Pattern for Distributed Transactions

**What It Is**: Coordinated sequence of operations with automatic compensation (rollback)

**Transaction Flow**:

```plaintext
1. Reserve Inventory ✅
2. Process Payment ✅
3. Dispense Item ❌ FAILED!
4. Compensate: Refund Payment ✅
5. Compensate: Release Inventory ✅
```

**Key Concept**: If any step fails, automatically undo (compensate) previous steps in reverse order

**Benefits**:

- Automatic rollback on failure
- Maintains data consistency across services
- Audit trail of what was attempted and compensated

#### 3.2 Dedicated Refund Entity

**New Database Table**: `refunds`

**Key Fields**:

- `refundId` (UUID) - for idempotency
- `status` - PENDING, PROCESSING, COMPLETED, FAILED
- `refundType` - FULL or PARTIAL
- `reason` - DISPENSING_FAILED, TIMEOUT, CUSTOMER_REQUEST, etc.
- `retryCount` - track retry attempts
- Timestamps for created/processed/completed

**Idempotency Guarantee**:

- Same `refundId` submitted twice → return existing refund, don't charge again
- Prevents accidental double refunds

#### 3.3 Refund Service with Retry Logic

**Process Flow**:

1. Check if refund already exists (idempotency)
2. Validate original payment exists and is completed
3. Check refund amount doesn't exceed remaining balance
4. Create refund record (PENDING)
5. Call payment gateway (PROCESSING)
6. If success → mark COMPLETED
7. If failure → retry up to 3 times with delays
8. If all retries fail → alert admin for manual review

**Partial Refund Support**:

- Track total refunded amount per original payment
- Allow multiple partial refunds until original amount reached
- Example: $10 purchase, dispense 2 of 3 items → refund $3.33

#### 3.4 Compensation Strategies for Different Scenarios

**Scenario 1 - Payment Succeeded, Dispensing Failed:**

```plaintext
Actions:
1. Issue full refund
2. Release inventory reservation
3. Notify customer: "Purchase failed, refund processing"
4. Mark transaction as REFUNDED
```

**Scenario 2 - Partial Dispensing:**

```plaintext
Purchase: 3 items for $9
Dispensed: 2 items successfully
Failed: 1 item

Actions:
1. Calculate partial refund ($3)
2. Issue partial refund
3. Notify customer: "2 items dispensed, $3 refunded for failed item"
4. Mark transaction as PARTIALLY_COMPLETED
```

**Scenario 3 - Transaction Timeout:**

```plaintext
Transaction stuck in PROCESSING for > 5 minutes

Actions:
1. Automatic timeout detection (background job)
2. Full refund
3. Release inventory
4. Notify customer: "Transaction timeout, full refund issued"
```

#### 3.5 Automated Refund Recovery

**Background Jobs**:

**Job 1: Retry Failed Refunds** (runs every 15 minutes)

- Find refunds with status=FAILED and retryCount < 3
- Retry refund processing
- Alert admin if max retries reached

**Job 2: Monitor Stuck Refunds** (runs every 30 minutes)

- Find refunds in PROCESSING state for > 1 hour
- Alert admin for manual intervention

**Benefits**:

- Automatic recovery from transient failures
- Early detection of stuck refunds
- Reduces manual intervention needed

---

## 4. Implementation Roadmap

### Phase 1: Critical Fault Tolerance (Weeks 1-2)

**Priority**: HIGH | **Effort**: Medium

✅ Add Resilience4j dependencies  
✅ Implement circuit breakers for all HTTP clients  
✅ Configure retry mechanisms  
✅ Add health indicators  
✅ Implement Kafka DLQ

**Impact**: Immediate improvement in system stability

### Phase 2: Refund System (Weeks 3-4)

**Priority**: HIGH | **Effort**: Medium

✅ Create Refund entity and repository  
✅ Implement RefundService with idempotency  
✅ Add compensation strategies  
✅ Implement automated recovery jobs  
✅ Add admin API endpoints

**Impact**: Better customer experience, reduced manual work

### Phase 3: Kafka Optimization (Weeks 5-6)

**Priority**: MEDIUM | **Effort**: High

✅ Create unified DomainEvent model  
✅ Consolidate to 1-2 topics  
✅ Migrate sync operations from Kafka to HTTP  
✅ Implement Redis-based deduplication  
✅ Deploy DLQ

**Impact**: 60% latency reduction, simpler operations

### Phase 4: Saga Pattern (Weeks 7-8)

**Priority**: MEDIUM | **Effort**: High

✅ Design saga entities  
✅ Implement SagaOrchestrator  
✅ Create compensators  
✅ Add saga state persistence  
✅ Implement recovery mechanisms

**Impact**: Better transaction coordination

### Phase 5: Monitoring & Observability (Weeks 9-10)

**Priority**: LOW | **Effort**: Medium

✅ Distributed tracing (Sleuth + Zipkin)  
✅ Metrics export (Prometheus)  
✅ Grafana dashboards  
✅ Custom health indicators  
✅ Alerting setup

**Impact**: Better visibility, faster issue detection

---

## Key Metrics to Track

### Before Implementation

| Metric                         | Current Value |
| ------------------------------ | ------------- |
| Average transaction latency    | 800-1000ms    |
| Failed transactions (no retry) | ~5%           |
| Manual refund interventions    | ~20/week      |
| Kafka topics to monitor        | 5             |
| Consumer lag incidents         | ~10/month     |

### After Implementation (Expected)

| Metric                           | Target Value | Improvement       |
| -------------------------------- | ------------ | ----------------- |
| Average transaction latency      | 200-300ms    | **70% faster**    |
| Failed transactions (with retry) | ~1%          | **80% reduction** |
| Manual refund interventions      | ~2/week      | **90% reduction** |
| Kafka topics to monitor          | 1-2          | **60-80% less**   |
| Consumer lag incidents           | ~2/month     | **80% reduction** |

---

## Quick Decision Summary

### Should We Implement These Changes?

**YES, because**:

✅ **Fault Tolerance** → System crashes less, recovers automatically  
✅ **Kafka Optimization** → 70% faster transactions, simpler operations  
✅ **Refund System** → Better customer experience, 90% less manual work  
✅ **Total Timeline** → 10 weeks for complete implementation  
✅ **Incremental Deployment** → Can be done in phases without system downtime

**Risks Mitigated**:

⚠️ **Customer complaints** → Proper refunds with tracking  
⚠️ **Operational overhead** → Fewer topics, automated recovery  
⚠️ **Data inconsistency** → Saga pattern ensures consistency  
⚠️ **System downtime** → Circuit breakers prevent cascading failures

---

## Questions for Discussion

1. **Priority**: Should we focus on fault tolerance first (highest impact) or Kafka optimization (biggest change)?
2. **Timeline**: Is 10-week timeline acceptable, or should we compress to 6-8 weeks?
3. **Resources**: Do we need additional developers, or can current team handle it?
4. **Testing**: How much time for testing between phases?
5. **Rollback**: What's our rollback strategy if a phase fails?

---

## Next Steps

1. **This Week**: Review and approve this proposal
2. **Week 1**: Set up development environment, add Resilience4j dependencies
3. **Week 2**: Begin Phase 1 implementation (Circuit Breakers)
4. **Ongoing**: Weekly progress reviews, adjust timeline as needed

---

**Document Version**: 1.0  
**Last Updated**: October 29, 2025  
**Prepared By**: Development Team  
**For**: System Improvement Planning Meeting
