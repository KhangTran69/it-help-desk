package com.example.helpdesk.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "tickets")
public class Ticket {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

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
    private LocalDateTime createdAt = LocalDateTime.now(ZoneOffset.UTC);

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now(ZoneOffset.UTC);

    @Column(name = "sla_target_seconds", nullable = false)
    private long slaTargetSeconds;

    @Column(name = "sla_elapsed_seconds", nullable = false)
    private long slaElapsedSeconds;

    @Column(name = "sla_started_at")
    private LocalDateTime slaStartedAt;

    @Column(name = "sla_due_at")
    private LocalDateTime slaDueAt;

    public Ticket() {}

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

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
        updatedAt = LocalDateTime.now(ZoneOffset.UTC);
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

    public long getSlaTargetSeconds() { return slaTargetSeconds; }
    public void setSlaTargetSeconds(long value) { slaTargetSeconds = value; }
    public long getSlaElapsedSeconds() { return slaElapsedSeconds; }
    public void setSlaElapsedSeconds(long value) { slaElapsedSeconds = value; }
    public LocalDateTime getSlaStartedAt() { return slaStartedAt; }
    public void setSlaStartedAt(LocalDateTime value) { slaStartedAt = value; }
    public LocalDateTime getSlaDueAt() { return slaDueAt; }
    public void setSlaDueAt(LocalDateTime value) { slaDueAt = value; }
    public boolean isSlaOverdue() {
        long elapsed = slaElapsedSeconds;
        if (slaStartedAt != null) elapsed += java.time.Duration.between(slaStartedAt, LocalDateTime.now(ZoneOffset.UTC)).getSeconds();
        return slaTargetSeconds > 0 && elapsed >= slaTargetSeconds;
    }
}
