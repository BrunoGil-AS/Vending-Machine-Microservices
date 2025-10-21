# Vending Machine Microservices - System Diagrams

## 📂 Quick Navigation

This directory contains comprehensive Mermaid diagrams documenting the complete Vending Machine Microservices architecture.

### 🗺️ Start Here

- **New to the project?** → Read [`../project_flow_guide.md`](../project_flow_guide.md)
- **Need overview?** → See [`system-overview.md`](./system-overview.md)
- **Looking for specific service?** → Check [`../diagram_index.md`](../diagram_index.md)
- **Want delivery status?** → Review [`../DIAGRAM_DELIVERY_SUMMARY.md`](../DIAGRAM_DELIVERY_SUMMARY.md)

---

## 📊 Available Diagrams

### ✅ System-Level (Complete)

#### [System Overview](./system-overview.md)

**8 comprehensive diagrams covering:**

- System Context - All services and infrastructure
- Purchase Flow Sequence - 30+ step transaction
- Kafka Topic Flow - Event streaming architecture
- Deployment - Resource requirements & ports
- Service Interaction Map - Sync vs async patterns
- Authentication Flow - JWT & RBAC
- Error Handling - Failure scenarios
- Performance Monitoring - Observability

**Use when:** You need to understand overall architecture, deployment, or communication patterns.

---

### ✅ Service-Level (Inventory Complete)

#### [Inventory Service](./inventory-service.md) - Port 8081

**9 detailed diagrams:**

- Service Context - External dependencies
- Component Architecture - Internal structure
- Data Flow (Level 0 & 1) - Process flows
- Entity Relationship - Database schema
- Kafka Integration - Topics and events
- Stock Check Sequence - Availability validation
- Product Creation Sequence - Admin operations
- Low Stock Alert Sequence - Threshold monitoring
- API Documentation - Endpoints with examples

**Use when:** Implementing or troubleshooting product/stock management features.

---

### ✅ All Service Diagrams (Complete)

#### [Payment Service](./payment-service.md) - Port 8082

**7 comprehensive diagrams:**

- Service Context - External dependencies
- Component Architecture - Internal structure
- Entity Relationship - PaymentTransaction schema
- Cash Payment Sequence - Cash payment flow
- Card Payment Sequence - Card payment flow
- Event-Driven Payment Initiation - Kafka integration
- Payment Simulation Logic - Configurable success rates

**Use when:** Implementing payment processing or troubleshooting payment failures.

#### [Transaction Service](./transaction-service.md) - Port 8083

**8 comprehensive diagrams:**

- Service Context - SAGA Orchestrator
- Component Architecture - Orchestration layer
- SAGA Orchestration Pattern - State machine
- Entity Relationship - Transaction & TransactionItem
- Kafka Topic Flow - Event coordination
- Successful Purchase Sequence - Happy path
- Payment Failure Compensation - Rollback logic
- Dispensing Failure Compensation - Manual intervention

**Use when:** Understanding the complete purchase orchestration or implementing SAGA patterns.

#### [Dispensing Service](./dispensing-service.md) - Port 8084

**8 comprehensive diagrams:**

- Service Context - Hardware simulation
- Component Architecture - Simulation layer
- Hardware Simulation Logic - Failure scenarios (95% success rate)
- Entity Relationship - DispensingRecord schema
- Kafka Topic Flow - Event publishing
- Successful Dispensing Sequence - Normal operation
- Hardware Fault Flow - Motor/sensor failures
- Product Jam Recovery - Jam handling

**Use when:** Implementing hardware operations or debugging dispensing failures.

#### [Notification Service](./notification-service.md) - Port 8085

**8 comprehensive diagrams:**

- Service Context - Event aggregator
- Component Architecture - Multi-topic listeners
- Event Aggregation Flow - All 5 topics
- Entity Relationship - Notification schema
- Kafka Topic Flow - Consumes all topics
- Critical Alert Processing - Alert classification
- Admin Dashboard Query - Alert retrieval
- Alert Acknowledgment - Admin actions

**Use when:** Implementing alerting system or building admin dashboards.

#### [API Gateway](./api-gateway.md) - Port 8080

**8 comprehensive diagrams:**

- Service Context - Entry point & security
- Component Architecture - Filter chain
- Authentication Flow - JWT login & validation
- Authorization Matrix - Role-based permissions
- Entity Relationship - User schema
- User Registration Sequence - SUPER_ADMIN only
- Failed Authentication Flow - Error handling
- Token Expiration Handling - 8-hour expiry

**Use when:** Implementing authentication, authorization, or service routing.

#### [Infrastructure Services](./infrastructure-services.md)

**8 comprehensive diagrams:**

- Overall Infrastructure Topology - Complete system view
- Config Server Architecture - File-based configuration
- Configuration Hierarchy Flow - Bootstrap process
- Eureka Service Registration - Service discovery
- Eureka Dashboard View - Registered instances
- Kafka Topic Structure - 5 topics with partitions
- MySQL Database-per-Service - 6 databases
- Service Startup Sequence - Dependency chain

**Use when:** Setting up infrastructure, troubleshooting service discovery, or understanding deployment.

---

## 🎯 Common Use Cases

### "I need to understand the purchase flow"

1. Read: [`system-overview.md#purchase-flow-sequence-diagram`](./system-overview.md#purchase-flow-sequence-diagram)
2. Review: Transaction Service docs (when available)
3. Check: Kafka Topic Flow for async updates

### "How do services communicate?"

1. Start: [`system-overview.md#service-interaction-map`](./system-overview.md#service-interaction-map)
2. Details: Individual service Context Diagrams
3. Events: [`system-overview.md#kafka-topic-flow-diagram`](./system-overview.md#kafka-topic-flow-diagram)

### "What's the database structure?"

1. Overview: [`../project_flow_guide.md#service-dependencies`](../project_flow_guide.md#service-dependencies)
2. Details: Each service's ERD diagram
3. Example: [`inventory-service.md#entity-relationship-diagram`](./inventory-service.md#entity-relationship-diagram)

### "How does authentication work?"

1. Flow: [`system-overview.md#authentication-flow-diagram`](./system-overview.md#authentication-flow-diagram)
2. Details: API Gateway docs (when available)
3. Context: [`../Vending Machine Authentication Flow Architecture.md`](../Vending%20Machine%20Authentication%20Flow%20Architecture.md)

### "How do I deploy this system?"

1. Architecture: [`system-overview.md#deployment-diagram`](./system-overview.md#deployment-diagram)
2. Sequence: [`../project_flow_guide.md#system-startup-order`](../project_flow_guide.md#system-startup-order)
3. Scripts: `../scripts/` directory

---

## 📐 Diagram Standards

### Visual Conventions

**Colors:**

- 🟣 Infrastructure (Config, Eureka): Purple
- 🔴 Gateway (API Gateway): Red
- 🟢 Database (MySQL): Green
- 🟡 Messaging (Kafka): Yellow
- 🔵 Business Services: Various pastels

**Line Styles:**

- `━━━` Solid: Synchronous HTTP/REST
- `┈┈┈` Dashed: Asynchronous Kafka events
- `···` Dotted: Configuration/registration

### Naming Patterns

- **Services:** `ServiceName` (PascalCase)
- **Topics:** `domain-events` (kebab-case)
- **Tables:** `table_name` (snake_case)
- **Endpoints:** `/api/{domain}/*`

---

## 🔢 Statistics

| Metric                 | Count                              |
| ---------------------- | ---------------------------------- |
| Total Documents        | 10 (8 services + 2 indices)        |
| Total Diagrams         | 50+                                |
| Lines of Documentation | 7,000+                             |
| Services Documented    | 8/8 (100%)                         |
| System Coverage        | Complete - All services documented |

---

## 📚 Document Hierarchy

```plaintext
Documentation/
├── 📄 project_flow_guide.md           ← Start here for business logic
├── 📄 diagram_index.md                ← Complete index & cross-references
├── 📄 DIAGRAM_DELIVERY_SUMMARY.md     ← What's been delivered
└── diagrams/
    ├── 📄 README.md                   ← You are here
    ├── ✅ system-overview.md          ← Architecture & flows
    ├── ✅ inventory-service.md        ← Product & stock management
    ├── ✅ payment-service.md          ← Payment processing simulation
    ├── ✅ transaction-service.md      ← SAGA orchestration
    ├── ✅ dispensing-service.md       ← Hardware simulation
    ├── ✅ notification-service.md     ← Event aggregation & alerts
    ├── ✅ api-gateway.md              ← Authentication & routing
    └── ✅ infrastructure-services.md  ← Config, Eureka, Kafka, MySQL
```

---

## 🛠️ Tools & Rendering

### Viewing Diagrams

**GitHub:** Diagrams render automatically in `.md` files  
**VS Code:** Install "Markdown Preview Mermaid Support" extension  
**IntelliJ:** Built-in Mermaid support in Markdown files

### Editing Diagrams

**Online:** [Mermaid Live Editor](https://mermaid.live/)  
**VS Code:** "Mermaid Chart" or "Markdown Preview Enhanced" extensions  
**Command Line:** `mmdc` (mermaid-cli)

### Exporting Diagrams

```bash
# Install mermaid-cli
npm install -g @mermaid-js/mermaid-cli

# Export to PNG
mmdc -i diagram.mmd -o diagram.png

# Export to SVG
mmdc -i diagram.mmd -o diagram.svg -b transparent
```

---

## 🔍 Cross-Reference Quick Links

### By Service

- **Inventory Service** → [`inventory-service.md`](./inventory-service.md)
- **Payment Service** → Coming soon
- **Transaction Service** → Coming soon
- **Dispensing Service** → Coming soon
- **Notification Service** → Coming soon
- **API Gateway** → Coming soon
- **Infrastructure** → Coming soon

### By Topic

- **Architecture** → [`system-overview.md`](./system-overview.md)
- **Kafka Events** → [`system-overview.md#kafka-topic-flow`](./system-overview.md#kafka-topic-flow)
- **Authentication** → [`system-overview.md#authentication-flow`](./system-overview.md#authentication-flow)
- **Database Design** → Individual service ERDs
- **API Endpoints** → Individual service docs

### By Diagram Type

- **Context Diagrams** → System overview + each service
- **Sequence Diagrams** → System overview + each service
- **Component Diagrams** → Each service document
- **ERD Diagrams** → Each service document
- **DFD Diagrams** → Each service document

---

## 📖 Related Documentation

### Core Documents

- [📋 SRS](../SRS-Vending-Machine.md) - Complete requirements
- [📅 Development Plan](../development_plan.md) - Implementation timeline
- [🔐 Auth Architecture](../Vending%20Machine%20Authentication%20Flow%20Architecture.md)
- [📨 Messaging Architecture](../Vending%20Machine%20Messaging%20Architecture.md)

### Technical Reports

- [💳 Purchase Flow](../reports/purchase_flow.md) - Transaction details
- [⚡ Race Condition Fix](../reports/race_condition_fix.md) - Concurrency solution

---

## ✅ Quality Checklist

Before using these diagrams:

- ✅ All Mermaid syntax validated
- ✅ Port numbers verified against services
- ✅ Database names match `create-databases.sql`
- ✅ Kafka topics align with service configs
- ✅ API endpoints match controller implementations
- ✅ Event types match common-library classes
- ✅ Visual standards consistently applied

---

## 🚀 Next Steps

### For Developers

1. Read project flow guide for context
2. Review system overview for architecture
3. Study inventory service as implementation reference
4. Check diagram index for cross-references

### For Contributors

1. Follow established diagram patterns
2. Use consistent visual standards
3. Update diagram index when adding content
4. Include sequence diagrams for key flows

---

## 💡 Tips

**Finding Specific Information:**

- 🔍 Use Ctrl+F to search within documents
- 📑 Check diagram index for cross-reference tables
- 🗺️ Start with system overview for high-level view
- 🔬 Drill down to service docs for details

**Understanding Flows:**

- 🎬 Sequence diagrams show step-by-step execution
- 🏗️ Context diagrams show service boundaries
- 📊 DFD diagrams show data transformation
- 🔄 Kafka diagrams show async event flows

---

## 📊 Completion Status

| Service         | Status | Diagrams | Completion |
| --------------- | ------ | -------- | ---------- |
| System Overview | ✅     | 8/8      | 100%       |
| Inventory       | ✅     | 9/9      | 100%       |
| Payment         | ✅     | 7/7      | 100%       |
| Transaction     | ✅     | 8/8      | 100%       |
| Dispensing      | ✅     | 8/8      | 100%       |
| Notification    | ✅     | 8/8      | 100%       |
| Gateway         | ✅     | 8/8      | 100%       |
| Infrastructure  | ✅     | 8/8      | 100%       |

**Overall Progress:** 50+/50+ diagrams (100%) ✅ COMPLETE

---

## 📞 Need Help?

**Can't find what you need?**

1. Check [`diagram_index.md`](../diagram_index.md) - Complete index
2. Review [`project_flow_guide.md`](../project_flow_guide.md) - Business flows
3. See [`DIAGRAM_DELIVERY_SUMMARY.md`](../DIAGRAM_DELIVERY_SUMMARY.md) - What's available

**Want to contribute?**

1. Follow diagram standards from index
2. Use inventory service as template
3. Update index with new content
4. Maintain visual consistency

---

## 🎓 Learning Path

**Beginner:**

1. Project Flow Guide → Business understanding
2. System Context Diagram → High-level architecture
3. Purchase Flow Sequence → Transaction understanding

**Intermediate:**

1. Service Context Diagrams → Service boundaries
2. Component Diagrams → Internal structure
3. API Documentation → Integration points

**Advanced:**

1. DFD Diagrams → Process flows
2. Kafka Topic Flow → Event-driven patterns
3. ERD Diagrams → Data model design

---

## ⭐ Quick Reference Card

```plaintext
📋 Business Logic     → project_flow_guide.md
🏗️ Architecture       → system-overview.md
🔍 Find Anything      → diagram_index.md
📦 What's Delivered   → DIAGRAM_DELIVERY_SUMMARY.md
🎯 Service Details    → individual service .md files

Ports:
  8888 - Config Server
  8761 - Eureka Server
  8080 - API Gateway
  8081 - Inventory Service
  8082 - Payment Service
  8083 - Transaction Service
  8084 - Dispensing Service
  8085 - Notification Service
  3306 - MySQL
  9092 - Kafka
  2181 - Zookeeper

Kafka Topics:
  transaction-events
  payment-events
  inventory-events
  dispensing-events
  notification-events
```

---

**Last Updated:** October 21, 2025  
**Version:** 2.0  
**Status:** Phase 2 Complete - All Services Documented ✅  
**Author:** GitHub Copilot

---

_For questions, clarifications, or contributions, refer to the diagram index or project documentation._
