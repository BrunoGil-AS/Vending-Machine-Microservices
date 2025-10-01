package com.vendingmachine.gateway.User;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.mapping.Column;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Admin User Entity - Reactive version using R2DBC
 * Represents administrative users with role-based access control
 */
@Table("admin_users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminUser {
    
    @Id
    private Long id;
    
    @Column("username")
    private String username;
    
    @Column("password_hash")
    private String passwordHash;
    
    @Column("role")
    private String role; // Store as String: "SUPER_ADMIN" or "ADMIN"
    
    @Column("active")
    private Boolean active;
    
    @CreatedDate
    @Column("created_at")
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column("updated_at")
    private LocalDateTime updatedAt;
    
    public enum Role {
        SUPER_ADMIN,
        ADMIN;
        
        public static Role fromString(String role) {
            try {
                return Role.valueOf(role);
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Invalid role: " + role);
            }
        }
    }
    
    public Role getRoleEnum() {
        return Role.fromString(this.role);
    }
    
    public void setRoleEnum(Role role) {
        this.role = role.name();
    }
}
