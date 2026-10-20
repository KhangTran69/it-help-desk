ALTER TABLE tickets ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE ticket_comments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    internal_note BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_comment_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(id),
    CONSTRAINT fk_comment_author FOREIGN KEY (author_id) REFERENCES users(id)
);
CREATE INDEX idx_comment_ticket_created ON ticket_comments(ticket_id, created_at);
