package com.vendingmachine.notification.notification;

import com.vendingmachine.common.aop.annotation.Auditable;
import com.vendingmachine.common.aop.annotation.ExecutionTime;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Transactional
    @Bulkhead(name = "database-operations", fallbackMethod = "createNotificationFallback", type = Bulkhead.Type.SEMAPHORE)
    @Auditable(operation = "CREATE_NOTIFICATION", entityType = "Notification", logParameters = true)
    @ExecutionTime(operation = "CREATE_NOTIFICATION", warningThreshold = 800, detailed = true)
    public Notification createNotification(NotificationType type, String message, String details,
                                         Long entityId, String entityType, String severity) {
        Notification notification = new Notification();
        notification.setType(type);
        notification.setMessage(message);
        notification.setDetails(details);
        notification.setEntityId(entityId);
        notification.setEntityType(entityType);
        notification.setSeverity(severity);
        notification.setStatus("NEW");

        Notification saved = notificationRepository.save(notification);
        log.info("Created notification: {} - {}", type, message);
        return saved;
    }

    @Transactional
    @Auditable(operation = "MARK_NOTIFICATION_AS_READ", entityType = "Notification", logParameters = true)
    @ExecutionTime(operation = "MARK_NOTIFICATION_AS_READ", warningThreshold = 500, detailed = true)
    public void markAsRead(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found"));
        notification.setStatus("READ");
        notificationRepository.save(notification);
        log.info("Marked notification {} as read", notificationId);
    }

    @Transactional
    @Auditable(operation = "ARCHIVE_NOTIFICATION", entityType = "Notification", logParameters = true)
    @ExecutionTime(operation = "ARCHIVE_NOTIFICATION", warningThreshold = 500, detailed = true)
    public void archiveNotification(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found"));
        notification.setStatus("ARCHIVED");
        notificationRepository.save(notification);
        log.info("Archived notification {}", notificationId);
    }

    @Bulkhead(name = "database-operations", fallbackMethod = "getAllNotificationsFallback", type = Bulkhead.Type.SEMAPHORE)
    public List<Notification> getAllNotifications() {
        return notificationRepository.findAll();
    }

    @Bulkhead(name = "database-operations", fallbackMethod = "getUnreadNotificationsFallback", type = Bulkhead.Type.SEMAPHORE)
    public List<Notification> getUnreadNotifications() {
        return notificationRepository.findUnreadNotifications();
    }

    public List<Notification> getNotificationsByStatus(String status) {
        return notificationRepository.findByStatus(status);
    }

    public List<Notification> getNotificationsByType(NotificationType type) {
        return notificationRepository.findByType(type);
    }

    public List<Notification> getRecentNotifications(int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return notificationRepository.findRecentNotifications(since);
    }

    public long getUnreadCount() {
        return notificationRepository.countByStatus("NEW");
    }

    public Notification getNotificationById(Long id) {
        return notificationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Notification not found"));
    }

    // Fallback methods for Bulkhead pattern

    /**
     * Fallback method when database operations bulkhead is full for creating notifications
     */
    private Notification createNotificationFallback(NotificationType type, String message, String details,
                                                   Long entityId, String entityType, String severity, Exception ex) {
        log.error("Database operations bulkhead full for creating notification. Type: {}, Message: {}. Error: {}", 
                type, message, ex.getMessage());
        log.warn("Notification service database operations at capacity - creating fallback notification");
        
        // Create a temporary notification without saving to database
        Notification failbackNotification = new Notification();
        failbackNotification.setId(-1L); // Negative ID to indicate fallback
        failbackNotification.setType(type);
        failbackNotification.setMessage("CAPACITY_EXCEEDED: " + message);
        failbackNotification.setDetails(details);
        failbackNotification.setEntityId(entityId);
        failbackNotification.setEntityType(entityType);
        failbackNotification.setSeverity("HIGH"); // Escalate severity due to capacity issues
        failbackNotification.setStatus("FALLBACK");
        
        return failbackNotification;
    }

    /**
     * Fallback method when database operations bulkhead is full for getting all notifications
     */
    private List<Notification> getAllNotificationsFallback(Exception ex) {
        log.error("Database operations bulkhead full for getAllNotifications. Error: {}", ex.getMessage());
        log.warn("Notification service database operations at capacity - returning empty list");
        return List.of(); // Return empty list when database is at capacity
    }

    /**
     * Fallback method when database operations bulkhead is full for getting unread notifications
     */
    private List<Notification> getUnreadNotificationsFallback(Exception ex) {
        log.error("Database operations bulkhead full for getUnreadNotifications. Error: {}", ex.getMessage());
        log.warn("Notification service database operations at capacity - returning empty list");
        return List.of(); // Return empty list when database is at capacity
    }
}