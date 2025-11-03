package com.vendingmachine.notification.notification;

import com.vendingmachine.common.aop.annotation.Auditable;
import com.vendingmachine.common.aop.annotation.ExecutionTime;
import com.vendingmachine.common.util.CorrelationIdUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/notifications")
    @Auditable(operation = "GET_ALL_NOTIFICATIONS", entityType = "Notification", logResult = true)
    @ExecutionTime(operation = "GET_ALL_NOTIFICATIONS", warningThreshold = 1500)
    public ResponseEntity<List<Notification>> getAllNotifications() {
        try {
            CorrelationIdUtil.setCorrelationId(UUID.randomUUID().toString());
            
            List<Notification> notifications = notificationService.getAllNotifications();
            return ResponseEntity.ok(notifications);
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }

    @GetMapping("/notifications/unread")
    @Auditable(operation = "GET_UNREAD_NOTIFICATIONS", entityType = "Notification", logResult = true)
    @ExecutionTime(operation = "GET_UNREAD_NOTIFICATIONS", warningThreshold = 1000)
    public ResponseEntity<List<Notification>> getUnreadNotifications() {
        try {
            CorrelationIdUtil.setCorrelationId(UUID.randomUUID().toString());
            
            List<Notification> notifications = notificationService.getUnreadNotifications();
            return ResponseEntity.ok(notifications);
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }

    @GetMapping("/notifications/status/{status}")
    @Auditable(operation = "GET_NOTIFICATIONS_BY_STATUS", entityType = "Notification", logParameters = true, logResult = true)
    @ExecutionTime(operation = "GET_NOTIFICATIONS_BY_STATUS", warningThreshold = 1000)
    public ResponseEntity<List<Notification>> getNotificationsByStatus(@PathVariable String status) {
        try {
            CorrelationIdUtil.setCorrelationId(UUID.randomUUID().toString());
            
            List<Notification> notifications = notificationService.getNotificationsByStatus(status);
            return ResponseEntity.ok(notifications);
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }

    @GetMapping("/notifications/type/{type}")
    @Auditable(operation = "GET_NOTIFICATIONS_BY_TYPE", entityType = "Notification", logParameters = true, logResult = true)
    @ExecutionTime(operation = "GET_NOTIFICATIONS_BY_TYPE", warningThreshold = 1000)
    public ResponseEntity<List<Notification>> getNotificationsByType(@PathVariable NotificationType type) {
        try {
            CorrelationIdUtil.setCorrelationId(UUID.randomUUID().toString());
            
            List<Notification> notifications = notificationService.getNotificationsByType(type);
            return ResponseEntity.ok(notifications);
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }

    @GetMapping("/notifications/recent/{hours}")
    @Auditable(operation = "GET_RECENT_NOTIFICATIONS", entityType = "Notification", logParameters = true, logResult = true)
    @ExecutionTime(operation = "GET_RECENT_NOTIFICATIONS", warningThreshold = 1000)
    public ResponseEntity<List<Notification>> getRecentNotifications(@PathVariable int hours) {
        try {
            CorrelationIdUtil.setCorrelationId(UUID.randomUUID().toString());
            
            List<Notification> notifications = notificationService.getRecentNotifications(hours);
            return ResponseEntity.ok(notifications);
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }

    @GetMapping("/notifications/{id}")
    @Auditable(operation = "GET_NOTIFICATION_BY_ID", entityType = "Notification", logParameters = true, logResult = true)
    @ExecutionTime(operation = "GET_NOTIFICATION_BY_ID", warningThreshold = 500)
    public ResponseEntity<Notification> getNotificationById(@PathVariable Long id) {
        try {
            CorrelationIdUtil.setCorrelationId(UUID.randomUUID().toString());
            
            Notification notification = notificationService.getNotificationById(id);
            return ResponseEntity.ok(notification);
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }

    @PutMapping("/notifications/{id}/read")
    @Auditable(operation = "MARK_NOTIFICATION_AS_READ", entityType = "Notification", logParameters = true)
    @ExecutionTime(operation = "MARK_NOTIFICATION_AS_READ", warningThreshold = 500, detailed = true)
    public ResponseEntity<Void> markAsRead(@PathVariable Long id) {
        try {
            CorrelationIdUtil.setCorrelationId(UUID.randomUUID().toString());
            
            notificationService.markAsRead(id);
            return ResponseEntity.ok().build();
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }

    @PutMapping("/notifications/{id}/archive")
    @Auditable(operation = "ARCHIVE_NOTIFICATION", entityType = "Notification", logParameters = true)
    @ExecutionTime(operation = "ARCHIVE_NOTIFICATION", warningThreshold = 500, detailed = true)
    public ResponseEntity<Void> archiveNotification(@PathVariable Long id) {
        try {
            CorrelationIdUtil.setCorrelationId(UUID.randomUUID().toString());
            
            notificationService.archiveNotification(id);
            return ResponseEntity.ok().build();
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }

    @GetMapping("/notifications/stats")
    @Auditable(operation = "GET_NOTIFICATION_STATS", entityType = "Notification", logResult = true)
    @ExecutionTime(operation = "GET_NOTIFICATION_STATS", warningThreshold = 800)
    public ResponseEntity<Map<String, Object>> getNotificationStats() {
        try {
            CorrelationIdUtil.setCorrelationId(UUID.randomUUID().toString());
            
            long unreadCount = notificationService.getUnreadCount();
            long totalCount = notificationService.getAllNotifications().size();

            Map<String, Object> stats = Map.of(
                    "total", totalCount,
                    "unread", unreadCount,
                    "read", totalCount - unreadCount
            );

            return ResponseEntity.ok(stats);
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }
}