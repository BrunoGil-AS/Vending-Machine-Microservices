package com.vendingmachine.payment.payment;

import com.vendingmachine.common.aop.annotation.Auditable;
import com.vendingmachine.common.aop.annotation.ExecutionTime;
import com.vendingmachine.common.event.TransactionEvent;
import com.vendingmachine.common.util.CorrelationIdUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final Random random = new Random();

    @PostMapping("/payment/process")
    @Auditable(operation = "PROCESS_PAYMENT", entityType = "Payment", logParameters = true, logResult = true)
    @ExecutionTime(operation = "PROCESS_PAYMENT", warningThreshold = 1000, detailed = true)
    public ResponseEntity<PaymentResponse> processPayment(@Valid @RequestBody PaymentRequest request) {
        try {
            CorrelationIdUtil.setCorrelationId(UUID.randomUUID().toString());
            
            // Create TransactionEvent with the provided transaction ID
            TransactionEvent event = new TransactionEvent();
            event.setTransactionId(request.getTransactionId());
            event.setTotalAmount(request.getAmount().doubleValue());
            event.setStatus("STARTED");
            event.setTimestamp(System.currentTimeMillis());

            PaymentTransaction transaction = paymentService.processPaymentForTransaction(event, request);
            PaymentResponse response = mapToResponse(transaction);
            return ResponseEntity.ok(response);
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }

    @GetMapping("/admin/payment/transactions")
    @Auditable(operation = "GET_ALL_PAYMENT_TRANSACTIONS", entityType = "Payment", logResult = true)
    @ExecutionTime(operation = "GET_ALL_PAYMENT_TRANSACTIONS", warningThreshold = 1500)
    public ResponseEntity<List<PaymentResponse>> getAllTransactions() {
        try {
            CorrelationIdUtil.setCorrelationId(UUID.randomUUID().toString());
            
            List<PaymentTransaction> transactions = paymentService.getAllTransactions();
            List<PaymentResponse> responses = transactions.stream()
                    .map(this::mapToResponse)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(responses);
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }

    @PostMapping("/payment/refund")
    @Auditable(operation = "PROCESS_REFUND", entityType = "Payment", logParameters = true, logResult = true)
    @ExecutionTime(operation = "PROCESS_REFUND", warningThreshold = 800, detailed = true)
    public ResponseEntity<Map<String, Object>> processRefund(@RequestBody Map<String, Object> refundRequest) {
        try {
            CorrelationIdUtil.setCorrelationId(UUID.randomUUID().toString());
            
            // Simulate refund processing
            boolean success = random.nextDouble() < 0.95; // 95% success rate
            Map<String, Object> response = Map.of("success", success);
            return ResponseEntity.ok(response);
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }

    private PaymentResponse mapToResponse(PaymentTransaction transaction) {
        return new PaymentResponse(
                transaction.getId(),
                transaction.getAmount(),
                transaction.getMethod(),
                transaction.getStatus(),
                transaction.getCreatedAt(),
                transaction.getUpdatedAt()
        );
    }
}