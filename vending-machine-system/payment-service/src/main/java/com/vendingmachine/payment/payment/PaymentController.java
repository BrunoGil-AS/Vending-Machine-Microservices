package com.vendingmachine.payment.payment;

import com.vendingmachine.common.event.TransactionEvent;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/payment/process")
    public ResponseEntity<PaymentResponse> processPayment(@Valid @RequestBody PaymentRequest request) {
        // For backward compatibility, create a mock TransactionEvent
        TransactionEvent mockEvent = new TransactionEvent();
        mockEvent.setTransactionId(0L); // No transaction ID for direct payments
        mockEvent.setTotalAmount(request.getAmount());
        mockEvent.setStatus("STARTED");
        mockEvent.setTimestamp(System.currentTimeMillis());

        PaymentTransaction transaction = paymentService.processPaymentForTransaction(mockEvent);
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