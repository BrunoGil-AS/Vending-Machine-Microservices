package com.vendingmachine.common.aop.aspect;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import com.vendingmachine.common.aop.annotation.Auditable;
import com.vendingmachine.common.util.CorrelationIdUtil;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

/**
 * Aspect to audit critical system operations.
 * Automatically logs who, what, when, and the result of operations.
 * 
 * @author bruno.gil
 */
@Aspect
@Component
@Slf4j
public class AuditAspect {
    
    private static final DateTimeFormatter TIMESTAMP_FORMAT = 
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /**
     * Executes before methods annotated with @Auditable.
     * Logs the operation details including user, operation name, entity type, class, method.
     */
    @Before("@annotation(auditable)")
    public void auditBefore(JoinPoint joinPoint, Auditable auditable) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String userContext = getCurrentUserContext(joinPoint);
        String correlationId = CorrelationIdUtil.getCorrelationId();
        
        StringBuilder auditLog = new StringBuilder();
        auditLog.append("\n[AUDIT-START] ").append(timestamp);
        auditLog.append("\n|- Correlation ID: ").append(correlationId);
        auditLog.append("\n|- User: ").append(userContext);
        auditLog.append("\n|- Operation: ").append(auditable.operation().isEmpty() ? methodName : auditable.operation());
        auditLog.append("\n|- Entity Type: ").append(auditable.entityType().isEmpty() ? "N/A" : auditable.entityType());
        auditLog.append("\n|- Class: ").append(className);
        auditLog.append("\n|- Method: ").append(methodName);
        
        if (auditable.logParameters() && joinPoint.getArgs().length > 0) {
            auditLog.append("\n|- Parameters: ");
            Object[] args = joinPoint.getArgs();
            for (int i = 0; i < args.length; i++) {
                if (i > 0) auditLog.append(", ");
                auditLog.append(getSafeParameterString(args[i]));
            }
        }
        
        log.info(auditLog.toString());
    }
    
    /**
     * Executes after the successful execution of methods annotated with @Auditable.
     * Logs the operation details including operation name, status, and result.
     */
    @AfterReturning(pointcut = "@annotation(auditable)", returning = "result")
    public void auditAfterReturning(JoinPoint joinPoint, Auditable auditable, Object result) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String methodName = joinPoint.getSignature().getName();
        String correlationId = CorrelationIdUtil.getCorrelationId();
        
        StringBuilder auditLog = new StringBuilder();
        auditLog.append("\n[AUDIT-SUCCESS] ").append(timestamp);
        auditLog.append("\n|- Correlation ID: ").append(correlationId);
        auditLog.append("\n|- Operation: ").append(auditable.operation().isEmpty() ? methodName : auditable.operation());
        auditLog.append("\n|- Status: SUCCESS");
        
        if (auditable.logResult() && result != null) {
            auditLog.append("\n|_ Result: ").append(getSafeParameterString(result));
        } else {
            auditLog.append("\n|_ Result: [Not logged]");
        }
        
        log.info(auditLog.toString());
    }
    
    /**
     * Executes when an exception occurs in methods annotated with @Auditable.
     * Logs the operation details including operation name, status, and exception details.
     */
    @AfterThrowing(pointcut = "@annotation(auditable)", throwing = "exception")
    public void auditAfterThrowing(JoinPoint joinPoint, Auditable auditable, Throwable exception) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String methodName = joinPoint.getSignature().getName();
        String userContext = getCurrentUserContext(joinPoint);
        String correlationId = CorrelationIdUtil.getCorrelationId();
        
        StringBuilder auditLog = new StringBuilder();
        auditLog.append("\n[AUDIT-ERROR] ").append(timestamp);
        auditLog.append("\n|- Correlation ID: ").append(correlationId);
        auditLog.append("\n|- User: ").append(userContext);
        auditLog.append("\n|- Operation: ").append(auditable.operation().isEmpty() ? methodName : auditable.operation());
        auditLog.append("\n|- Status: ERROR");
        auditLog.append("\n|- Exception: ").append(exception.getClass().getSimpleName());
        auditLog.append("\n|_ Message: ").append(exception.getMessage());
        
        log.warn(auditLog.toString());
    }
    
    /**
     * Obtains user context from request headers (X-User-Id, X-Username, X-User-Role).
     * This is for anonymous vending machine operations.
     */
    private String getCurrentUserContext(JoinPoint joinPoint) {
        try {
            // In vending machine, we track anonymous transactions
            // Could extract from HTTP headers if available in context
            return "ANONYMOUS";
        } catch (Exception e) {
            return "SYSTEM";
        }
    }
    
    /**
     * Safely formats the parameter for logging.
     * Avoids logging sensitive information such as passwords or credentials.
     */
    private String getSafeParameterString(Object parameter) {
        if (parameter == null) {
            return "null";
        }
        
        // Don't log objects that may contain sensitive information
        String className = parameter.getClass().getSimpleName();
        if (className.toLowerCase().contains("password") || 
            className.toLowerCase().contains("credential") ||
            className.toLowerCase().contains("secret") ||
            className.toLowerCase().contains("payment")) {
            return "[SENSITIVE_DATA]";
        }
        
        // For arrays
        if (parameter.getClass().isArray()) {
            return Arrays.toString((Object[]) parameter);
        }
        
        // For long strings, truncate
        String paramStr = parameter.toString();
        if (paramStr.length() > 200) {
            return paramStr.substring(0, 200) + "... [truncated]";
        }
        
        return paramStr;
    }
}
