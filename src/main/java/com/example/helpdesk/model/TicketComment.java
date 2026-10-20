package com.example.helpdesk.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "ticket_comments")
public class TicketComment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "ticket_id")
    private Ticket ticket;
    @ManyToOne(optional = false) @JoinColumn(name = "author_id")
    private User author;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;
    @Column(name = "internal_note", nullable = false)
    private boolean internalNote;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now(ZoneOffset.UTC);

    protected TicketComment() {}
    public TicketComment(Ticket ticket, User author, String content, boolean internalNote) {
        this.ticket = ticket;
        this.author = author;
        this.content = content;
        this.internalNote = internalNote;
        this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }
    public Long getId() { return id; }
    public Ticket getTicket() { return ticket; }
    public User getAuthor() { return author; }
    public String getContent() { return content; }
    public boolean isInternalNote() { return internalNote; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
