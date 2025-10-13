package com.vendingmachine.transaction.transaction.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionSummaryDTO {
    private Long totalTransactions;
    private BigDecimal totalRevenue;
    private Map<String, Long> transactionsByStatus;
    private Map<LocalDate, BigDecimal> dailyRevenue;
    private Map<LocalDate, Long> dailyTransactionCount;
    private BigDecimal averageTransactionValue;
    private Long successfulTransactions;
    private Long failedTransactions;
    private BigDecimal successRate;
}