package com.vendingmachine.transaction.transaction;

import com.vendingmachine.transaction.transaction.dto.TransactionDTO;
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
}