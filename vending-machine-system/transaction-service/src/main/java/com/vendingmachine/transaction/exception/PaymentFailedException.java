package com.vendingmachine.transaction.exception;

/**
 * Business exception thrown when payment processing fails.
 * This is an expected business scenario, not a system error.
 */
public class PaymentFailedException extends RuntimeException {
    
    public PaymentFailedException(String message) {
        super(message);
    }
    
    public PaymentFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
