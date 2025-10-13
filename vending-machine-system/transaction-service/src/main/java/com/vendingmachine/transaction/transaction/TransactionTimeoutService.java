package com.vendingmachine.transaction.transaction;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionTimeoutService {

    private final TransactionRepository transactionRepository;
    private final TransactionService transactionService;

    @Scheduled(fixedRate = 60000) // Check every minute
    @Transactional
    public void checkForStuckTransactions() {
        log.debug("Checking for stuck transactions...");

        LocalDateTime timeoutThreshold = LocalDateTime.now().minusMinutes(5); // 5 minute timeout

        // Find transactions that are stuck in PENDING or PROCESSING state
        List<Transaction> stuckTransactions = transactionRepository.findByStatusInAndCreatedAtBefore(
            List.of(TransactionStatus.PENDING, TransactionStatus.PROCESSING),
            timeoutThreshold
        );

        for (Transaction transaction : stuckTransactions) {
            log.warn("Found stuck transaction {} in status {} created at {}",
                    transaction.getId(), transaction.getStatus(), transaction.getCreatedAt());

            try {
                // Attempt compensation for stuck transactions
                transactionService.compensateTransaction(transaction.getId(),
                    "Transaction timeout - stuck in " + transaction.getStatus() + " state");
            } catch (Exception e) {
                log.error("Failed to compensate stuck transaction {}", transaction.getId(), e);
                // Mark as failed if compensation fails
                transaction.setStatus(TransactionStatus.FAILED);
                transactionRepository.save(transaction);
            }
        }

        if (!stuckTransactions.isEmpty()) {
            log.info("Processed {} stuck transactions", stuckTransactions.size());
        }
    }
}