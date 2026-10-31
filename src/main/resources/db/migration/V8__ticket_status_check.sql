ALTER TABLE tickets
    ADD CONSTRAINT chk_tickets_status
    CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'));
