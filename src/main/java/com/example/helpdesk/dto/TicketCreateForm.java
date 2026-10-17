package com.example.helpdesk.dto;

import com.example.helpdesk.model.Category;
import com.example.helpdesk.model.Priority;
import com.example.helpdesk.model.Ticket;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class TicketCreateForm {
    @NotBlank(message = "Title is required")
    @Size(max = 160, message = "Title cannot exceed 160 characters")
    private String title;

    @NotBlank(message = "Description is required")
    private String description;

    @NotNull(message = "Category is required")
    private Category category;

    @NotNull(message = "Priority is required")
    private Priority priority = Priority.MEDIUM;

    public TicketCreateForm() {}

    public Ticket toTicket() {
        Ticket t = new Ticket();
        t.setTitle(this.title);
        t.setDescription(this.description);
        t.setCategory(this.category);
        t.setPriority(this.priority != null ? this.priority : Priority.MEDIUM);
        return t;
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }

    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }
}
