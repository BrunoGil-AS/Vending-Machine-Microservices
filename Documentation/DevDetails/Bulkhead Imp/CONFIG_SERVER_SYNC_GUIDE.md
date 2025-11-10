# Config Server Synchronization Guide - Bulkhead Configuration

## 📋 Resumen de Sincronización

Se ha completado la **sincronización completa** de las configuraciones de Bulkhead entre:

- ✅ **Config Server** (`config-server/src/main/resources/config/`)
- ✅ **Application Properties locales** (`{service}/src/main/resources/application.properties`)

## 🔄 Estrategia de Configuración Híbrida

### **Prioridad de Configuración (Order de Precedencia)**

```
1. Config Server (CENTRALIZADO) ← PRINCIPAL
2. Application Properties (BACKUP/LOCAL)
3. Environment Variables (OVERRIDE)
4. Command Line Arguments (HIGHEST)
```

### **Configuración Spring Cloud Config**

```properties
# En cada service application.properties
spring.config.import=optional:configserver:http://localhost:8888

# El "optional:" permite fallback a configuración local si Config Server no está disponible
```

---

## 🏗️ Servicios Sincronizados

### **1. Transaction Service** 🔄

#### **Config Server** (`transaction-service.properties`)

```properties
# Resilience4j Bulkhead Configuration (Semaphore-based)
resilience4j.bulkhead.instances.payment-service.maxConcurrentCalls=20
resilience4j.bulkhead.instances.payment-service.maxWaitDuration=100ms

resilience4j.bulkhead.instances.dispensing-service.maxConcurrentCalls=10
resilience4j.bulkhead.instances.dispensing-service.maxWaitDuration=100ms

resilience4j.bulkhead.instances.inventory-service.maxConcurrentCalls=15
resilience4j.bulkhead.instances.inventory-service.maxWaitDuration=50ms

# Thread Pool Bulkhead
resilience4j.thread-pool-bulkhead.instances.kafka-processing.maxThreadPoolSize=8
resilience4j.thread-pool-bulkhead.instances.background-tasks.maxThreadPoolSize=5
```

#### **Local Backup** (`application.properties`)

```properties
# Configuración idéntica para fallback
resilience4j.bulkhead.instances.payment-service.maxConcurrentCalls=20
# ... (misma configuración)
```

### **2. Payment Service** 💳

#### **Config Server** (`payment-service.properties`)

```properties
# Payment-specific Bulkhead Configuration
resilience4j.bulkhead.instances.external-payment-provider.maxConcurrentCalls=25
resilience4j.bulkhead.instances.external-payment-provider.maxWaitDuration=200ms

resilience4j.bulkhead.instances.database-operations.maxConcurrentCalls=30
resilience4j.bulkhead.instances.database-operations.maxWaitDuration=50ms

# Thread Pool for async payment processing
resilience4j.thread-pool-bulkhead.instances.payment-processing.maxThreadPoolSize=10
resilience4j.thread-pool-bulkhead.instances.kafka-processing.maxThreadPoolSize=6
```

### **3. Dispensing Service** 🏭

#### **Config Server** (`dispensing-service.properties`)

```properties
# Hardware-specific Bulkhead (Critical Resource)
resilience4j.bulkhead.instances.hardware-operations.maxConcurrentCalls=5
resilience4j.bulkhead.instances.hardware-operations.maxWaitDuration=500ms

resilience4j.bulkhead.instances.database-operations.maxConcurrentCalls=15
resilience4j.bulkhead.instances.database-operations.maxWaitDuration=100ms

# Thread Pool for dispensing operations
resilience4j.thread-pool-bulkhead.instances.dispensing-operations.maxThreadPoolSize=6
```

### **4. Inventory Service** 📦

#### **Config Server** (`inventory-service.properties`)

```properties
# Inventory-specific Bulkhead
resilience4j.bulkhead.instances.inventory-checks.maxConcurrentCalls=20
resilience4j.bulkhead.instances.inventory-checks.maxWaitDuration=100ms

resilience4j.bulkhead.instances.stock-updates.maxConcurrentCalls=15
resilience4j.bulkhead.instances.stock-updates.maxWaitDuration=200ms

resilience4j.bulkhead.instances.external-suppliers.maxConcurrentCalls=5
resilience4j.bulkhead.instances.external-suppliers.maxWaitDuration=300ms

# Thread Pool for inventory processing
resilience4j.thread-pool-bulkhead.instances.inventory-processing.maxThreadPoolSize=8
```

### **5. Notification Service** 📧

#### **Config Server** (`notification-service.properties`)

```properties
# Notification-specific Bulkhead
resilience4j.bulkhead.instances.email-notifications.maxConcurrentCalls=8
resilience4j.bulkhead.instances.email-notifications.maxWaitDuration=300ms

resilience4j.bulkhead.instances.sms-notifications.maxConcurrentCalls=5
resilience4j.bulkhead.instances.sms-notifications.maxWaitDuration=200ms

resilience4j.bulkhead.instances.database-operations.maxConcurrentCalls=12
resilience4j.bulkhead.instances.database-operations.maxWaitDuration=100ms

# Thread Pool for notification processing
resilience4j.thread-pool-bulkhead.instances.notification-processing.maxThreadPoolSize=6
```

---

## 🎯 Ventajas de la Sincronización

### **1. Alta Disponibilidad** 🚀

- **Config Server DOWN**: Los servicios usan configuración local (fallback automático)
- **Network Issues**: No afecta el startup de servicios
- **Emergency Changes**: Se pueden hacer localmente si Config Server no está disponible

### **2. Gestión Centralizada** 🎛️

```bash
# Cambios centralizados en Config Server
vim config-server/src/main/resources/config/payment-service.properties

# Los servicios obtienen la nueva configuración:
# 1. Al reiniciar (automático)
# 2. Via /actuator/refresh endpoint (manual)
# 3. Via Spring Cloud Bus (automático con RabbitMQ/Kafka)
```

### **3. Consistency Across Environments** 🌍

```
Development → Config Server (local files)
Staging    → Config Server (Git repository)
Production → Config Server (Git repository + encryption)
```

### **4. Auditability** 📝

- **Git History**: Todos los cambios de configuración tracked
- **Rollback**: Fácil reversión de configuraciones problemáticas
- **Approval Process**: Pull requests para cambios de configuración

---

## 🛠️ Administración de Configuración

### **Refresh Configuration (Hot Reload)**

#### **Manual Refresh per Service**

```bash
# Trigger configuration refresh
curl -X POST http://localhost:8083/actuator/refresh
curl -X POST http://localhost:8082/actuator/refresh
curl -X POST http://localhost:8081/actuator/refresh
curl -X POST http://localhost:8084/actuator/refresh
curl -X POST http://localhost:8085/actuator/refresh
```

#### **Automated Refresh Script**

```bash
#!/bin/bash
# refresh-all-configs.sh

SERVICES=("8081" "8082" "8083" "8084" "8085")

echo "🔄 Refreshing configuration for all services..."

for port in "${SERVICES[@]}"; do
    echo "Refreshing service on port $port..."
    curl -X POST "http://localhost:$port/actuator/refresh" -H "Content-Type: application/json"
    echo "✅ Service $port refreshed"
done

echo "🎉 All services configuration refreshed!"
```

### **Configuration Validation**

#### **Check Current Configuration**

```bash
# Check effective configuration per service
curl http://localhost:8083/actuator/configprops | jq '.resilience4j'
curl http://localhost:8082/actuator/env | jq '.propertySources[] | select(.name | contains("configserver"))'
```

#### **Health Check including Config Server**

```bash
# Verify Config Server connectivity
curl http://localhost:8083/actuator/health | jq '.components.configServer'
```

---

## 🔧 Troubleshooting Guide

### **Common Issues & Solutions**

#### **1. Config Server Not Available**

```
❌ Problem: Service fails to start - Cannot connect to config server
✅ Solution: Services have fallback to local application.properties
```

#### **2. Configuration Not Updated**

```
❌ Problem: Changed config in Config Server but service uses old values
✅ Solution:
   - Restart the service, OR
   - Call /actuator/refresh endpoint
```

#### **3. Conflicting Configuration**

```
❌ Problem: Different values between Config Server and local properties
✅ Solution: Config Server takes precedence - verify config-server values
```

#### **4. Missing Bulkhead Configuration**

```
❌ Problem: BulkheadFullException but no bulkhead configured
✅ Solution: Check both Config Server and local application.properties files
```

### **Debug Configuration Source**

```bash
# Check which configuration source is being used
curl http://localhost:8083/actuator/env/resilience4j.bulkhead.instances.payment-service.maxConcurrentCalls

# Expected response shows property source:
{
  "property": {
    "source": "configserver:http://localhost:8888/transaction-service.properties",
    "value": "20"
  }
}
```

---

## 📊 Configuration Matrix

### **Complete Bulkhead Configuration Overview**

| Service          | Operation               | Bulkhead Type | Limit | Wait Time | Location         |
| ---------------- | ----------------------- | ------------- | ----- | --------- | ---------------- |
| **Transaction**  | Payment Calls           | Semaphore     | 20    | 100ms     | Config Server ✅ |
| **Transaction**  | Dispensing Calls        | Semaphore     | 10    | 100ms     | Config Server ✅ |
| **Transaction**  | Inventory Calls         | Semaphore     | 15    | 50ms      | Config Server ✅ |
| **Transaction**  | Kafka Processing        | Thread Pool   | 8     | N/A       | Config Server ✅ |
| **Payment**      | External Provider       | Semaphore     | 25    | 200ms     | Config Server ✅ |
| **Payment**      | Database Ops            | Semaphore     | 30    | 50ms      | Config Server ✅ |
| **Payment**      | Payment Processing      | Thread Pool   | 10    | N/A       | Config Server ✅ |
| **Dispensing**   | Hardware Ops            | Semaphore     | 5     | 500ms     | Config Server ✅ |
| **Dispensing**   | Database Ops            | Semaphore     | 15    | 100ms     | Config Server ✅ |
| **Dispensing**   | Dispensing Ops          | Thread Pool   | 6     | N/A       | Config Server ✅ |
| **Inventory**    | Inventory Checks        | Semaphore     | 20    | 100ms     | Config Server ✅ |
| **Inventory**    | Stock Updates           | Semaphore     | 15    | 200ms     | Config Server ✅ |
| **Inventory**    | External Suppliers      | Semaphore     | 5     | 300ms     | Config Server ✅ |
| **Inventory**    | Inventory Processing    | Thread Pool   | 8     | N/A       | Config Server ✅ |
| **Notification** | Email Notifications     | Semaphore     | 8     | 300ms     | Config Server ✅ |
| **Notification** | SMS Notifications       | Semaphore     | 5     | 200ms     | Config Server ✅ |
| **Notification** | Database Ops            | Semaphore     | 12    | 100ms     | Config Server ✅ |
| **Notification** | Notification Processing | Thread Pool   | 6     | N/A       | Config Server ✅ |

---

## 🚀 Next Steps

### **Immediate Actions**

1. **Test Config Server connectivity**: `curl http://localhost:8888/actuator/health`
2. **Restart all services** to pick up new configurations
3. **Verify bulkhead metrics**: Check `/actuator/metrics/resilience4j.bulkhead.calls`

### **Monitoring Setup**

1. **Prometheus metrics** for bulkhead utilization
2. **Grafana dashboards** for visual monitoring
3. **Alerts** when bulkhead utilization > 80%

### **Future Enhancements**

1. **Git-backed Config Server** for better change tracking
2. **Encrypted properties** for sensitive configuration
3. **Spring Cloud Bus** for automatic configuration refresh
4. **Configuration profiles** per environment (dev/staging/prod)

---

## ✅ Verification Checklist

- [x] **Config Server files updated** with Bulkhead configuration
- [x] **Local application.properties** synchronized as backup
- [x] **All 5 services configured** with appropriate bulkhead limits
- [x] **Fallback strategy implemented** (optional:configserver)
- [x] **Documentation updated** with configuration matrix
- [x] **Troubleshooting guide** provided
- [ ] **Services restarted** to pick up new configuration (NEXT STEP)
- [ ] **End-to-end testing** of bulkhead functionality (NEXT STEP)

---

## 🎉 Conclusion

La **sincronización completa** entre Config Server y archivos locales garantiza:

✅ **Configuración centralizada** - Cambios en un solo lugar  
✅ **Alta disponibilidad** - Fallback automático si Config Server falla  
✅ **Consistency** - Misma configuración en todos los entornos  
✅ **Mantenibilidad** - Gestión simplificada de configuración  
✅ **Observabilidad** - Endpoints para monitorear configuración efectiva

El sistema está ahora completamente preparado para **gestión de configuración empresarial** con **resiliencia de nivel producción**! 🚀
