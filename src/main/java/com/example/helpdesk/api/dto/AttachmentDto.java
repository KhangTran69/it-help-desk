package com.example.helpdesk.api.dto;

import java.time.OffsetDateTime;

/** Metadata for a stored ticket attachment (content is streamed, never embedded). */
public record AttachmentDto(
        Long id,
        Long ticketId,
        String filename,
        String contentType,
        long sizeBytes,
        String uploadedBy,
        OffsetDateTime uploadedAt) {
}
