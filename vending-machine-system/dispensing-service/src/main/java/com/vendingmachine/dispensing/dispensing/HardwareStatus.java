package com.vendingmachine.dispensing.dispensing;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "hardware_status")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HardwareStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "component_name", nullable = false)
    private String componentName; // e.g., "dispenser_motor", "product_chute", etc.

    @Column(nullable = false)
    private String status; // "OPERATIONAL", "MAINTENANCE", "OUT_OF_ORDER"

    @Column
    private String lastError;

    @Column(nullable = false)
    private LocalDateTime lastChecked;

    @Column
    private LocalDateTime lastMaintenance;

    @PrePersist
    protected void onCreate() {
        lastChecked = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        lastChecked = LocalDateTime.now();
    }
}