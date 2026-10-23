ALTER TABLE assets ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE tickets ADD COLUMN sla_target_seconds BIGINT NOT NULL DEFAULT 0;
ALTER TABLE tickets ADD COLUMN sla_elapsed_seconds BIGINT NOT NULL DEFAULT 0;
ALTER TABLE tickets ADD COLUMN sla_started_at DATETIME(6) NULL;
ALTER TABLE tickets ADD COLUMN sla_due_at DATETIME(6) NULL;
CREATE INDEX idx_tickets_sla_due_status ON tickets(sla_due_at, status);

-- Existing records begin a fresh SLA clock at migration time, using their current priority target.
UPDATE tickets
SET sla_target_seconds = CASE priority
    WHEN 'HIGH' THEN 86400
    WHEN 'LOW' THEN 1209600
    ELSE 259200
END,
    sla_started_at = CASE WHEN status IN ('RESOLVED', 'CLOSED') THEN NULL ELSE UTC_TIMESTAMP(6) END,
    sla_due_at = CASE WHEN status IN ('RESOLVED', 'CLOSED') THEN NULL
        ELSE DATE_ADD(UTC_TIMESTAMP(6), INTERVAL (CASE priority WHEN 'HIGH' THEN 1 WHEN 'LOW' THEN 14 ELSE 3 END) DAY) END;
