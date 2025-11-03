package com.vendingmachine.transaction.transaction;

import com.vendingmachine.transaction.transaction.dto.PurchaseRequestDTO;
import com.vendingmachine.transaction.transaction.dto.TransactionDTO;
import com.vendingmachine.transaction.transaction.dto.TransactionSummaryDTO;
import com.vendingmachine.common.aop.annotation.Auditable;
import com.vendingmachine.common.aop.annotation.ExecutionTime;
import com.vendingmachine.common.util.CorrelationIdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/transaction")
@RequiredArgsConstructor
@Slf4j
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping("/purchase")
    @Auditable(operation = "PURCHASE_TRANSACTION", entityType = "Transaction", logParameters = true, logResult = true)
    @ExecutionTime(operation = "Purchase Request", warningThreshold = 3000, detailed = true)
    public ResponseEntity<TransactionDTO> purchase(
            @Valid @RequestBody PurchaseRequestDTO request,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
        
        // Set correlation ID for request tracing
        CorrelationIdUtil.setCorrelationId(correlationId);
        
        try {
            log.info("Processing purchase request with {} items", request.getItems().size());
            TransactionDTO transaction = transactionService.purchase(request);
            return ResponseEntity.ok(transaction);
        } catch (Exception e) {
            log.error("Error processing purchase request", e);
            throw e;
        }
        finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }
    
    @GetMapping("/all")
    @Auditable(operation = "GET_ALL_TRANSACTIONS", entityType = "Transaction", logResult = false)
    @ExecutionTime(operation = "Get All Transactions", warningThreshold = 2000)
    public ResponseEntity<List<TransactionDTO>> getAllTransactions(
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
        
        CorrelationIdUtil.setCorrelationId(correlationId);
        
        try {
            List<TransactionDTO> transactions = transactionService.getAllTransactions();
            return ResponseEntity.ok(transactions);
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }
    
    @GetMapping("/status/{status}")
    @Auditable(operation = "GET_TRANSACTIONS_BY_STATUS", entityType = "Transaction", logParameters = true)
    @ExecutionTime(operation = "Get Transactions by Status", warningThreshold = 1500)
    public ResponseEntity<List<TransactionDTO>> getTransactionsByStatus(
            @PathVariable TransactionStatus status,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
        
        CorrelationIdUtil.setCorrelationId(correlationId);
        
        try {
            List<TransactionDTO> transactions = transactionService.getTransactionsByStatus(status);
            return ResponseEntity.ok(transactions);
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }
    
    @GetMapping("/{id}")
    @Auditable(operation = "GET_TRANSACTION_BY_ID", entityType = "Transaction", logParameters = true, logResult = true)
    @ExecutionTime(operation = "Get Transaction by ID", warningThreshold = 1000)
    public ResponseEntity<TransactionDTO> getTransactionById(
            @PathVariable Long id,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
        
        CorrelationIdUtil.setCorrelationId(correlationId);
        
        try {
            TransactionDTO transaction = transactionService.getTransactionById(id);
            return ResponseEntity.ok(transaction);
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }
    
    @GetMapping("/summary")
    @Auditable(operation = "GET_TRANSACTION_SUMMARY", entityType = "Transaction", logResult = false)
    @ExecutionTime(operation = "Get Transaction Summary", warningThreshold = 2000)
    public ResponseEntity<TransactionSummaryDTO> getTransactionSummary(
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
        
        CorrelationIdUtil.setCorrelationId(correlationId);
        
        try {
            TransactionSummaryDTO summary = transactionService.getTransactionSummary();
            return ResponseEntity.ok(summary);
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }
}
