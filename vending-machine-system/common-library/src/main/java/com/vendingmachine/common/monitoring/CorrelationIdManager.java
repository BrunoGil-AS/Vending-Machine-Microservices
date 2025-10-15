package com.vendingmachine.common.monitoring;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CorrelationIdManager {

    private static final String CORRELATION_ID_KEY = "correlationId";
    private static final ThreadLocal<String> correlationIdHolder = new ThreadLocal<>();

    /**
     * Generate a new correlation ID and set it in MDC and ThreadLocal
     */
    public String generateCorrelationId() {
        String correlationId = UUID.randomUUID().toString();
        setCorrelationId(correlationId);
        return correlationId;
    }

    /**
     * Set correlation ID in MDC and ThreadLocal
     */
    public void setCorrelationId(String correlationId) {
        MDC.put(CORRELATION_ID_KEY, correlationId);
        correlationIdHolder.set(correlationId);
    }

    /**
     * Get current correlation ID from ThreadLocal
     */
    public String getCorrelationId() {
        String correlationId = correlationIdHolder.get();
        if (correlationId == null) {
            correlationId = generateCorrelationId();
        }
        return correlationId;
    }

    /**
     * Clear correlation ID from MDC and ThreadLocal
     */
    public void clearCorrelationId() {
        MDC.remove(CORRELATION_ID_KEY);
        correlationIdHolder.remove();
    }

    /**
     * Inherit correlation ID from another thread/context
     */
    public void inheritCorrelationId(String parentCorrelationId) {
        if (parentCorrelationId != null && !parentCorrelationId.trim().isEmpty()) {
            setCorrelationId(parentCorrelationId);
        } else {
            generateCorrelationId();
        }
    }
}