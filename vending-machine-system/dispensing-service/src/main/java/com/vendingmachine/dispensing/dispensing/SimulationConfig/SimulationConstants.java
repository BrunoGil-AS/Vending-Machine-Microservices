package com.vendingmachine.dispensing.dispensing.SimulationConfig;

import org.springframework.stereotype.Component;

import lombok.NoArgsConstructor;

@Component
@NoArgsConstructor
public class SimulationConstants {

    // Property keys
    public static final String DISPENSING_SIMULATION_SUCCESS_RATE = "${dispensing.simulation.success-rate}";
    public static final String DISPENSING_SIMULATION_JAM_PROBABILITY = "${dispensing.simulation.jam-probability}";
    public static final String SPRING_KAFKA_TOPIC_DISPENSING_EVENTS = "${spring.kafka.topic.dispensing-events}";

    // New property to enable/disable dispensing simulation (set to false to force deterministic 100% success)
    public static final String DISPENSING_SIMULATION_ENABLED = "${dispensing.simulation.enabled}";
    public static final String DISPENSING_SIMULATION_ENABLED_DEFAULT = "${dispensing.simulation.enabled:false}";
    
    // Verification success rate property (probability that sensor verification passes)
    public static final String DISPENSING_SIMULATION_VERIFICATION_RATE = "${dispensing.simulation.verification-success-rate}";
    public static final String DISPENSING_SIMULATION_VERIFICATION_RATE_DEFAULT = "${dispensing.simulation.verification-success-rate:0.98}";

    public static final String SPRING_KAFKA_TOPIC_DISPENSING_EVENTS_DEFAULT = "${spring.kafka.topic.dispensing-events:dispensing-events}";
    
}
