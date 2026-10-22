package com.example.helpdesk.api.dto;

import java.time.OffsetDateTime;

public record TicketCommentDto(Long id, String authorName, String content, boolean internalNote,
                               OffsetDateTime createdAt) {}
