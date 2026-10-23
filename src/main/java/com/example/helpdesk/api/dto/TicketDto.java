package com.example.helpdesk.api.dto;

import com.example.helpdesk.model.Category;
import com.example.helpdesk.model.Priority;
import com.example.helpdesk.model.TicketStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public class TicketDto {
    private Long id;
    @NotBlank(message = "Title is required")
    @Size(min = 5, max = 160, message = "Title must be between 5 and 160 characters")
    private String title;
    @NotBlank(message = "Description is required")
    @Size(min = 10, max = 2000, message = "Description must be between 10 and 2000 characters")
    private String description;
    @NotNull(message = "Category is required")
    private Category category;
    @NotNull(message = "Priority is required")
    private Priority priority;
    private TicketStatus status;
    private String resolutionNotes;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String reporterName;
    private String assignedTechnicianName;
    private Long version;
    private OffsetDateTime slaDueAt;
    private boolean slaOverdue;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }
    public TicketStatus getStatus() { return status; }
    public void setStatus(TicketStatus status) { this.status = status; }
    public String getResolutionNotes() { return resolutionNotes; }
    public void setResolutionNotes(String resolutionNotes) { this.resolutionNotes = resolutionNotes; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getReporterName() { return reporterName; }
    public void setReporterName(String reporterName) { this.reporterName = reporterName; }
    public String getAssignedTechnicianName() { return assignedTechnicianName; }
    public void setAssignedTechnicianName(String assignedTechnicianName) { this.assignedTechnicianName = assignedTechnicianName; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public OffsetDateTime getSlaDueAt() { return slaDueAt; }
    public void setSlaDueAt(OffsetDateTime slaDueAt) { this.slaDueAt = slaDueAt; }
    public boolean isSlaOverdue() { return slaOverdue; }
    public void setSlaOverdue(boolean slaOverdue) { this.slaOverdue = slaOverdue; }
}
