package com.example.helpdesk.dto;

import com.example.helpdesk.model.Category;
import com.example.helpdesk.model.Priority;
import com.example.helpdesk.model.Ticket;
import com.example.helpdesk.model.TicketStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class TicketEditForm {
    @NotBlank(message = "Title is required")
    @Size(max = 160, message = "Title cannot exceed 160 characters")
    private String title;

    @NotBlank(message = "Description is required")
    private String description;

    @NotNull(message = "Category is required")
    private Category category;

    @NotNull(message = "Priority is required")
    private Priority priority;

    private TicketStatus status;
    private Long technicianId;
    private String resolutionNotes;

    public TicketEditForm() {}

    public static TicketEditForm fromTicket(Ticket ticket) {
        TicketEditForm form = new TicketEditForm();
        form.setTitle(ticket.getTitle());
        form.setDescription(ticket.getDescription());
        form.setCategory(ticket.getCategory());
        form.setPriority(ticket.getPriority());
        form.setStatus(ticket.getStatus());
        form.setTechnicianId(ticket.getTechnician() != null ? ticket.getTechnician().getId() : null);
        form.setResolutionNotes(ticket.getResolutionNotes());
        return form;
    }

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

    public Long getTechnicianId() { return technicianId; }
    public void setTechnicianId(Long technicianId) { this.technicianId = technicianId; }

    public String getResolutionNotes() { return resolutionNotes; }
    public void setResolutionNotes(String resolutionNotes) { this.resolutionNotes = resolutionNotes; }
}
