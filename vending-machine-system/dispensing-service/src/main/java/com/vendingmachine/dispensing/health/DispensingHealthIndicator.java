package com.vendingmachine.dispensing.health;

import com.vendingmachine.dispensing.dispensing.HardwareStatusRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DispensingHealthIndicator implements HealthIndicator {

    @Autowired
    private HardwareStatusRepository hardwareStatusRepository;

    @Override
    public Health health() {
        try {
            // Check database connectivity
            long totalComponents = hardwareStatusRepository.count();

            // Check for offline or error components
            List<com.vendingmachine.dispensing.dispensing.HardwareStatus> offlineComponents =
                hardwareStatusRepository.findByStatus("OFFLINE");
            List<com.vendingmachine.dispensing.dispensing.HardwareStatus> errorComponents =
                hardwareStatusRepository.findByStatus("ERROR");

            Health.Builder health = Health.up()
                .withDetail("totalComponents", totalComponents)
                .withDetail("offlineComponents", offlineComponents.size())
                .withDetail("errorComponents", errorComponents.size());

            if (offlineComponents.size() > 0 || errorComponents.size() > 0) {
                health.withDetail("warning",
                    "Hardware issues detected: " + offlineComponents.size() + " offline, " +
                    errorComponents.size() + " with errors");
            }

            return health.build();

        } catch (Exception e) {
            return Health.down()
                .withDetail("error", "Database connection failed")
                .withDetail("message", e.getMessage())
                .build();
        }
    }
}