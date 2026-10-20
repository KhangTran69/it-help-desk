package com.example.helpdesk.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "ticket_history")
public class TicketHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;

    @ManyToOne(optional = false)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Column(nullable = false)
    private String action;

    private String details;

    @Column(name = "field_changed", length = 80)
    private String fieldChanged;

    @Column(name = "previous_value", columnDefinition = "TEXT")
    private String previousValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now(ZoneOffset.UTC);

    public TicketHistory() {}

    public TicketHistory(Ticket t, User a, String x, String d) {
        this.ticket = t;
        this.actor = a;
        this.action = x;
        this.details = d;
        this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public TicketHistory(Ticket t, User a, String x, String d, String fieldChanged, String previousValue, String newValue) {
        this.ticket = t;
        this.actor = a;
        this.action = x;
        this.details = d;
        this.fieldChanged = fieldChanged;
        this.previousValue = previousValue;
        this.newValue = newValue;
        this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() { return id; }
    public Ticket getTicket() { return ticket; }
    public User getActor() { return actor; }
    public String getAction() { return action; }
    public String getDetails() { return details; }
    public String getFieldChanged() { return fieldChanged; }
    public String getPreviousValue() { return previousValue; }
    public String getNewValue() { return newValue; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
