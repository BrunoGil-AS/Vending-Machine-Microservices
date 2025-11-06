package com.vendingmachine.transaction.transaction;

import com.vendingmachine.transaction.transaction.dto.TransactionItemDTO;
import com.vendingmachine.transaction.transaction.dto.TransactionDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Internal Transaction Controller
 * Endpoints for inter-service communication only
 * These endpoints are protected by X-Request-Source: internal header
 */
@RestController
@RequestMapping("/api/internal/transaction")
@RequiredArgsConstructor
@Slf4j
public class InternalTransactionController {

    private final TransactionService transactionService;

    /**
     * Get transaction items for dispensing service
     * Used by dispensing-service to get product details for a transaction
     * 
     * @param id Transaction ID
     * @return List of transaction items
     */
    @GetMapping("/{id}/items")
    public ResponseEntity<List<TransactionItemDTO>> getTransactionItems(@PathVariable Long id) {
        log.debug("Internal request: Getting items for transaction {}", id);
        TransactionDTO transaction = transactionService.getTransactionById(id);
        return ResponseEntity.ok(transaction.getItems());
    }

    /**
     * Get transaction details
     * Used by other services to get full transaction information
     * 
     * @param id Transaction ID
     * @return Transaction details
     */
    @GetMapping("/{id}")
    public ResponseEntity<TransactionDTO> getTransaction(@PathVariable Long id) {
        log.debug("Internal request: Getting transaction {}", id);
        TransactionDTO transaction = transactionService.getTransactionById(id);
        return ResponseEntity.ok(transaction);
    }
}
