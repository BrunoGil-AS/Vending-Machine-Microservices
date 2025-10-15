package com.vendingmachine.dispensing.monitoring;

import com.vendingmachine.common.monitoring.BusinessMetricsCollector;
import com.vendingmachine.common.monitoring.CorrelationIdManager;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class DispensingLoggingAspect {

    private static final Logger logger = LoggerFactory.getLogger(DispensingLoggingAspect.class);

    private final CorrelationIdManager correlationIdManager;
    private final BusinessMetricsCollector metricsCollector;

    public DispensingLoggingAspect(CorrelationIdManager correlationIdManager,
                                  BusinessMetricsCollector metricsCollector) {
        this.correlationIdManager = correlationIdManager;
        this.metricsCollector = metricsCollector;
    }

    @Pointcut("execution(* com.vendingmachine.dispensing.dispensing.DispensingService.*(..))")
    public void dispensingServiceMethods() {}

    @Around("dispensingServiceMethods()")
    public Object logDispensingOperations(ProceedingJoinPoint joinPoint) throws Throwable {
        String correlationId = correlationIdManager.getCorrelationId();
        String methodName = joinPoint.getSignature().getName();

        logger.info("Dispensing operation started - Method: {}, CorrelationId: {}",
                   methodName, correlationId);

        Timer.Sample timerSample = null;
        if ("dispenseProduct".equals(methodName)) {
            timerSample = metricsCollector.startDispensingTimer();
        }

        try {
            Object result = joinPoint.proceed();

            if (timerSample != null) {
                metricsCollector.stopDispensingTimer(timerSample);
                metricsCollector.recordDispensingSuccess();
            }

            logger.info("Dispensing operation completed - Method: {}, CorrelationId: {}",
                       methodName, correlationId);

            return result;

        } catch (Exception e) {
            if (timerSample != null) {
                metricsCollector.stopDispensingTimer(timerSample);
                metricsCollector.recordDispensingFailure();
            }

            logger.error("Dispensing operation failed - Method: {}, CorrelationId: {}, Error: {}",
                        methodName, correlationId, e.getMessage(), e);

            throw e;
        }
    }
}