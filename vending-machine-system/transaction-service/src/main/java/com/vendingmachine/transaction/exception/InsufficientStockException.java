package com.vendingmachine.transaction.exception;

/**
 * Business exception thrown when product stock is unavailable.
 * This is an expected business scenario, not a system error.
 */
public class InsufficientStockException extends RuntimeException {
    
    public InsufficientStockException(String message) {
        super(message);
    }
    
    public InsufficientStockException(String message, Throwable cause) {
        super(message, cause);
    }
}
