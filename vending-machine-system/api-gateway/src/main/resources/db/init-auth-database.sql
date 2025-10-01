-- Authentication Database Initialization Script

USE vending_auth;

-- Create admin_users table
CREATE TABLE IF NOT EXISTS admin_users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role ENUM('SUPER_ADMIN', 'ADMIN') NOT NULL,
    active BOOLEAN DEFAULT TRUE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_username (username),
    INDEX idx_active (active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Insert default super admin user
-- Username: admin
-- Password: admin123
-- BCrypt hash for 'admin123'
INSERT INTO admin_users (username, password_hash, role, active)
VALUES ('admin', '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN96.f9rDNrP1JCr5X8Oi', 'SUPER_ADMIN', true)
ON DUPLICATE KEY UPDATE username=username;

-- Insert test admin user
-- Username: testadmin
-- Password: testadmin123
INSERT INTO admin_users (username, password_hash, role, active)
VALUES ('testadmin', '$2a$12$9k3RGcQHF/8gJdxFLp2FH.hHWnZL5y9Ck7BvUKcJxz7zQeZUfZQe2', 'ADMIN', true)
ON DUPLICATE KEY UPDATE username=username;

-- Verify data
SELECT id, username, role, active, created_at FROM admin_users;