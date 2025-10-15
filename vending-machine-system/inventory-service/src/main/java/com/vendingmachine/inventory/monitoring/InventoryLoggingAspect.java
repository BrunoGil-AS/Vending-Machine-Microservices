package com.vendingmachine.inventory.monitoring;

import com.vendingmachine.common.monitoring.CorrelationIdManager;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class InventoryLoggingAspect {

    private static final Logger logger = LoggerFactory.getLogger(InventoryLoggingAspect.class);

    private final CorrelationIdManager correlationIdManager;

    public InventoryLoggingAspect(CorrelationIdManager correlationIdManager) {
        this.correlationIdManager = correlationIdManager;
    }

    @Pointcut("execution(* com.vendingmachine.inventory.stock.StockService.*(..)) || " +
              "execution(* com.vendingmachine.inventory.product.ProductService.*(..))")
    public void inventoryServiceMethods() {}

    @Around("inventoryServiceMethods()")
    public Object logInventoryOperations(ProceedingJoinPoint joinPoint) throws Throwable {
        String correlationId = correlationIdManager.getCorrelationId();
        String methodName = joinPoint.getSignature().getName();

        logger.info("Inventory operation started - Method: {}, CorrelationId: {}",
                   methodName, correlationId);

        try {
            Object result = joinPoint.proceed();

            logger.info("Inventory operation completed - Method: {}, CorrelationId: {}",
                       methodName, correlationId);

            // Monitor stock levels for relevant operations
            if ("updateStock".equals(methodName) || "reduceStock".equals(methodName)) {
                // Could add stock level monitoring here
                logger.debug("Stock level updated via method: {}", methodName);
            }

            return result;

        } catch (Exception e) {
            logger.error("Inventory operation failed - Method: {}, CorrelationId: {}, Error: {}",
                        methodName, correlationId, e.getMessage(), e);

            throw e;
        }
    }
}