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
    private final HardwareStatusService hardwareStatusService;

    @GetMapping("/dispensing/transactions")
    public ResponseEntity<List<DispensingOperation>> getAllDispensingTransactions() {
        List<DispensingOperation> transactions = dispensingService.getAllDispensingTransactions();
        return ResponseEntity.ok(transactions);
    }

    @GetMapping("/dispensing/transactions/{transactionId}")
    public ResponseEntity<List<DispensingOperation>> getDispensingTransactionsByTransactionId(@PathVariable Long transactionId) {
        List<DispensingOperation> transactions = dispensingService.getDispensingTransactionsByTransactionId(transactionId);
        return ResponseEntity.ok(transactions);
    }

    @GetMapping("/hardware/status")
    public ResponseEntity<List<HardwareStatus>> getHardwareStatus() {
        List<HardwareStatus> statuses = hardwareStatusService.getAllHardwareStatus();
        return ResponseEntity.ok(statuses);
    }

    @PostMapping("/hardware/{componentName}/operational")
    public ResponseEntity<Void> markComponentOperational(@PathVariable String componentName) {
        hardwareStatusService.markComponentOperational(componentName);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/hardware/operational")
    public ResponseEntity<Boolean> isHardwareOperational() {
        boolean operational = hardwareStatusService.isHardwareOperational();
        return ResponseEntity.ok(operational);
    }
}