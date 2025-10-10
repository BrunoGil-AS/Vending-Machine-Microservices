package com.vendingmachine.transaction.transaction;

import com.vendingmachine.transaction.transaction.dto.PurchaseRequestDTO;
import com.vendingmachine.transaction.transaction.dto.TransactionDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/transaction")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping("/purchase")
    public ResponseEntity<TransactionDTO> purchase(@Valid @RequestBody PurchaseRequestDTO request) {
        TransactionDTO transaction = transactionService.purchase(request);
        return ResponseEntity.ok(transaction);
    }
}