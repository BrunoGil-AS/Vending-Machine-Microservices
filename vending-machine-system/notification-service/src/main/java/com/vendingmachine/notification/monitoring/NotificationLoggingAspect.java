package com.vendingmachine.notification.monitoring;

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
public class NotificationLoggingAspect {

    private static final Logger logger = LoggerFactory.getLogger(NotificationLoggingAspect.class);

    private final CorrelationIdManager correlationIdManager;

    public NotificationLoggingAspect(CorrelationIdManager correlationIdManager) {
        this.correlationIdManager = correlationIdManager;
    }

    @Pointcut("execution(* com.vendingmachine.notification.notification.NotificationService.*(..))")
    public void notificationServiceMethods() {}

    @Around("notificationServiceMethods()")
    public Object logNotificationOperations(ProceedingJoinPoint joinPoint) throws Throwable {
        String correlationId = correlationIdManager.getCorrelationId();
        String methodName = joinPoint.getSignature().getName();

        logger.info("Notification operation started - Method: {}, CorrelationId: {}",
                   methodName, correlationId);

        try {
            Object result = joinPoint.proceed();

            logger.info("Notification operation completed - Method: {}, CorrelationId: {}",
                       methodName, correlationId);

            return result;

        } catch (Exception e) {
            logger.error("Notification operation failed - Method: {}, CorrelationId: {}, Error: {}",
                        methodName, correlationId, e.getMessage(), e);

            throw e;
        }
    }
}