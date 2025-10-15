package com.vendingmachine.notification.health;

import com.vendingmachine.notification.notification.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class NotificationHealthIndicator implements HealthIndicator {

    @Autowired
    private NotificationRepository notificationRepository;

    @Override
    public Health health() {
        try {
            // Check database connectivity
            long totalNotifications = notificationRepository.count();

            // Check for unread notifications (might indicate processing backlog)
            long unreadCount = notificationRepository.countByStatus("NEW");

            // Check for error notifications
            long errorCount = notificationRepository.countByType(
                com.vendingmachine.notification.notification.NotificationType.TRANSACTION_FAILED) +
                notificationRepository.countByType(
                com.vendingmachine.notification.notification.NotificationType.SYSTEM_ALERT);

            Health.Builder health = Health.up()
                .withDetail("totalNotifications", totalNotifications)
                .withDetail("unreadNotifications", unreadCount)
                .withDetail("errorNotifications", errorCount);

            if (unreadCount > 100) { // Arbitrary threshold
                health.withDetail("warning", "High number of unread notifications: " + unreadCount);
            }

            if (errorCount > 0) {
                health.withDetail("warning", "Error notifications detected: " + errorCount);
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