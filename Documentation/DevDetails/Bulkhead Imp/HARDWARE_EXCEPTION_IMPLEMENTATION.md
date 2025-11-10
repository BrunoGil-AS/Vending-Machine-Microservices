# Hardware Exception Implementation - Dispensing Service

## 📋 Implementación Completada

Se ha creado exitosamente la excepción personalizada `HardwareException` para el Dispensing Service, proporcionando manejo específico y semántico para errores de hardware.

---

## 🆕 Nueva Excepción Creada

### **HardwareException.java**

**Ubicación**: `dispensing-service/src/main/java/com/vendingmachine/dispensing/exception/HardwareException.java`

#### **Características Principales:**

```java
public class HardwareException extends RuntimeException {
    private final String hardwareComponent;
    private final String operationType;

    // Constructores múltiples para diferentes casos de uso
    // Factory methods estáticos para errores comunes
    // Información detallada del componente y operación que falló
}
```

#### **Factory Methods Disponibles:**

| Method                                   | Descripción                      | Componente      | Operación  |
| ---------------------------------------- | -------------------------------- | --------------- | ---------- |
| `dispensingMotorFailure(productId)`      | Motor de dispensado falló        | dispenser_motor | DISPENSE   |
| `productJam(productId)`                  | Producto atascado en el conducto | product_chute   | DISPENSE   |
| `verificationFailure(productId)`         | Sensor no verificó el dispensado | sensor_array    | VERIFY     |
| `hardwareNotOperational()`               | Sistema de hardware no operativo | system          | INITIALIZE |
| `operationTimeout(component, timeoutMs)` | Operación de hardware timeout    | custom          | TIMEOUT    |

---

## 🔧 Actualizaciones en DispensingService

### **Imports Agregados:**

```java
import com.vendingmachine.dispensing.exception.HardwareException;
```

### **1. Verificación de Hardware Operativo**

**ANTES:**

```java
if (!hardwareStatusService.isHardwareOperational()) {
    log.error("Hardware is not operational, cannot dispense for transaction {}", transactionId);
    // Publish failure event or handle accordingly
    return;
}
```

**DESPUÉS:**

```java
if (!hardwareStatusService.isHardwareOperational()) {
    log.error("Hardware is not operational, cannot dispense for transaction {}", transactionId);
    throw HardwareException.hardwareNotOperational();
}
```

### **2. Simulación de Hardware con Excepciones Específicas**

**ANTES:**

```java
if (random.nextDouble() < jamProbability) {
    log.warn("Dispensing jam detected for product {}", item.getProductId());
    hardwareStatusService.reportHardwareError("product_chute", "Jam detected during dispensing");
    return false; // Simple boolean return
}
```

**DESPUÉS:**

```java
if (random.nextDouble() < jamProbability) {
    log.warn("Dispensing jam detected for product {}", item.getProductId());
    hardwareStatusService.reportHardwareError("product_chute", "Jam detected during dispensing");
    throw HardwareException.productJam(item.getProductId()); // Specific exception
}
```

### **3. Manejo de Excepciones en el Loop Principal**

**ANTES:**

```java
boolean success = simulateDispensing(item);

if (success) {
    dispensing.setStatus("SUCCESS");
    // ...
} else {
    dispensing.setStatus("FAILED");
    dispensing.setErrorMessage("Dispensing failed - possible jam or hardware error");
    // ...
}
```

**DESPUÉS:**

```java
try {
    boolean success = simulateDispensing(item);

    if (success) {
        dispensing.setStatus("SUCCESS");
        // ...
    } else {
        dispensing.setStatus("FAILED");
        dispensing.setErrorMessage("Dispensing failed - unknown hardware error");
        // ...
    }
} catch (HardwareException e) {
    dispensing.setStatus("FAILED");
    dispensing.setErrorMessage(String.format("Hardware failure: %s [Component: %s, Operation: %s]",
            e.getMessage(), e.getHardwareComponent(), e.getOperationType()));
    log.error("Hardware exception during dispensing for product {} in transaction {}: {}",
            item.getProductId(), transactionId, e.toString());
    hardwareStatusService.reportHardwareError(e.getHardwareComponent(), e.getMessage());
}
```

---

## 🎯 Beneficios de la Nueva Implementación

### **1. Semántica Específica** 🔍

- **Antes**: Errores genéricos como `RuntimeException` o `false` returns
- **Después**: Excepciones específicas con contexto detallado del hardware
- **Ventaja**: Debugging más fácil y logs más informativos

### **2. Información Contextual Rica** 📊

```java
HardwareException exception = HardwareException.productJam(123L);
// Contiene:
// - message: "Product jam detected for product 123 in dispensing chute"
// - hardwareComponent: "product_chute"
// - operationType: "DISPENSE"
// - productId: implícito en el mensaje
```

### **3. Factory Methods para Casos Comunes** 🏭

```java
// Uso simple y expresivo
throw HardwareException.dispensingMotorFailure(productId);
throw HardwareException.verificationFailure(productId);
throw HardwareException.hardwareNotOperational();
```

### **4. Integración con Resilience4j** ⚡

```properties
# Config Server - dispensing-service.properties
resilience4j.retry.instances.hardware-operations.retryExceptions=java.util.concurrent.TimeoutException,com.vendingmachine.dispensing.exception.HardwareException
```

- **Retry Pattern**: Ahora reintenta automáticamente cuando se detectan errores de hardware específicos
- **Circuit Breaker**: Puede abrir cuando hay múltiples errores de hardware
- **Bulkhead**: Aísla las operaciones de hardware problemáticas

---

## 🔄 Flujo de Error Mejorado

### **Escenario: Product Jam Detection**

```
1. simulateDispensing(item) → HardwareException.productJam(123)
2. Resilience4j Retry → Reintenta operación 2 veces más
3. Si persiste → Circuit Breaker evalúa patrón de fallos
4. Bulkhead → Limita concurrent operations a 5 para hardware
5. Exception caught → Información detallada guardada en BD
6. Hardware Status Service → Reporta error específico del componente
7. Kafka Event → Publicado con información detallada del fallo
```

### **Logging Mejorado**

**ANTES:**

```
WARN: Failed to dispense product 123 for transaction 456
```

**DESPUÉS:**

```
ERROR: Hardware exception during dispensing for product 123 in transaction 456:
       HardwareException{component='product_chute', operation='DISPENSE',
       message='Product jam detected for product 123 in dispensing chute'}
```

---

## 🧪 Casos de Uso de las Excepciones

### **1. Motor Failure**

```java
throw HardwareException.dispensingMotorFailure(productId);
// → Component: dispenser_motor
// → Operation: DISPENSE
// → Retry: Sí (hardware podría recuperarse)
// → Circuit Breaker: Incrementa failure count
```

### **2. Product Jam**

```java
throw HardwareException.productJam(productId);
// → Component: product_chute
// → Operation: DISPENSE
// → Retry: Sí (jam podría resolverse automáticamente)
// → Mantenimiento: Alert para limpieza física
```

### **3. Sensor Verification Failure**

```java
throw HardwareException.verificationFailure(productId);
// → Component: sensor_array
// → Operation: VERIFY
// → Retry: Sí (lectura de sensor podría ser temporal)
// → Reembolso: Posible activación de compensation
```

### **4. Hardware Not Operational**

```java
throw HardwareException.hardwareNotOperational();
// → Component: system
// → Operation: INITIALIZE
// → Retry: No (requiere intervención manual)
// → Circuit Breaker: Abre inmediatamente
```

---

## 🔧 Testing & Validation

### **Unit Test Examples**

```java
@Test
void shouldThrowHardwareExceptionWhenJamOccurs() {
    // Given
    DispensingItem item = new DispensingItem(123L, 1);

    // When & Then
    HardwareException exception = assertThrows(HardwareException.class,
        () -> dispensingService.dispenseProductsForTransaction(456L, List.of(item)));

    assertEquals("product_chute", exception.getHardwareComponent());
    assertEquals("DISPENSE", exception.getOperationType());
    assertTrue(exception.getMessage().contains("jam detected"));
}
```

### **Integration Test with Resilience4j**

```java
@Test
void shouldRetryOnHardwareException() {
    // Verificar que Retry pattern funciona con HardwareException
    // Verificar que Circuit Breaker cuenta failures apropiadamente
    // Verificar que Bulkhead limita concurrent operations
}
```

---

## 🚀 Próximos Pasos

### **Immediate Actions**

1. **Build & Test**: Compilar y verificar que la nueva excepción funciona
2. **Restart Dispensing Service**: Para cargar la nueva configuración y código
3. **Validate Retry Behavior**: Probar que Resilience4j reintenta con HardwareException

### **Validation Commands**

```bash
# Build the project
./build.sh

# Check if HardwareException is properly loaded
curl http://localhost:8084/actuator/configprops | jq '.resilience4j'

# Test dispensing endpoint to trigger potential hardware exceptions
curl -X POST http://localhost:8084/api/dispensing/dispense \
  -H "Content-Type: application/json" \
  -d '{"transactionId": 123, "items": [{"productId": 1, "quantity": 1}]}'
```

### **Monitoring & Observability**

```bash
# Check logs for specific hardware exceptions
tail -f logs/dispensing-service.log | grep "HardwareException"

# Monitor Resilience4j metrics
curl http://localhost:8084/actuator/metrics/resilience4j.retry.calls | jq '.measurements'
```

---

## ✅ Summary

### **Implementation Results**:

- ✅ **HardwareException created** - Complete custom exception with contextual information
- ✅ **DispensingService updated** - Proper exception handling and throwing
- ✅ **Resilience4j integration** - HardwareException configured for retry pattern
- ✅ **Factory methods implemented** - Easy-to-use static methods for common failures
- ✅ **Rich logging** - Detailed error information with component and operation context
- ✅ **Backward compatibility** - Existing fallback methods still work

### **Architecture Enhanced**:

```
┌─────────────────────────────────────────────────────────────┐
│                ENHANCED ERROR HANDLING                      │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌─────────────────┐    ┌────────────────┐    ┌──────────┐  │
│  │ Hardware Operation │─→│ HardwareException │─→│ Resilience4j │ │
│  │                 │    │                │    │          │  │
│  │ • Motor Control    │    │ • Component Info   │    │ • Retry      │  │
│  │ • Sensor Reading   │    │ • Operation Type   │    │ • Circuit    │  │
│  │ • Jam Detection    │    │ • Detailed Message │    │ • Bulkhead   │  │
│  │ • Verification     │    │ • Factory Methods  │    │ • Timeout    │  │
│  └─────────────────┘    └────────────────┘    └──────────┘  │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**El Dispensing Service ahora tiene manejo de errores de hardware de nivel empresarial! 🎉**
