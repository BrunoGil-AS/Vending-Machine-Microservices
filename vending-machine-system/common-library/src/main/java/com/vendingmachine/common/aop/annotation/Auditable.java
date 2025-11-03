package com.vendingmachine.common.aop.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for auditing critical operations in the system.
 * Automatically logs operation details such as the operation name, entity type,
 * and whether to log input parameters and results.
 * 
 * @author bruno.gil
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {
    
    /**
     * Description of the operation being audited.
     * This should provide a clear and concise description of the operation
     * being performed, which will be logged for auditing purposes.
     */
    String operation();
    
    /**
     * Type of entity being accessed.
     */
    String entityType() default "";
    
    /**
     * If the input parameters should be logged.
     * This is useful for tracking the inputs to critical operations,
     * especially in cases where the parameters may affect the outcome of the operation.
     */
    boolean logParameters() default true;
    
    /**
     * If the result of the operation should be logged.
     * This is useful for tracking the outcomes of critical operations,
     * especially in cases where the result may indicate success or failure.
     */
    boolean logResult() default false;
}
