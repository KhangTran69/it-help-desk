-- Add indexes for improved query performance
CREATE INDEX idx_ticket_reporter ON tickets(reporter_id);
CREATE INDEX idx_ticket_technician ON tickets(technician_id);
CREATE INDEX idx_ticket_updated_at ON tickets(updated_at DESC);
CREATE INDEX idx_ticket_created_at ON tickets(created_at DESC);
CREATE INDEX idx_ticket_category ON tickets(category);
CREATE INDEX idx_asset_category ON assets(category);
CREATE INDEX idx_asset_updated_at ON assets(updated_at DESC);
CREATE INDEX idx_history_ticket ON ticket_history(ticket_id);
CREATE INDEX idx_history_actor ON ticket_history(actor_id);
CREATE INDEX idx_history_created_at ON ticket_history(created_at DESC);
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_role ON users(role);
