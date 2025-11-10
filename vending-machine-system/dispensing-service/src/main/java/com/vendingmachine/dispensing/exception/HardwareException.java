package com.vendingmachine.dispensing.exception;

/**
 * Exception thrown when hardware operations fail in the dispensing service.
 * This includes motor failures, sensor malfunctions, jams, and other hardware-related issues.
 */
public class HardwareException extends RuntimeException {

    private final String hardwareComponent;
    private final String operationType;

    /**
     * Constructs a new hardware exception with the specified detail message.
     *
     * @param message the detail message
     */
    public HardwareException(String message) {
        super(message);
        this.hardwareComponent = "UNKNOWN";
        this.operationType = "UNKNOWN";
    }

    /**
     * Constructs a new hardware exception with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause
     */
    public HardwareException(String message, Throwable cause) {
        super(message, cause);
        this.hardwareComponent = "UNKNOWN";
        this.operationType = "UNKNOWN";
    }

    /**
     * Constructs a new hardware exception with detailed hardware information.
     *
     * @param message the detail message
     * @param hardwareComponent the specific hardware component that failed (e.g., "dispenser_motor", "product_chute", "sensor_array")
     * @param operationType the type of operation that failed (e.g., "DISPENSE", "VERIFY", "INITIALIZE")
     */
    public HardwareException(String message, String hardwareComponent, String operationType) {
        super(message);
        this.hardwareComponent = hardwareComponent;
        this.operationType = operationType;
    }

    /**
     * Constructs a new hardware exception with detailed hardware information and cause.
     *
     * @param message the detail message
     * @param hardwareComponent the specific hardware component that failed
     * @param operationType the type of operation that failed
     * @param cause the cause
     */
    public HardwareException(String message, String hardwareComponent, String operationType, Throwable cause) {
        super(message, cause);
        this.hardwareComponent = hardwareComponent;
        this.operationType = operationType;
    }

    /**
     * Gets the hardware component that failed.
     *
     * @return the hardware component name
     */
    public String getHardwareComponent() {
        return hardwareComponent;
    }

    /**
     * Gets the type of operation that failed.
     *
     * @return the operation type
     */
    public String getOperationType() {
        return operationType;
    }

    /**
     * Returns a detailed description of the hardware failure.
     *
     * @return a detailed error description
     */
    @Override
    public String toString() {
        return String.format("HardwareException{component='%s', operation='%s', message='%s'}", 
                hardwareComponent, operationType, getMessage());
    }

    // Predefined static factory methods for common hardware failures

    /**
     * Creates a hardware exception for dispensing motor failures.
     *
     * @param productId the product ID that failed to dispense
     * @return a configured HardwareException
     */
    public static HardwareException dispensingMotorFailure(Long productId) {
        return new HardwareException(
                String.format("Dispensing motor failed for product %d", productId),
                "dispenser_motor",
                "DISPENSE"
        );
    }

    /**
     * Creates a hardware exception for product jam situations.
     *
     * @param productId the product ID that caused the jam
     * @return a configured HardwareException
     */
    public static HardwareException productJam(Long productId) {
        return new HardwareException(
                String.format("Product jam detected for product %d in dispensing chute", productId),
                "product_chute",
                "DISPENSE"
        );
    }

    /**
     * Creates a hardware exception for sensor verification failures.
     *
     * @param productId the product ID that failed verification
     * @return a configured HardwareException
     */
    public static HardwareException verificationFailure(Long productId) {
        return new HardwareException(
                String.format("Sensor verification failed for product %d - product may not have been dispensed", productId),
                "sensor_array",
                "VERIFY"
        );
    }

    /**
     * Creates a hardware exception for hardware not being operational.
     *
     * @return a configured HardwareException
     */
    public static HardwareException hardwareNotOperational() {
        return new HardwareException(
                "Hardware system is not operational - dispensing unavailable",
                "system",
                "INITIALIZE"
        );
    }

    /**
     * Creates a hardware exception for timeout operations.
     *
     * @param component the hardware component that timed out
     * @param timeoutMs the timeout value in milliseconds
     * @return a configured HardwareException
     */
    public static HardwareException operationTimeout(String component, long timeoutMs) {
        return new HardwareException(
                String.format("Hardware operation timed out after %d ms", timeoutMs),
                component,
                "TIMEOUT"
        );
    }
}