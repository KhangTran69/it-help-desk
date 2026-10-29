package com.example.helpdesk.service;

import com.example.helpdesk.exception.ResourceNotFoundException;
import com.example.helpdesk.model.Ticket;
import com.example.helpdesk.model.TicketAttachment;
import com.example.helpdesk.model.User;
import com.example.helpdesk.repo.TicketAttachmentRepository;
import com.example.helpdesk.repo.TicketRepository;
import com.example.helpdesk.repo.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Stores and validates ticket attachments.
 *
 * Files live in the database so backups (mysqldump) capture them with the
 * ticket. Uploads are capped and restricted to document/image/archive
 * extensions to keep scripts and executables out of the system.
 */
@Service
public class TicketAttachmentService {

    public static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;
    static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "png", "jpg", "jpeg", "gif", "webp", "txt", "csv", "log", "md",
            "doc", "docx", "xls", "xlsx", "ppt", "pptx", "zip");

    private final TicketAttachmentRepository attachments;
    private final TicketRepository tickets;
    private final UserRepository users;

    public TicketAttachmentService(TicketAttachmentRepository attachments, TicketRepository tickets,
                                   UserRepository users) {
        this.attachments = attachments;
        this.tickets = tickets;
        this.users = users;
    }

    @Transactional
    public TicketAttachment store(Long ticketId, MultipartFile file, String username) {
        Ticket ticket = tickets.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
        User uploader = users.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Choose a file to upload");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException("Attachments are limited to 5 MB");
        }
        String filename = safeFilename(file.getOriginalFilename());
        String extension = extensionOf(filename);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("File type ." + extension + " is not allowed; permitted: "
                    + String.join(", ", ALLOWED_EXTENSIONS.stream().sorted().toList()));
        }
        try {
            TicketAttachment attachment = new TicketAttachment(ticket, filename,
                    contentType(file.getContentType()), file.getSize(), uploader, file.getBytes());
            return attachments.save(attachment);
        } catch (IOException error) {
            throw new IllegalStateException("Upload failed; try again", error);
        }
    }

    @Transactional(readOnly = true)
    public List<TicketAttachment> listForTicket(Long ticketId) {
        return attachments.findByTicketIdOrderByUploadedAtAsc(ticketId);
    }

    /** Returns the attachment when the viewer is a technician or the ticket reporter. */
    @Transactional(readOnly = true)
    public TicketAttachment readableContent(Long ticketId, Long attachmentId, String username) {
        TicketAttachment attachment = attachments.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found"));
        User viewer = users.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        boolean reporter = attachment.getTicket().getReporter() != null
                && sameUser(attachment.getTicket().getReporter(), viewer);
        if (!reporter && viewer.getRole() != com.example.helpdesk.model.Role.TECHNICIAN) {
            throw new ResourceNotFoundException("Attachment not found");
        }
        if (attachment.getTicket().getId() != null && !attachment.getTicket().getId().equals(ticketId)) {
            throw new ResourceNotFoundException("Attachment not found");
        }
        return attachment;
    }

    /** Kept as a small named helper instead of a static import. */
    private boolean sameUser(User ticketUser, User viewer) {
        return ticketUser.getUsername() != null && ticketUser.getUsername().equals(viewer.getUsername());
    }

    private String safeFilename(String original) {
        if (original == null || original.isBlank()) {
            throw new IllegalArgumentException("The file has no name");
        }
        String cleaned = original.replace("\\", "/");
        cleaned = cleaned.substring(cleaned.lastIndexOf('/') + 1).trim();
        if (cleaned.length() > 200) {
            cleaned = cleaned.substring(cleaned.length() - 200);
        }
        if (cleaned.isBlank() || ".".equals(cleaned) || "..".equals(cleaned)) {
            throw new IllegalArgumentException("The file has no usable name");
        }
        return cleaned;
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            throw new IllegalArgumentException("The file must have an extension");
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String contentType(String provided) {
        return provided == null || provided.isBlank() ? "application/octet-stream" : provided;
    }
}
