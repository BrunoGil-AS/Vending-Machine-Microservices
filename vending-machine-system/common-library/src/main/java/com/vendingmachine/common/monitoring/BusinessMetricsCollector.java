package com.vendingmachine.common.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class BusinessMetricsCollector {

    private static final Logger logger = LoggerFactory.getLogger(BusinessMetricsCollector.class);

    private final Counter transactionSuccessCounter;
    private final Counter transactionFailureCounter;
    private final Counter paymentSuccessCounter;
    private final Counter paymentFailureCounter;
    private final Counter dispensingSuccessCounter;
    private final Counter dispensingFailureCounter;
    private final Timer paymentProcessingTimer;
    private final Timer dispensingProcessingTimer;

    public BusinessMetricsCollector(MeterRegistry meterRegistry) {
        // Transaction metrics
        this.transactionSuccessCounter = Counter.builder("vending_machine.transaction.success")
                .description("Number of successful transactions")
                .register(meterRegistry);

        this.transactionFailureCounter = Counter.builder("vending_machine.transaction.failure")
                .description("Number of failed transactions")
                .register(meterRegistry);

        // Payment metrics
        this.paymentSuccessCounter = Counter.builder("vending_machine.payment.success")
                .description("Number of successful payments")
                .register(meterRegistry);

        this.paymentFailureCounter = Counter.builder("vending_machine.payment.failure")
                .description("Number of failed payments")
                .register(meterRegistry);

        this.paymentProcessingTimer = Timer.builder("vending_machine.payment.duration")
                .description("Payment processing duration")
                .register(meterRegistry);

        // Dispensing metrics
        this.dispensingSuccessCounter = Counter.builder("vending_machine.dispensing.success")
                .description("Number of successful dispensing operations")
                .register(meterRegistry);

        this.dispensingFailureCounter = Counter.builder("vending_machine.dispensing.failure")
                .description("Number of failed dispensing operations")
                .register(meterRegistry);

        this.dispensingProcessingTimer = Timer.builder("vending_machine.dispensing.duration")
                .description("Dispensing processing duration")
                .register(meterRegistry);
    }

    // Transaction metrics
    public void recordTransactionSuccess() {
        transactionSuccessCounter.increment();
        logger.debug("Recorded transaction success");
    }

    public void recordTransactionFailure() {
        transactionFailureCounter.increment();
        logger.debug("Recorded transaction failure");
    }

    // Payment metrics
    public void recordPaymentSuccess() {
        paymentSuccessCounter.increment();
        logger.debug("Recorded payment success");
    }

    public void recordPaymentFailure() {
        paymentFailureCounter.increment();
        logger.debug("Recorded payment failure");
    }

    public Timer.Sample startPaymentTimer() {
        return Timer.start();
    }

    public void stopPaymentTimer(Timer.Sample sample) {
        sample.stop(paymentProcessingTimer);
    }

    // Dispensing metrics
    public void recordDispensingSuccess() {
        dispensingSuccessCounter.increment();
        logger.debug("Recorded dispensing success");
    }

    public void recordDispensingFailure() {
        dispensingFailureCounter.increment();
        logger.debug("Recorded dispensing failure");
    }

    public Timer.Sample startDispensingTimer() {
        return Timer.start();
    }

    public void stopDispensingTimer(Timer.Sample sample) {
        sample.stop(dispensingProcessingTimer);
    }

    // Inventory metrics
    public void recordLowStockAlert(String productName, int currentStock) {
        logger.warn("Low stock alert - Product: {}, Current Stock: {}", productName, currentStock);
        // Could add gauge metrics here for stock levels
    }

    public void recordStockUpdate(String productName, int oldStock, int newStock) {
        logger.info("Stock updated - Product: {}, {} -> {}", productName, oldStock, newStock);
    }
}