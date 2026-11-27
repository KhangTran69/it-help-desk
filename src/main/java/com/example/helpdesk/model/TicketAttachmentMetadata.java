package com.example.helpdesk.model;

import java.time.LocalDateTime;

public record TicketAttachmentMetadata(
        Long id,
        Long ticketId,
        String filename,
        String contentType,
        long sizeBytes,
        String uploadedByUsername,
        String uploadedByFullName,
        LocalDateTime uploadedAt) {

    public static TicketAttachmentMetadata from(TicketAttachment attachment) {
        User uploader = attachment.getUploadedBy();
        return new TicketAttachmentMetadata(
                attachment.getId(),
                attachment.getTicket().getId(),
                attachment.getFilename(),
                attachment.getContentType(),
                attachment.getSizeBytes(),
                uploader.getUsername(),
                uploader.getFullName(),
                attachment.getUploadedAt());
    }
}
