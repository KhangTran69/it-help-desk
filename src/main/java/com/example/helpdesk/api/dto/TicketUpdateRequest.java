package com.example.helpdesk.api.dto;

import com.example.helpdesk.model.Category;
import com.example.helpdesk.model.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for PUT /api/tickets/{id}. The version field is intentionally not @NotNull: a
 * missing version is rejected with 428 Precondition Required so clients learn to send it.
 */
public record TicketUpdateRequest(
        @NotBlank(message = "Title is required")
                @Size(min = 5, max = 160, message = "Title must be between 5 and 160 characters")
                String title,
        @NotBlank(message = "Description is required")
                @Size(min = 10, max = 2000, message = "Description must be between 10 and 2000 characters")
                String description,
        @NotNull(message = "Category is required") Category category,
        @NotNull(message = "Priority is required") Priority priority,
        Long version) {}
