package com.vendingmachine.transaction.recovery;

import com.vendingmachine.transaction.transaction.Transaction;
import com.vendingmachine.transaction.transaction.TransactionRepository;
import com.vendingmachine.transaction.transaction.TransactionStatus;
import com.vendingmachine.transaction.client.PaymentServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionRecoveryService {

    private final TransactionRepository transactionRepository;
    private final PaymentServiceClient paymentClient;

    /**
     * Scheduled job to check for transactions that may have payment records
     * but failed due to technical errors (like serialization issues)
     */
    @Scheduled(fixedDelay = 300000) // Run every 5 minutes
    @Transactional
    public void recoverFailedTransactions() {
        log.debug("Starting failed transaction recovery process");

        // Look for transactions that failed in the last hour
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        List<Transaction> failedTransactions = transactionRepository
                .findByStatusAndCreatedAtAfter(TransactionStatus.FAILED, oneHourAgo);

        if (failedTransactions.isEmpty()) {
            log.debug("No failed transactions found for recovery");
            return;
        }

        log.info("Found {} failed transactions to check for recovery", failedTransactions.size());

        for (Transaction transaction : failedTransactions) {
            try {
                recoverTransaction(transaction);
            } catch (Exception e) {
                log.error("Failed to recover transaction {}: {}", transaction.getId(), e.getMessage());
            }
        }
    }

    /**
     * Attempt to recover a specific failed transaction by checking payment status
     */
    @Transactional
    public void recoverTransaction(Transaction transaction) {
        log.info("Attempting to recover transaction {}", transaction.getId());

        try {
            // Check if payment actually succeeded despite the technical error
            Map<String, Object> paymentStatus = checkPaymentStatus(transaction.getId());
            
            if (paymentStatus != null && isPaymentSuccessful(paymentStatus)) {
                log.info("Payment was successful for transaction {}, recovering transaction", transaction.getId());
                
                // Update transaction to processing status since payment succeeded
                transaction.setStatus(TransactionStatus.PROCESSING);
                transactionRepository.save(transaction);
                
                log.info("Successfully recovered transaction {} - moved to PROCESSING", transaction.getId());
                
                // TODO: Could trigger dispensing event here if needed
                // publishProcessingEvent(transaction);
                
            } else {
                log.debug("Payment was not successful for transaction {}, keeping as FAILED", transaction.getId());
                
                // If payment failed, could initiate refund if partial payment was made
                if (paymentStatus != null && hasPartialPayment(paymentStatus)) {
                    log.info("Partial payment detected for transaction {}, initiating refund", transaction.getId());
                    initiateRefundForFailedTransaction(transaction);
                }
            }
            
        } catch (Exception e) {
            log.warn("Could not check payment status for transaction {}: {}", transaction.getId(), e.getMessage());
        }
    }

    /**
     * Check payment status using the payment service
     */
    private Map<String, Object> checkPaymentStatus(Long transactionId) {
        try {
            // This would be a new endpoint in payment service to check status
            // For now, we'll use a simple approach
            return paymentClient.getPaymentStatus(transactionId.toString());
        } catch (Exception e) {
            log.warn("Failed to check payment status for transaction {}: {}", transactionId, e.getMessage());
            return null;
        }
    }

    /**
     * Determine if payment was successful based on response
     */
    private boolean isPaymentSuccessful(Map<String, Object> paymentStatus) {
        if (paymentStatus == null) {
            return false;
        }

        // Check various indicators of success
        Object successObj = paymentStatus.get("success");
        String status = (String) paymentStatus.get("status");
        
        return (successObj instanceof Boolean && (Boolean) successObj) || 
               "SUCCESS".equals(status) || 
               "COMPLETED".equals(status);
    }

    /**
     * Check if there was a partial payment made
     */
    private boolean hasPartialPayment(Map<String, Object> paymentStatus) {
        if (paymentStatus == null) {
            return false;
        }

        String status = (String) paymentStatus.get("status");
        return "PARTIAL".equals(status) || "PENDING".equals(status);
    }

    /**
     * Initiate refund for a transaction that has partial payment
     */
    private void initiateRefundForFailedTransaction(Transaction transaction) {
        try {
            Map<String, Object> refundResponse = paymentClient.refundPayment(
                transaction.getId().toString(), 
                transaction.getTotalAmount()
            );
            
            if (refundResponse != null) {
                Boolean success = (Boolean) refundResponse.get("success");
                if (Boolean.TRUE.equals(success)) {
                    log.info("Successfully initiated refund for failed transaction {}", transaction.getId());
                    transaction.setStatus(TransactionStatus.CANCELLED);
                    transactionRepository.save(transaction);
                }
            }
        } catch (Exception e) {
            log.error("Failed to initiate refund for transaction {}: {}", transaction.getId(), e.getMessage());
        }
    }
}