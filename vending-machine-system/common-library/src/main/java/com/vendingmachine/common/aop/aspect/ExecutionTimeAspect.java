package com.vendingmachine.common.aop.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import com.vendingmachine.common.aop.annotation.ExecutionTime;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Aspect for measuring method execution time.
 * Provides performance metrics and performance alerts.
 * 
 * @author bruno.gil
 */
@Aspect
@Component
@Slf4j
public class ExecutionTimeAspect {
    
    private static final DateTimeFormatter TIMESTAMP_FORMAT = 
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    /**
     * Wraps method execution to measure time
     */
    @Around("@annotation(executionTime)")
    public Object measureExecutionTime(ProceedingJoinPoint joinPoint, ExecutionTime executionTime) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String operationName = executionTime.operation().isEmpty() ? methodName : executionTime.operation();
        
        // Log start
        long startTime = System.currentTimeMillis();
        String startTimestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        
        if (executionTime.detailed()) {
            StringBuilder startLog = new StringBuilder();
            startLog.append("\n[PERFORMANCE-START] ").append(startTimestamp);
            startLog.append("\n|- Operation: ").append(operationName);
            startLog.append("\n|_ Class.Method: ").append(className).append(".").append(methodName);
            
            log.info(startLog.toString());
        }
        
        Object result = null;
        boolean success = true;
        Throwable exception = null;
        
        try {
            // Execute the original method
            result = joinPoint.proceed();
            return result;
        } catch (Throwable throwable) {
            success = false;
            exception = throwable;
            throw throwable;
        } finally {
            // Calculate execution time
            long endTime = System.currentTimeMillis();
            long executionTimeMs = endTime - startTime;
            String endTimestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
            
            // Create performance log
            StringBuilder perfLog = new StringBuilder();
            perfLog.append("\n[PERFORMANCE-REPORT] ").append(endTimestamp);
            perfLog.append("\n|- Operation: ").append(operationName);
            perfLog.append("\n|- Class: ").append(className);
            perfLog.append("\n|- Method: ").append(methodName);
            perfLog.append("\n|- Execution Time: ").append(executionTimeMs).append(" ms");
            perfLog.append("\n|- Status: ").append(success ? "SUCCESS" : "ERROR");
            
            if (executionTime.detailed()) {
                perfLog.append("\n|- Start Time: ").append(startTimestamp);
                perfLog.append("\n|- End Time: ").append(endTimestamp);
                if (!success && exception != null) {
                    perfLog.append("\n|- Exception: ").append(exception.getClass().getSimpleName());
                }
            }
            
            // Determine log level based on execution time
            if (executionTimeMs > executionTime.warningThreshold()) {
                perfLog.append("\n|_ WARNING: Execution time exceeded threshold (")
                       .append(executionTime.warningThreshold()).append(" ms)");
                log.warn(perfLog.toString());
            } else {
                perfLog.append("\n|_ Performance: NORMAL");
                log.info(perfLog.toString());
            }
            
            // Additional log for metrics (could be integrated with monitoring systems)
            logMetrics(operationName, className, methodName, executionTimeMs, success);
        }
    }
    
    /**
     * Logs metrics in structured format for integration with monitoring systems
     */
    private void logMetrics(String operation, String className, String methodName, 
                           long executionTime, boolean success) {
        // This method could send metrics to systems like Prometheus, Micrometer, etc.
        String metricsLog = String.format(
            "METRICS|operation=%s|class=%s|method=%s|execution_time_ms=%d|success=%s|timestamp=%s",
            operation, className, methodName, executionTime, success, 
            LocalDateTime.now().format(TIMESTAMP_FORMAT)
        );
        
        // Separate log for metrics (could be directed to a specific appender)
        log.info("[METRICS] " + metricsLog);
    }
}
