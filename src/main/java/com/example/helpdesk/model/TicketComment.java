package com.example.helpdesk.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "ticket_comments")
public class TicketComment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;

    @ManyToOne(optional = false)
    @JoinColumn(name = "author_id")
    private User author;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "internal_note", nullable = false)
    private boolean internalNote;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now(ZoneOffset.UTC);

    @Column(name = "last_edited_at")
    private LocalDateTime lastEditedAt;

    protected TicketComment() {}

    public TicketComment(Ticket ticket, User author, String content, boolean internalNote) {
        this.ticket = ticket;
        this.author = author;
        this.content = content;
        this.internalNote = internalNote;
        this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() {
        return id;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public User getAuthor() {
        return author;
    }

    public String getContent() {
        return content;
    }

    public boolean isInternalNote() {
        return internalNote;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getLastEditedAt() {
        return lastEditedAt;
    }

    public void setContent(String value) {
        this.content = value;
    }

    public void setCreatedAtForTesting(LocalDateTime when) {
        this.createdAt = when;
    }

    public void markEdited(LocalDateTime when) {
        this.lastEditedAt = when;
    }

    /** Authors may edit their comment for fifteen minutes after posting. */
    public boolean editableBy(User user) {
        if (user == null || author == null || !user.getUsername().equals(author.getUsername())) {
            return false;
        }
        return LocalDateTime.now(ZoneOffset.UTC).isBefore(createdAt.plusMinutes(15));
    }
}
