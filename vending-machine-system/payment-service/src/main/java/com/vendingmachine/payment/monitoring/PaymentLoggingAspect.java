package com.vendingmachine.payment.monitoring;

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
public class PaymentLoggingAspect {

    private static final Logger logger = LoggerFactory.getLogger(PaymentLoggingAspect.class);

    private final CorrelationIdManager correlationIdManager;
    private final BusinessMetricsCollector metricsCollector;

    public PaymentLoggingAspect(CorrelationIdManager correlationIdManager,
                               BusinessMetricsCollector metricsCollector) {
        this.correlationIdManager = correlationIdManager;
        this.metricsCollector = metricsCollector;
    }

    @Pointcut("execution(* com.vendingmachine.payment.payment.PaymentService.*(..))")
    public void paymentServiceMethods() {}

    @Around("paymentServiceMethods()")
    public Object logPaymentOperations(ProceedingJoinPoint joinPoint) throws Throwable {
        String correlationId = correlationIdManager.getCorrelationId();
        String methodName = joinPoint.getSignature().getName();

        logger.info("Payment operation started - Method: {}, CorrelationId: {}",
                   methodName, correlationId);

        Timer.Sample timerSample = null;
        if ("processPayment".equals(methodName)) {
            timerSample = metricsCollector.startPaymentTimer();
        }

        try {
            Object result = joinPoint.proceed();

            if (timerSample != null) {
                metricsCollector.stopPaymentTimer(timerSample);
                metricsCollector.recordPaymentSuccess();
            }

            logger.info("Payment operation completed - Method: {}, CorrelationId: {}",
                       methodName, correlationId);

            return result;

        } catch (Exception e) {
            if (timerSample != null) {
                metricsCollector.stopPaymentTimer(timerSample);
                metricsCollector.recordPaymentFailure();
            }

            logger.error("Payment operation failed - Method: {}, CorrelationId: {}, Error: {}",
                        methodName, correlationId, e.getMessage(), e);

            throw e;
        }
    }
}