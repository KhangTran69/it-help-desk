ALTER TABLE ticket_history ADD COLUMN field_changed VARCHAR(80) NULL;
ALTER TABLE ticket_history ADD COLUMN previous_value TEXT NULL;
ALTER TABLE ticket_history ADD COLUMN new_value TEXT NULL;
