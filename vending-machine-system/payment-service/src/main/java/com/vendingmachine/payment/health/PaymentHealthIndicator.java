package com.vendingmachine.payment.health;

import com.vendingmachine.payment.payment.PaymentTransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class PaymentHealthIndicator implements HealthIndicator {

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Override
    public Health health() {
        try {
            // Check database connectivity
            long totalTransactions = paymentTransactionRepository.count();

            // Check for recent failed transactions (assuming status field exists)
            // For now, just check total count and database connectivity
            Health.Builder health = Health.up()
                .withDetail("totalTransactions", totalTransactions)
                .withDetail("databaseStatus", "connected");

            // Add warning if no transactions in last hour (basic check)
            if (totalTransactions == 0) {
                health.withDetail("warning", "No payment transactions found");
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