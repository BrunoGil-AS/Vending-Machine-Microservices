# 🚀 Kafka Infrastructure - Quick Guide for Vending Machine

## ✅ Current Status

- **Kafka**: ✅ Running at `localhost:9092`
- **Zookeeper**: ✅ Running at `localhost:2181`
- **Kafka UI**: ✅ Available at <http://localhost:9090>
- **Topics**: ✅ `transaction-events`, `payment-events`, `inventory-events`, `dispensing-events`, `notification-events`

## 🎯 Essential Commands

### Kafka Management

```powershell
# Start Kafka
.\scripts\kafka-manager.ps1 start

# Stop Kafka
.\scripts\kafka-manager.ps1 stop

# Restart Kafka
.\scripts\kafka-manager.ps1 restart

# Check status
.\scripts\kafka-manager.ps1 status

# View logs
.\scripts\kafka-manager.ps1 logs

# List topics
.\scripts\kafka-manager.ps1 topics
```

### Direct Docker Commands

```powershell
# List topics
docker exec vending-kafka kafka-topics --bootstrap-server localhost:9092 --list

# Describe a topic
docker exec vending-kafka kafka-topics --bootstrap-server localhost:9092 --describe --topic transaction-events

# Produce test messages
docker exec -it vending-kafka kafka-console-producer --bootstrap-server localhost:9092 --topic transaction-events

# Consume messages
docker exec -it vending-kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic transaction-events --from-beginning
```

## 🔄 Event Flow in Vending Machine Microservices

### 1. Transaction Service → Other Services

- **Topic**: `transaction-events`
- **Events**: TRANSACTION_STARTED, TRANSACTION_COMPLETED, TRANSACTION_FAILED
- **Producer**: Transaction Service
- **Consumers**: Payment, Inventory, Dispensing Services

### 2. Payment Service

- **Topic**: `payment-events`
- **Events**: PAYMENT_PROCESSING, PAYMENT_SUCCESSFUL, PAYMENT_FAILED
- **Producer**: Payment Service
- **Consumers**: Transaction, Notification Services

### 3. Inventory Service

- **Topic**: `inventory-events`
- **Events**: STOCK_UPDATED, STOCK_LOW, OUT_OF_STOCK
- **Producer**: Inventory Service
- **Consumers**: Transaction, Notification Services

### 4. Dispensing Service

- **Topic**: `dispensing-events`
- **Events**: DISPENSING_STARTED, DISPENSING_COMPLETED, DISPENSING_FAILED
- **Producer**: Dispensing Service
- **Consumers**: Transaction, Notification Services

### 5. Notification Service

- **Topic**: `notification-events`
- **Events**: USER_NOTIFICATION, ADMIN_ALERT, SYSTEM_STATUS
- **Producer**: Notification Service

## 🛠️ Microservice Configuration

All microservices must be configured to connect to:

```properties
spring.kafka.bootstrap-servers=localhost:9092
```

### Consumer Groups

- **Payment Service**: `payment-group`
- **Inventory Service**: `inventory-group`
- **Dispensing Service**: `dispensing-group`
- **Notification Service**: `notification-group`

## 🔧 Vending Machine Service Startup Sequence

1. **Kafka Infrastructure** ✅ (This directory)
2. **Config Server** (port 8888)
3. **Eureka Server** (port 8761)
4. **API Gateway** (port 8080)
5. **Inventory Service** (port 8081)
6. **Payment Service** (port 8082)
7. **Transaction Service** (port 8083)
8. **Dispensing Service** (port 8084)
9. **Notification Service** (port 8085)

## 📊 Monitoring

### Kafka UI (Recommended)

- **URL**: <http://localhost:9090>
- **Features**:
  - View topics and messages
  - Monitor consumers
  - Analyze throughput
  - Manage configurations

### Microservice Logs

Kafka events are logged with the prefix `"KAFKA:"` in each microservice.

## ⚠️ Important Notes

- Kafka **must** be running **before** starting the microservices
- Topics are created automatically if they do not exist
- To stop everything: `.\scripts\kafka-manager.ps1 stop`
- To check status: `.\scripts\kafka-manager.ps1 status`
- Data is persisted using Docker volumes

## ✅ Done

Your Kafka infrastructure is up and running and the topics are created.
You can now start your Vending Machine microservices and observe the real-time communication events.

## 🔗 Useful Links

- [Detailed Configuration](SETUP.md)
- [Troubleshooting](TROUBLESHOOTING.md)
- [Microservice Integration](INTEGRATION.md)
