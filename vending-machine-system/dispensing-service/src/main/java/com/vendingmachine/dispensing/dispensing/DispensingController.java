package com.vendingmachine.dispensing.dispensing;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class DispensingController {

    private final DispensingService dispensingService;

    @GetMapping("/dispensing/transactions")
    public ResponseEntity<List<DispensingTransaction>> getAllDispensingTransactions() {
        List<DispensingTransaction> transactions = dispensingService.getAllDispensingTransactions();
        return ResponseEntity.ok(transactions);
    }

    @GetMapping("/dispensing/transactions/{transactionId}")
    public ResponseEntity<List<DispensingTransaction>> getDispensingTransactionsByTransactionId(@PathVariable Long transactionId) {
        List<DispensingTransaction> transactions = dispensingService.getDispensingTransactionsByTransactionId(transactionId);
        return ResponseEntity.ok(transactions);
    }
}