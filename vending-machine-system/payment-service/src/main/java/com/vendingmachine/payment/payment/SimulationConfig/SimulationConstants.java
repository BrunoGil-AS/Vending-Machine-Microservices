package com.vendingmachine.payment.payment.SimulationConfig;

import org.springframework.stereotype.Component;

import lombok.NoArgsConstructor;

@Component
@NoArgsConstructor
public class SimulationConstants {

	// Property placeholders (style similar to dispensing-service)
	public static final String PAYMENT_SIMULATION_SUCCESS_RATE = "${payment.simulation.success-rate}";
	public static final String PAYMENT_SIMULATION_SUCCESS_RATE_DEFAULT = "${payment.simulation.success-rate:0.98}";

	// Enable/disable payment simulation (set false to force deterministic success for card payments)
	public static final String PAYMENT_SIMULATION_ENABLED = "${payment.simulation.enabled}";
	public static final String PAYMENT_SIMULATION_ENABLED_DEFAULT = "${payment.simulation.enabled:false}";

	public static final String SPRING_KAFKA_TOPIC_PAYMENT_EVENTS = "${spring.kafka.topic.payment-events}";
	public static final String SPRING_KAFKA_TOPIC_PAYMENT_EVENTS_DEFAULT = "${spring.kafka.topic.payment-events:payment-events}";

}
