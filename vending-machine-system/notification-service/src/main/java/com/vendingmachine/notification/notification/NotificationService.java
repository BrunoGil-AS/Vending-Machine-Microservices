package com.vendingmachine.notification.notification;

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
    public void markAsRead(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found"));
        notification.setStatus("READ");
        notificationRepository.save(notification);
        log.info("Marked notification {} as read", notificationId);
    }

    @Transactional
    public void archiveNotification(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found"));
        notification.setStatus("ARCHIVED");
        notificationRepository.save(notification);
        log.info("Archived notification {}", notificationId);
    }

    public List<Notification> getAllNotifications() {
        return notificationRepository.findAll();
    }

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
}