package com.vendingmachine.common.aop.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for measuring the execution time of methods.
 * This annotation is useful for performance monitoring and identifying bottlenecks.
 * 
 * @author bruno.gil
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExecutionTime {
    
    /**
     * Name of the operation for logging purposes.
     * If not specified, the method name will be used.
     */
    String operation() default "";
    
    /**
     * Threshold in milliseconds for performance alerts.
     * If the method takes longer than this threshold, a warning is logged.
     */
    long warningThreshold() default 1000;
    
    /**
     * If detailed logging is enabled.
     * If true, additional information about the execution will be logged.
     */
    boolean detailed() default false;
}
