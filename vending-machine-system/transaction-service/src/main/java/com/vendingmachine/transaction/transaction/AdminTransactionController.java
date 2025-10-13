package com.vendingmachine.transaction.transaction;

import com.vendingmachine.transaction.transaction.dto.TransactionDTO;
import com.vendingmachine.transaction.transaction.dto.TransactionSummaryDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/transaction")
@RequiredArgsConstructor
public class AdminTransactionController {

    private final TransactionService transactionService;

    @GetMapping("/history")
    public ResponseEntity<List<TransactionDTO>> getTransactionHistory() {
        List<TransactionDTO> history = transactionService.getAllTransactions();
        return ResponseEntity.ok(history);
    }

    @GetMapping("/history/{status}")
    public ResponseEntity<List<TransactionDTO>> getTransactionsByStatus(@PathVariable TransactionStatus status) {
        List<TransactionDTO> transactions = transactionService.getTransactionsByStatus(status);
        return ResponseEntity.ok(transactions);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionDTO> getTransactionById(@PathVariable Long id) {
        TransactionDTO transaction = transactionService.getTransactionById(id);
        return ResponseEntity.ok(transaction);
    }

    @PostMapping("/{id}/compensate")
    public ResponseEntity<Void> compensateTransaction(@PathVariable Long id, @RequestParam String reason) {
        transactionService.compensateTransaction(id, reason);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/summary")
    public ResponseEntity<TransactionSummaryDTO> getTransactionSummary() {
        TransactionSummaryDTO summary = transactionService.getTransactionSummary();
        return ResponseEntity.ok(summary);
    }
}