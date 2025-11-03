package com.vendingmachine.transaction.aop;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;

import com.vendingmachine.transaction.transaction.Transaction;
import com.vendingmachine.transaction.transaction.dto.PurchaseRequestDTO;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Specific aspect for transaction operations.
 * Provides specialized logging, validations and business metrics.
 * 
 * Pointcuts are annotations to define where the aspect should apply.
 * This aspect includes:
 * - Logging before and after transaction operations
 * - Error handling for failed transactions
 * - Business validations
 * - Performance tracking
 * 
 * @author bruno.gil
 */
@Aspect
@Component
@Slf4j
public class TransactionOperationAspect {
    
    private static final DateTimeFormatter TIMESTAMP_FORMAT = 
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /**
     * Pointcut for all TransactionController methods
     */
    @Pointcut("execution(* com.vendingmachine.transaction.transaction.TransactionController.*(..))")
    public void transactionControllerMethods() {}
    
    /**
     * Pointcut for all TransactionService methods
     */
    @Pointcut("execution(* com.vendingmachine.transaction.transaction.TransactionService.*(..))")
    public void transactionServiceMethods() {}
    
    /**
     * Pointcut for methods that modify transactions (create, compensate)
     */
    @Pointcut("execution(* com.vendingmachine.transaction.transaction.TransactionService.purchase(..)) || " +
              "execution(* com.vendingmachine.transaction.transaction.TransactionService.compensateTransaction(..))")
    public void transactionModificationMethods() {}
    
    /**
     * Log before operations in the controller
     */
    @Before("transactionControllerMethods()")
    public void logBeforeTransactionController(JoinPoint joinPoint) {
        String methodName = joinPoint.getSignature().getName();
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        
        StringBuilder controllerLog = new StringBuilder();
        controllerLog.append("\n[TRANSACTION-CONTROLLER] ").append(timestamp);
        controllerLog.append("\n|- Method: ").append(methodName);
        controllerLog.append("\n|_ Args: ").append(joinPoint.getArgs().length);
        
        log.info(controllerLog.toString());
    }
    
    /**
     * Logging and additional validations for transaction modification operations
     */
    @Around("transactionModificationMethods()")
    public Object logTransactionModifications(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        long startTime = System.currentTimeMillis();
        
        // Log critical operation start
        StringBuilder startLog = new StringBuilder();
        startLog.append("\n[TRANSACTION-MODIFICATION-START] ").append(timestamp);
        startLog.append("\n|- Operation: ").append(methodName);
        startLog.append("\n|_ Critical operation initiated");
        
        log.info(startLog.toString());
        
        try {
            // Additional validations for transactions
            validateTransactionOperation(joinPoint);
            
            // Execute original method
            Object result = joinPoint.proceed();
            
            // Log success
            long executionTime = System.currentTimeMillis() - startTime;
            StringBuilder successLog = new StringBuilder();
            successLog.append("\n[TRANSACTION-MODIFICATION-SUCCESS] ").append(LocalDateTime.now().format(TIMESTAMP_FORMAT));
            successLog.append("\n|- Operation: ").append(methodName);
            successLog.append("\n|- Duration: ").append(executionTime).append(" ms");
            successLog.append("\n|_ Operation completed successfully");
            
            log.info(successLog.toString());
            
            return result;
            
        } catch (Exception e) {
            // Log error
            long executionTime = System.currentTimeMillis() - startTime;
            StringBuilder errorLog = new StringBuilder();
            errorLog.append("\n[TRANSACTION-MODIFICATION-ERROR] ").append(LocalDateTime.now().format(TIMESTAMP_FORMAT));
            errorLog.append("\n|- Operation: ").append(methodName);
            errorLog.append("\n|- Duration: ").append(executionTime).append(" ms");
            errorLog.append("\n|- Error: ").append(e.getClass().getSimpleName());
            errorLog.append("\n|_ Message: ").append(e.getMessage());
            
            log.error(errorLog.toString());
            
            throw e;
        }
    }
    
    /**
     * Log after successful operations in the service
     */
    @AfterReturning(pointcut = "transactionServiceMethods()", returning = "result")
    public void logAfterTransactionService(JoinPoint joinPoint, Object result) {
        String methodName = joinPoint.getSignature().getName();
        String resultInfo = getResultInfo(result);
        
        StringBuilder serviceLog = new StringBuilder();
        serviceLog.append("\n[TRANSACTION-SERVICE] Method: ").append(methodName).append(" completed");
        serviceLog.append("\n|_ Result: ").append(resultInfo);
        
        log.info(serviceLog.toString());
    }
    
    /**
     * Log when errors occur in the service
     */
    @AfterThrowing(pointcut = "transactionServiceMethods()", throwing = "exception")
    public void logTransactionServiceErrors(JoinPoint joinPoint, Throwable exception) {
        String methodName = joinPoint.getSignature().getName();
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        
        StringBuilder errorLog = new StringBuilder();
        errorLog.append("\n[TRANSACTION-SERVICE-ERROR] ").append(timestamp);
        errorLog.append("\n|- Method: ").append(methodName);
        errorLog.append("\n|- Exception: ").append(exception.getClass().getSimpleName());
        errorLog.append("\n|_ Message: ").append(exception.getMessage());
        
        log.warn(errorLog.toString());
    }
    
    /**
     * Additional specific validations for transaction operations
     */
    private void validateTransactionOperation(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        
        // Validate if there's a purchase request in the parameters
        for (Object arg : args) {
            if (arg instanceof PurchaseRequestDTO) {
                PurchaseRequestDTO request = (PurchaseRequestDTO) arg;
                
                // Validate that the purchase has items
                if (request.getItems() == null || request.getItems().isEmpty()) {
                    throw new IllegalArgumentException("Purchase request must have at least one item");
                }
                
                // Validate payment info
                if (request.getPaymentInfo() == null) {
                    throw new IllegalArgumentException("Payment information is required");
                }
                
                log.debug(String.format("Purchase request validation passed - Items: %d", 
                        request.getItems().size()));
            }
            
            if (arg instanceof Transaction) {
                Transaction transaction = (Transaction) arg;
                
                log.debug(String.format("Transaction validation - Transaction ID: %s", 
                        transaction.getId()));
            }
        }
    }
    
    /**
     * Gets safe information about the operation result
     */
    private String getResultInfo(Object result) {
        if (result == null) {
            return "null";
        }
        
        String className = result.getClass().getSimpleName();
        
        // For ResponseEntity, extract information from body
        if (className.equals("ResponseEntity")) {
            return "ResponseEntity[" + result.toString().length() + " chars]";
        }
        
        // For collections, show size
        if (result instanceof java.util.Collection) {
            return "Collection[" + ((java.util.Collection<?>) result).size() + " items]";
        }
        
        return className;
    }
}
