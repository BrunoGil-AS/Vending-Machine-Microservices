package com.vendingmachine.notification.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByStatus(String status);

    List<Notification> findByType(NotificationType type);

    List<Notification> findBySeverity(String severity);

    List<Notification> findByEntityIdAndEntityType(Long entityId, String entityType);

    @Query("SELECT n FROM Notification n WHERE n.timestamp >= :since ORDER BY n.timestamp DESC")
    List<Notification> findRecentNotifications(@Param("since") LocalDateTime since);

    @Query("SELECT n FROM Notification n WHERE n.status = 'NEW' ORDER BY n.timestamp DESC")
    List<Notification> findUnreadNotifications();

    long countByStatus(String status);

    long countByType(NotificationType type);
}