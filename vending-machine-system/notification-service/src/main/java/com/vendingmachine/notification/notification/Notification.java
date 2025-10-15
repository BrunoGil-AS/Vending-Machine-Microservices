package com.vendingmachine.notification.notification;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column
    private String details;

    @Column(name = "entity_id")
    private Long entityId; // transactionId, productId, etc.

    @Column(name = "entity_type")
    private String entityType; // "TRANSACTION", "PRODUCT", "PAYMENT", etc.

    @Column(nullable = false)
    private String status; // "NEW", "READ", "ARCHIVED"

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column
    private String severity; // "LOW", "MEDIUM", "HIGH", "CRITICAL"

    @PrePersist
    protected void onCreate() {
        timestamp = LocalDateTime.now();
        if (status == null) {
            status = "NEW";
        }
    }
}