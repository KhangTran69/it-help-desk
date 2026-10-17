package com.example.helpdesk.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "tickets")
public class Ticket {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    private Category category;

    @Enumerated(EnumType.STRING)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    private TicketStatus status;

    @ManyToOne(optional = false)
    @JoinColumn(name = "reporter_id")
    private User reporter;

    @ManyToOne
    @JoinColumn(name = "technician_id")
    private User technician;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Ticket() {}

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String v) { title = v; }

    public String getDescription() { return description; }
    public void setDescription(String v) { description = v; }

    public Category getCategory() { return category; }
    public void setCategory(Category v) { category = v; }

    public Priority getPriority() { return priority; }
    public void setPriority(Priority v) { priority = v; }

    public TicketStatus getStatus() { return status; }
    public void setStatus(TicketStatus v) {
        status = v;
        updatedAt = LocalDateTime.now();
    }

    public User getReporter() { return reporter; }
    public void setReporter(User v) { reporter = v; }

    public User getTechnician() { return technician; }
    public void setTechnician(User v) { technician = v; }

    public String getResolutionNotes() { return resolutionNotes; }
    public void setResolutionNotes(String v) { resolutionNotes = v; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { createdAt = v; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v) { updatedAt = v; }
}
