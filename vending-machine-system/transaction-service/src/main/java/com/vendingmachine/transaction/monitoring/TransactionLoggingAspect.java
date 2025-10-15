package com.vendingmachine.transaction.monitoring;

import com.vendingmachine.common.monitoring.BusinessMetricsCollector;
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
public class TransactionLoggingAspect {

    private static final Logger logger = LoggerFactory.getLogger(TransactionLoggingAspect.class);

    private final CorrelationIdManager correlationIdManager;
    private final BusinessMetricsCollector metricsCollector;

    public TransactionLoggingAspect(CorrelationIdManager correlationIdManager,
                                   BusinessMetricsCollector metricsCollector) {
        this.correlationIdManager = correlationIdManager;
        this.metricsCollector = metricsCollector;
    }

    @Pointcut("execution(* com.vendingmachine.transaction.transaction.TransactionService.*(..))")
    public void transactionServiceMethods() {}

    @Around("transactionServiceMethods()")
    public Object logTransactionOperations(ProceedingJoinPoint joinPoint) throws Throwable {
        String correlationId = correlationIdManager.getCorrelationId();
        String methodName = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();

        logger.info("Transaction operation started - Method: {}, CorrelationId: {}, Args: {}",
                   methodName, correlationId, args.length);

        long startTime = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();

            long duration = System.currentTimeMillis() - startTime;
            logger.info("Transaction operation completed - Method: {}, CorrelationId: {}, Duration: {}ms",
                       methodName, correlationId, duration);

            // Record success metrics for key operations
            if ("processTransaction".equals(methodName)) {
                metricsCollector.recordTransactionSuccess();
            }

            return result;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logger.error("Transaction operation failed - Method: {}, CorrelationId: {}, Duration: {}ms, Error: {}",
                        methodName, correlationId, duration, e.getMessage(), e);

            // Record failure metrics for key operations
            if ("processTransaction".equals(methodName)) {
                metricsCollector.recordTransactionFailure();
            }

            throw e;
        }
    }
}