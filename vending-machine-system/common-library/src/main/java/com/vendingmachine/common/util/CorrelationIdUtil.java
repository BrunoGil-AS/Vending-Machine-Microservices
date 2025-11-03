package com.vendingmachine.common.util;

import org.slf4j.MDC;
import java.util.UUID;

/**
 * Utility class for managing Correlation IDs across the distributed system.
 * Correlation IDs help trace requests across multiple microservices.
 * 
 * @author bruno.gil
 */
public class CorrelationIdUtil {
    
    public static final String CORRELATION_ID_KEY = "correlationId";
    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    
    /**
     * Generate a new Correlation ID
     */
    public static String generateCorrelationId() {
        return UUID.randomUUID().toString();
    }
    
    /**
     * Set the Correlation ID in the MDC (Mapped Diagnostic Context)
     * This makes it available in log patterns
     */
    public static void setCorrelationId(String correlationId) {
        if (correlationId == null || correlationId.trim().isEmpty()) {
            correlationId = generateCorrelationId();
        }
        MDC.put(CORRELATION_ID_KEY, correlationId);
    }
    
    /**
     * Get the current Correlation ID from MDC
     */
    public static String getCorrelationId() {
        String correlationId = MDC.get(CORRELATION_ID_KEY);
        if (correlationId == null || correlationId.trim().isEmpty()) {
            correlationId = generateCorrelationId();
            setCorrelationId(correlationId);
        }
        return correlationId;
    }
    
    /**
     * Clear the Correlation ID from MDC
     * Should be called after request processing is complete
     */
    public static void clearCorrelationId() {
        MDC.remove(CORRELATION_ID_KEY);
    }
}
