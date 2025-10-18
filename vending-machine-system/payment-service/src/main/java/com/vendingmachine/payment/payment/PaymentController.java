package com.vendingmachine.payment.payment;

import com.vendingmachine.common.event.TransactionEvent;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final Random random = new Random();

    @PostMapping("/payment/process")
    public ResponseEntity<PaymentResponse> processPayment(@Valid @RequestBody PaymentRequest request) {
        // Create TransactionEvent with the provided transaction ID
        TransactionEvent event = new TransactionEvent();
        event.setTransactionId(request.getTransactionId());
        event.setTotalAmount(request.getAmount().doubleValue());
        event.setStatus("STARTED");
        event.setTimestamp(System.currentTimeMillis());

        PaymentTransaction transaction = paymentService.processPaymentForTransaction(event, request);
        PaymentResponse response = mapToResponse(transaction);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/admin/payment/transactions")
    public ResponseEntity<List<PaymentResponse>> getAllTransactions() {
        List<PaymentTransaction> transactions = paymentService.getAllTransactions();
        List<PaymentResponse> responses = transactions.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @PostMapping("/payment/refund")
    public ResponseEntity<Map<String, Object>> processRefund(@RequestBody Map<String, Object> refundRequest) {
        // Simulate refund processing
        boolean success = random.nextDouble() < 0.95; // 95% success rate
        Map<String, Object> response = Map.of("success", success);
        return ResponseEntity.ok(response);
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