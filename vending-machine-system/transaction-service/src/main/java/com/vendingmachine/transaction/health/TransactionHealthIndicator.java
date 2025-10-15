package com.vendingmachine.transaction.health;

import com.vendingmachine.transaction.transaction.Transaction;
import com.vendingmachine.transaction.transaction.TransactionRepository;
import com.vendingmachine.transaction.transaction.TransactionStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Component
public class TransactionHealthIndicator implements HealthIndicator {

    @Autowired
    private TransactionRepository transactionRepository;

    @Override
    public Health health() {
        try {
            // Check database connectivity
            long totalTransactions = transactionRepository.count();

            // Check for pending transactions (might indicate processing issues)
            List<Transaction> pendingTransactions = transactionRepository
                .findByStatusOrderByCreatedAtDesc(TransactionStatus.PENDING);

            // Check for stuck transactions (created more than 5 minutes ago and still pending)
            List<Transaction> stuckTransactions = transactionRepository
                .findByStatusInAndCreatedAtBefore(
                    Arrays.asList(TransactionStatus.PENDING, TransactionStatus.PROCESSING),
                    LocalDateTime.now().minusMinutes(5)
                );

            Health.Builder health = Health.up()
                .withDetail("totalTransactions", totalTransactions)
                .withDetail("pendingTransactions", pendingTransactions.size())
                .withDetail("stuckTransactions", stuckTransactions.size());

            if (stuckTransactions.size() > 0) {
                health.withDetail("warning", "Stuck transactions detected: " + stuckTransactions.size());
            }

            return health.build();

        } catch (Exception e) {
            return Health.down()
                .withDetail("error", "Database connection failed")
                .withDetail("message", e.getMessage())
                .build();
        }
    }
}