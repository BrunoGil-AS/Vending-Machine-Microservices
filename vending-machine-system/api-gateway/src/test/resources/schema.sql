-- Test Schema Initialization
CREATE TABLE IF NOT EXISTS admin_users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(255) NOT NULL,
    active BOOLEAN DEFAULT TRUE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Insert default super admin user
MERGE INTO admin_users (username, password_hash, role, active)
KEY(username)
VALUES ('admin', '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN96.f9rDNrP1JCr5X8Oi', 'SUPER_ADMIN', true);

-- Insert test admin user
MERGE INTO admin_users (username, password_hash, role, active)
KEY(username)
VALUES ('testadmin', '$2a$12$9k3RGcQHF/8gJdxFLp2FH.hHWnZL5y9Ck7BvUKcJxz7zQeZUfZQe2', 'ADMIN', true);
