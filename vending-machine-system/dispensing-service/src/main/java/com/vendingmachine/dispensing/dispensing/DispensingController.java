package com.vendingmachine.dispensing.dispensing;

import com.vendingmachine.common.aop.annotation.Auditable;
import com.vendingmachine.common.aop.annotation.ExecutionTime;
import com.vendingmachine.common.util.CorrelationIdUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class DispensingController {

    private final DispensingService dispensingService;
    private final HardwareStatusService hardwareStatusService;

    @GetMapping("/dispensing/transactions")
    @Auditable(operation = "GET_ALL_DISPENSING_TRANSACTIONS", entityType = "DispensingOperation", logResult = true)
    @ExecutionTime(operation = "GET_ALL_DISPENSING_TRANSACTIONS", warningThreshold = 1500)
    public ResponseEntity<List<DispensingOperation>> getAllDispensingTransactions() {
        try {
            CorrelationIdUtil.setCorrelationId(UUID.randomUUID().toString());
            
            List<DispensingOperation> transactions = dispensingService.getAllDispensingTransactions();
            return ResponseEntity.ok(transactions);
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }

    @GetMapping("/dispensing/transactions/{transactionId}")
    @Auditable(operation = "GET_DISPENSING_BY_TRANSACTION", entityType = "DispensingOperation", logParameters = true, logResult = true)
    @ExecutionTime(operation = "GET_DISPENSING_BY_TRANSACTION", warningThreshold = 1000)
    public ResponseEntity<List<DispensingOperation>> getDispensingTransactionsByTransactionId(@PathVariable Long transactionId) {
        try {
            CorrelationIdUtil.setCorrelationId(UUID.randomUUID().toString());
            
            List<DispensingOperation> transactions = dispensingService.getDispensingTransactionsByTransactionId(transactionId);
            return ResponseEntity.ok(transactions);
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }

    @GetMapping("/hardware/status")
    @Auditable(operation = "GET_HARDWARE_STATUS", entityType = "HardwareStatus", logResult = true)
    @ExecutionTime(operation = "GET_HARDWARE_STATUS", warningThreshold = 800)
    public ResponseEntity<List<HardwareStatus>> getHardwareStatus() {
        try {
            CorrelationIdUtil.setCorrelationId(UUID.randomUUID().toString());
            
            List<HardwareStatus> statuses = hardwareStatusService.getAllHardwareStatus();
            return ResponseEntity.ok(statuses);
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }

    @PostMapping("/hardware/{componentName}/operational")
    @Auditable(operation = "MARK_COMPONENT_OPERATIONAL", entityType = "HardwareStatus", logParameters = true)
    @ExecutionTime(operation = "MARK_COMPONENT_OPERATIONAL", warningThreshold = 500, detailed = true)
    public ResponseEntity<Void> markComponentOperational(@PathVariable String componentName) {
        try {
            CorrelationIdUtil.setCorrelationId(UUID.randomUUID().toString());
            
            hardwareStatusService.markComponentOperational(componentName);
            return ResponseEntity.ok().build();
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }

    @GetMapping("/hardware/operational")
    @Auditable(operation = "CHECK_HARDWARE_OPERATIONAL", entityType = "HardwareStatus", logResult = true)
    @ExecutionTime(operation = "CHECK_HARDWARE_OPERATIONAL", warningThreshold = 500)
    public ResponseEntity<Boolean> isHardwareOperational() {
        try {
            CorrelationIdUtil.setCorrelationId(UUID.randomUUID().toString());
            
            boolean operational = hardwareStatusService.isHardwareOperational();
            return ResponseEntity.ok(operational);
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }
}