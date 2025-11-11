# System Improvement Implementation Plan

**Project**: Vending Machine Microservices Enhancement  
**Start Date**: November 4, 2025  
**Estimated Completion**: January 13, 2026 (10 weeks)  
**Team Size**: 2-3 Developers  
**Status**: 📋 Planning Phase

---

## Overview

### Goals

✅ Improve system resilience and fault tolerance  
✅ Reduce transaction latency by 70% (1000ms → 300ms)  
✅ Simplify Kafka architecture (5 topics → 1-2 topics)  
✅ Implement comprehensive refund system with 90% automation  
✅ Reduce failed transactions by 80%

### Guiding Principles

1. **Incremental Deployment** - Each phase can be deployed independently
2. **No Downtime** - All changes must be backward compatible
3. **Testing First** - Comprehensive tests before production deployment
4. **Metrics Driven** - Measure before/after for each phase
5. **Rollback Ready** - Ability to revert any phase within 30 minutes

### 🚨 **STRATEGIC DECISION: Kafka Optimization FIRST**

**Reasoning**:

- Refund system depends on reliable messaging infrastructure
- Kafka optimization reduces latency from 1000ms → 300ms
- Simplified architecture (5 topics → 1 topic) easier to debug
- Foundation for all subsequent messaging-based features

---

## Phase 1: Critical Fault Tolerance (Weeks 1-2)

**Priority**: 🔴 **CRITICAL**  
**Estimated Effort**: 80 hours  
**Team**: 2 developers  
**Risk Level**: Low

### Week 1: Setup & Circuit Breakers

#### Day 1-2: Environment Setup & Dependencies

**Tasks**:

- [x] Create feature branch: `feature/fault-tolerance`
- [x] Add Resilience4j dependencies to parent `pom.xml`
- [x] Add Spring AOP dependency
- [x] Update all service POMs to inherit new dependencies
- [x] Run `mvn clean install` to verify build

**Deliverables**:

- ✅ All services build successfully with new dependencies
- ✅ No version conflicts

**Testing**:

- Build verification
- Dependency tree analysis

---

#### Day 3-5: Circuit Breaker Implementation

**Tasks**:

**Transaction Service**:

- [x] Create `InventoryServiceClient` with circuit breaker
- [x] Create `PaymentServiceClient` with circuit breaker
- [x] Create `DispensingServiceClient` with circuit breaker
- [x] Add fallback methods for each client
- [x] Configure circuit breaker properties in `application.properties`

**Configuration**:

- [x] Set `slidingWindowSize=10`
- [x] Set `failureRateThreshold=50`
- [x] Set `waitDurationInOpenState=10s` (inventory)
- [x] Set `waitDurationInOpenState=15s` (payment)
- [x] Set `waitDurationInOpenState=10s` (dispensing)

**Deliverables**:

- ✅ Circuit breakers on all synchronous service calls
- ✅ Fallback methods implemented
- ✅ Configuration externalized

**Testing**:

- [x] Unit tests for circuit breaker activation
- [x] Integration tests simulating service failures
- [x] Verify circuit opens after threshold failures
- [x] Verify circuit closes after recovery

**Success Metrics**:

- Circuit breaker opens within 10 failed calls
- Fallback methods execute correctly
- System doesn't cascade fail when service is down

---

### Week 2: Retry Logic & Kafka Resilience

#### Day 1-2: Retry Mechanisms

**Tasks**:

- [x] Add `@Retry` annotations to all HTTP client methods
- [x] Configure exponential backoff multiplier (2x)
- [x] Set retry exceptions (ConnectException, ResourceAccessException)
- [x] Configure different retry counts per service:
  - Inventory: 3 retries
  - Payment: 3 retries
  - Dispensing: 2 retries

**Deliverables**:

- ✅ Retry logic on all external calls
- ✅ Exponential backoff configured

**Testing**:

- [x] Verify retry attempts with delays
- [x] Ensure retries stop after max attempts
- [x] Test exponential backoff timing

---

#### Day 3-4: Kafka Dead Letter Queue

**Tasks**:

- [x] Create DLQ topic in `KafkaTopicConfig`
- [x] Implement `KafkaErrorHandler` class
- [x] Configure error handler in consumer factories
- [x] Update all `@KafkaListener` with error handling
- [x] Create `FailedEvent` entity for database persistence

**Deliverables**:

- ✅ DLQ topic created for each main topic
- ✅ Error handler routes failed messages to DLQ
- ✅ Database table for critical failures

**Testing**:

- [x] Force consumer exception, verify DLQ routing
- [x] Verify failed events saved to database
- [x] Test manual replay from DLQ

---

#### Day 5: Health Indicators & Database Optimization

**Tasks**:

- [ ] Create `DatabaseHealthIndicator` (requires actuator fix)
- [ ] Create `KafkaHealthIndicator` (requires actuator fix)
- [ ] Create `CircuitBreakerHealthIndicator` (requires actuator fix)
- [x] Add HikariCP optimized configuration
- [x] Run comprehensive integration tests

**Deliverables**:

- ⚠️ Health checks at `/actuator/health` (partially complete)
- ✅ Database connection pool optimized
- ✅ All tests passing

**Testing**:

- [x] Full end-to-end purchase flow
- [ ] Chaos testing (kill services randomly)
- [ ] Load testing (100 concurrent transactions)

---

### Phase 1 Deployment

**Pre-Deployment Checklist**:

- [ ] All unit tests passing (>90% coverage)
- [ ] Integration tests passing
- [ ] Code review completed
- [ ] Documentation updated
- [ ] Rollback plan documented

**Deployment Steps**:

1. Deploy to staging environment
2. Run smoke tests (1 hour)
3. Monitor circuit breaker metrics
4. Deploy to production (blue-green deployment)
5. Monitor for 24 hours

**Rollback Trigger**:

- Circuit breakers not functioning
- Increased error rate > 5%
- Performance degradation > 20%

---

## Phase 2: Kafka Optimization (Weeks 3-4) ⚡ **PRIORITIZED**

**Priority**: � **HIGH**  
**Estimated Effort**: 100 hours  
**Team**: 3 developers  
**Risk Level**: High

### Week 3: Domain Event Model & Topic Consolidation

#### Day 1-2: DomainEvent Model

**Tasks**:

- [ ] Create feature branch: `feature/kafka-optimization`
- [ ] Create `DomainEvent` class in common-library:
  - eventId, eventType, aggregateId, aggregateType
  - timestamp, payload, metadata
- [ ] Create factory methods for each event type:
  - transactionCreated(), paymentCompleted(), itemDispensed()
  - stockUpdated(), refundProcessed(), etc.
- [ ] Add JSON serialization/deserialization support
- [ ] Build and deploy common-library

**Deliverables**:

- ✅ DomainEvent model created
- ✅ Factory methods for all event types
- ✅ Common library updated

**Testing**:

- [ ] Serialization/deserialization tests
- [ ] Factory method tests

---

#### Day 3-5: Topic Configuration & Migration

**Tasks**:

- [ ] Create new topic: `vending-machine-domain-events` (3 partitions)
- [ ] Update `KafkaTopicConfig` in all services
- [ ] Create dual-publishing mechanism (old + new topics)
- [ ] Update event publishers to publish to both topics
- [ ] Configure partition key as aggregateId (transactionId)

**Deliverables**:

- ✅ New unified topic created
- ✅ Dual publishing active

**Testing**:

- [ ] Verify events published to both topics
- [ ] Verify partition distribution
- [ ] Verify event ordering per transaction

---

### Week 4: Consumer Migration & Sync HTTP

#### Day 1-3: Event Consumer Refactoring

**Tasks**:

- [ ] Create `DomainEventConsumer` in each service
- [ ] Implement event type routing (switch statement)
- [ ] Create event handler classes:
  - `PaymentEventHandler`
  - `DispensingEventHandler`
  - `InventoryEventHandler`
- [ ] Migrate existing consumers to new pattern
- [ ] Keep old consumers active (dual consumption)
- [ ] Add logging for event routing

**Deliverables**:

- ✅ New consumers consuming from unified topic
- ✅ Old consumers still active (safe migration)

**Testing**:

- [ ] Test event routing to correct handlers
- [ ] Test event type filtering
- [ ] End-to-end transaction flow

---

#### Day 4-5: Synchronous HTTP Migration

**Tasks**:

- [ ] Create `TransactionOrchestrator` service
- [ ] Implement synchronous flow:
  - checkInventorySync()
  - processPaymentSync()
  - dispenseItemSync()
- [ ] Add circuit breakers to all sync calls
- [ ] Update transaction flow to use orchestrator
- [ ] Keep Kafka events for notifications only
- [ ] Performance testing

**Deliverables**:

- ✅ Orchestrator pattern implemented
- ✅ Critical path now synchronous

**Testing**:

- [ ] Measure transaction latency (target: <300ms)
- [ ] Test failure scenarios with circuit breakers
- [ ] Load testing (200 concurrent transactions)

### Phase 2 Deployment (Gradual Migration)

**Week 3 Deployment**: New topic + dual publishing
**Week 4 Deployment**: Consumer migration + sync HTTP

**Pre-Deployment Checklist**:

- [ ] All services updated with new event model
- [ ] Performance benchmarks documented
- [ ] Rollback to old topics tested

**Deployment Steps**:

1. Deploy new topic and dual publishing
2. Monitor for 1 week (both topics active)
3. Deploy new consumers (consume from both topics)
4. Monitor for 1 week
5. Disable old consumers
6. Monitor for 1 week
7. Deprecate old topics (retain data for 30 days)

**Rollback Trigger**:

- Event loss detected
- Consumer lag > 10 seconds
- Transaction latency increase > 20%

---

## Phase 3: Refund System (Weeks 5-6) 💰 **DEPENDS ON KAFKA**

**Priority**: 🟡 **MEDIUM**  
**Estimated Effort**: 80 hours  
**Team**: 2 developers  
**Risk Level**: Medium

### Week 5: Refund Entity & Core Service

#### Day 1-2: Database Schema & Entity

**Tasks**:

- [ ] Create feature branch: `feature/refund-system`
- [ ] Design `refunds` table schema
- [ ] Create `Refund` entity class with all fields:
  - id, refundId, transactionId, originalPaymentId
  - refundAmount, originalAmount
  - refundType (FULL/PARTIAL), status, reason
  - timestamps, initiatedBy, retryCount
- [ ] Create `RefundRepository` with custom queries
- [ ] Add database migration script
- [ ] Update `PaymentTransaction` entity with refund relationship

**Deliverables**:

- ✅ Refund entity created
- ✅ Database migration tested
- ✅ Repository with custom queries

**Testing**:

- [ ] Schema creation tests
- [ ] Repository CRUD tests
- [ ] Query performance tests

---

## Phase 4: Saga Pattern (Weeks 7-8)

**Priority**: 🟡 **MEDIUM**  
**Estimated Effort**: 80 hours  
**Team**: 2 developers  
**Risk Level**: Medium

### Week 7: Saga Infrastructure

#### Day 1-3: Saga Entities & Repository

**Tasks**:

- [ ] Create feature branch: `feature/saga-pattern`
- [ ] Create `TransactionSaga` entity
- [ ] Create `SagaStep` embedded class
- [ ] Create `SagaStatus` enum
- [ ] Create `SagaRepository`
- [ ] Add database migration for saga tables

**Deliverables**:

- ✅ Saga persistence layer created

**Testing**:

- [ ] Saga CRUD tests
- [ ] State transition tests

---

#### Day 4-5: Saga Orchestrator

**Tasks**:

- [ ] Create `SagaOrchestrator` service
- [ ] Implement `executeTransactionSaga()` method
- [ ] Implement `compensateSaga()` method
- [ ] Create step execution tracking
- [ ] Add saga state persistence

**Deliverables**:

- ✅ Saga orchestrator implemented
- ✅ Step tracking active

**Testing**:

- [ ] Test successful saga completion
- [ ] Test saga compensation on failure

---

### Week 8: Compensators & Integration

#### Day 1-3: Compensator Implementation

**Tasks**:

- [ ] Create `InventoryCompensator` service
- [ ] Create `PaymentCompensator` service
- [ ] Create `DispensingCompensator` service
- [ ] Implement compensation logic for each
- [ ] Add idempotency to compensations

**Deliverables**:

- ✅ All compensators implemented

**Testing**:

- [ ] Test each compensator independently
- [ ] Test compensation order (reverse)

---

#### Day 4-5: Integration & Recovery

**Tasks**:

- [ ] Integrate saga with `TransactionOrchestrator`
- [ ] Create `SagaRecoveryService` for stuck sagas
- [ ] Add saga monitoring endpoints
- [ ] Update transaction flow to use saga
- [ ] Comprehensive testing

**Deliverables**:

- ✅ Saga fully integrated
- ✅ Recovery mechanisms active

**Testing**:

- [ ] End-to-end saga tests
- [ ] Compensation tests
- [ ] Recovery tests

---

### Phase 4 Deployment

**Deployment Steps**:

1. Deploy to staging
2. Run saga simulation tests
3. Monitor compensation accuracy
4. Gradual rollout to production (10% → 50% → 100%)

**Rollback Trigger**:

- Compensation failures
- Saga state corruption
- Increased transaction failures

---

## Phase 5: Monitoring & Observability (Weeks 9-10)

**Priority**: 🟢 **LOW**  
**Estimated Effort**: 80 hours  
**Team**: 2 developers  
**Risk Level**: Low

### Week 9: Distributed Tracing

#### Day 1-3: Sleuth & Zipkin Setup

**Tasks**:

- [ ] Add Spring Cloud Sleuth dependencies
- [ ] Add Zipkin dependencies
- [ ] Configure Zipkin server (Docker container)
- [ ] Update all services with trace configuration
- [ ] Add trace IDs to logs

**Deliverables**:

- ✅ Distributed tracing active
- ✅ Trace IDs in all logs

**Testing**:

- [ ] Verify trace propagation across services
- [ ] Test Zipkin UI

---

#### Day 4-5: Prometheus Metrics

**Tasks**:

- [ ] Add Micrometer dependencies
- [ ] Configure Prometheus endpoint
- [ ] Set up Prometheus server
- [ ] Define custom metrics:
  - Transaction latency
  - Circuit breaker states
  - Refund success rate
  - Kafka consumer lag

**Deliverables**:

- ✅ Prometheus metrics exported

**Testing**:

- [ ] Verify metrics collection
- [ ] Test Prometheus queries

---

### Week 10: Dashboards & Alerts

#### Day 1-3: Grafana Dashboards

**Tasks**:

- [ ] Set up Grafana instance
- [ ] Configure Prometheus data source
- [ ] Create dashboards:
  - System Overview Dashboard
  - Transaction Flow Dashboard
  - Kafka Monitoring Dashboard
  - Refund System Dashboard
  - Circuit Breaker Dashboard
- [ ] Add panels for all key metrics

**Deliverables**:

- ✅ Grafana dashboards created
- ✅ Real-time monitoring active

---

#### Day 4-5: Alerting & Documentation

**Tasks**:

- [ ] Configure alert rules in Prometheus:
  - High error rate (> 5%)
  - Circuit breaker open
  - Consumer lag > 10 seconds
  - Failed refunds
- [ ] Set up alert channels (email, Slack)
- [ ] Create runbook for each alert
- [ ] Document monitoring strategy

**Deliverables**:

- ✅ Alerting configured
- ✅ Documentation complete

**Testing**:

- [ ] Trigger test alerts
- [ ] Verify alert delivery

---

### Phase 5 Deployment

**Deployment Steps**:

1. Deploy monitoring infrastructure
2. Deploy updated services with tracing
3. Verify data collection
4. Test alerts
5. Team training on dashboards

---

## Risk Management

### High-Risk Areas

#### 1. Kafka Migration (Phase 3)

**Risk**: Event loss or duplication during topic migration

**Mitigation**:

- Dual publishing to old and new topics (1 week overlap)
- Comprehensive monitoring during migration
- Automated rollback if issues detected
- Retain old topics for 30 days

**Contingency**: Revert to old topics, analyze issues, retry migration

---

#### 2. Database Migrations (Phase 2)

**Risk**: Schema changes cause downtime or data loss

**Mitigation**:

- Test migrations in staging environment
- Use backward-compatible changes only
- Run migrations during off-peak hours
- Full database backup before migration

**Contingency**: Restore from backup, analyze failure, fix migration script

---

#### 3. Performance Regression (Phase 3)

**Risk**: Synchronous HTTP calls slower than expected

**Mitigation**:

- Extensive load testing before production
- Circuit breakers prevent cascading slowdowns
- Monitor latency metrics in real-time
- Gradual rollout (10% → 50% → 100%)

**Contingency**: Revert to Kafka-based flow, optimize sync calls, retry

---

### Medium-Risk Areas

#### 4. Circuit Breaker Configuration (Phase 1)

**Risk**: Incorrect thresholds cause premature failures or slow recovery

**Mitigation**:

- Start with conservative thresholds
- Monitor circuit breaker metrics
- Tune based on production behavior

**Contingency**: Adjust thresholds via configuration, no code changes needed

---

#### 5. Refund Idempotency (Phase 2)

**Risk**: Duplicate refunds charge customers twice

**Mitigation**:

- Extensive idempotency testing
- Database unique constraints
- Transaction-level locking

**Contingency**: Manual refund reversal, fix idempotency bug, compensate customers

---

## Success Criteria

### Phase 1 Success Metrics

- [ ] Circuit breakers activate correctly on service failures
- [ ] Failed transactions reduced by > 50%
- [ ] System recovers from failures automatically
- [ ] Zero data loss with Kafka DLQ

### Phase 2 Success Metrics

- [ ] Manual refund interventions reduced by > 80%
- [ ] Refund processing time < 5 seconds
- [ ] Zero duplicate refunds
- [ ] Automated recovery rate > 95%

### Phase 3 Success Metrics

- [ ] Transaction latency reduced to < 300ms
- [ ] Kafka topics reduced from 5 to 1-2
- [ ] Event deduplication 60% faster
- [ ] Zero event loss during migration

### Phase 4 Success Metrics

- [ ] Saga compensation success rate > 99%
- [ ] Transaction consistency maintained
- [ ] Recovery from stuck sagas < 5 minutes

### Phase 5 Success Metrics

- [ ] All services traced end-to-end
- [ ] Dashboards show real-time metrics
- [ ] Alerts trigger within 1 minute of issues
- [ ] MTTD (Mean Time To Detect) < 2 minutes

---

## Rollback Strategy

### General Rollback Procedure

1. **Identify Issue**: Monitor alerts and dashboards
2. **Assess Severity**: Critical (rollback immediately) vs. Non-critical (fix forward)
3. **Execute Rollback**: Revert to previous deployment
4. **Verify Stability**: Monitor for 1 hour post-rollback
5. **Root Cause Analysis**: Identify what went wrong
6. **Fix & Retry**: Address issue, redeploy

### Phase-Specific Rollback Plans

#### Phase 1: Fault Tolerance

- **Action**: Remove circuit breaker annotations, redeploy
- **Time**: < 15 minutes
- **Impact**: Minimal (no data loss)

#### Phase 2: Refund System

- **Action**: Disable refund API, revert compensation logic
- **Time**: < 30 minutes
- **Impact**: Manual refunds required temporarily

#### Phase 3: Kafka Optimization

- **Action**: Revert to old topics, disable new consumers
- **Time**: < 30 minutes
- **Impact**: Higher latency, but functional

#### Phase 4: Saga Pattern

- **Action**: Disable saga orchestration, use direct refunds
- **Time**: < 20 minutes
- **Impact**: Less sophisticated compensation

#### Phase 5: Monitoring

- **Action**: Disable tracing, remove metrics
- **Time**: < 10 minutes
- **Impact**: Reduced visibility only

---

## Communication Plan

### Weekly Status Updates

**Audience**: Development team, Product Manager, CTO

**Format**: 30-minute meeting + written report

**Content**:

- Progress on current phase
- Blockers and risks
- Key decisions made
- Metrics achieved
- Plan for next week

### Milestone Reviews

**Timing**: End of each phase

**Audience**: All stakeholders

**Content**:

- Phase completion demo
- Success metrics review
- Lessons learned
- Go/No-Go decision for next phase

### Incident Communication

**Critical Issues**: Immediate notification to all stakeholders

**Medium Issues**: Daily update in team channel

**Low Issues**: Documented in weekly report

---

## Budget & Resource Allocation

### Team Composition

**Phase 1-2**: 2 Backend Developers  
**Phase 3**: 3 Backend Developers (high risk)  
**Phase 4**: 2 Backend Developers  
**Phase 5**: 1 Backend Developer + 1 DevOps Engineer

### Infrastructure Costs (Estimated)

- Redis Cluster: $200/month
- Zipkin Server: $100/month
- Prometheus Server: $150/month
- Grafana Instance: $50/month
- Additional Kafka Storage: $100/month

**Total**: ~$600/month ongoing

### Time Investment

| Phase     | Dev Hours | Testing Hours | Total   |
| --------- | --------- | ------------- | ------- |
| Phase 1   | 60        | 20            | 80      |
| Phase 2   | 60        | 20            | 80      |
| Phase 3   | 80        | 20            | 100     |
| Phase 4   | 60        | 20            | 80      |
| Phase 5   | 60        | 20            | 80      |
| **Total** | **320**   | **100**       | **420** |

---

## Next Steps

### Week of November 4, 2025

- [ ] **Monday**: Team kickoff meeting, review implementation plan
- [ ] **Tuesday**: Set up development branches, update dependencies
- [ ] **Wednesday-Friday**: Begin Phase 1, Day 1-3 tasks

### Key Milestones

- **November 15**: Phase 1 complete ✅
- **November 29**: Phase 2 complete ✅
- **December 13**: Phase 3 complete ✅
- **December 27**: Phase 4 complete ✅
- **January 10**: Phase 5 complete ✅
- **January 13**: Final review and documentation ✅

---

## Appendix

### A. Testing Strategy

**Unit Tests**: 80% code coverage minimum  
**Integration Tests**: All critical flows covered  
**Load Tests**: 200 concurrent users, 1000 transactions  
**Chaos Tests**: Random service failures, network issues  
**Performance Tests**: Latency < 300ms, throughput > 100 TPS

### B. Documentation Requirements

- [ ] API documentation (OpenAPI/Swagger)
- [ ] Architecture decision records (ADRs)
- [ ] Runbooks for operations team
- [ ] Developer setup guide
- [ ] Monitoring guide

### C. Training Plan

**Week 8**: Refund system training for support team  
**Week 10**: Monitoring dashboards training for ops team  
**Week 11**: Full system walkthrough for all teams

---

**Document Version**: 1.0  
**Last Updated**: October 31, 2025  
**Next Review**: November 4, 2025  
**Owner**: Development Team Lead  
**Approved By**: _[Pending Approval]_
