ALTER TABLE tickets ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE ticket_comments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    ticket_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    internal_note BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ticket_comments_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(id),
    CONSTRAINT fk_ticket_comments_author FOREIGN KEY (author_id) REFERENCES users(id),
    INDEX idx_ticket_comments_ticket_created (ticket_id, created_at),
    INDEX idx_ticket_comments_internal (internal_note)
);
