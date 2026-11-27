ALTER TABLE users ADD COLUMN email VARCHAR(160) NULL;
CREATE UNIQUE INDEX idx_users_email ON users(email);
ALTER TABLE tickets ADD COLUMN sla_breach_notified_at TIMESTAMP NULL;
