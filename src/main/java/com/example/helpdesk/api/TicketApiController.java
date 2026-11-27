package com.example.helpdesk.api;

import com.example.helpdesk.api.dto.AttachmentDto;
import com.example.helpdesk.api.dto.PageResponse;
import com.example.helpdesk.api.dto.TicketCommentDto;
import com.example.helpdesk.api.dto.TicketCreateRequest;
import com.example.helpdesk.api.dto.TicketDto;
import com.example.helpdesk.api.dto.TicketUpdateRequest;
import com.example.helpdesk.dto.TicketEditForm;
import com.example.helpdesk.model.Category;
import com.example.helpdesk.model.Priority;
import com.example.helpdesk.model.Ticket;
import com.example.helpdesk.model.TicketAttachment;
import com.example.helpdesk.model.TicketAttachmentMetadata;
import com.example.helpdesk.model.TicketComment;
import com.example.helpdesk.model.TicketStatus;
import com.example.helpdesk.service.TicketAttachmentService;
import com.example.helpdesk.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/tickets")
@Tag(name = "Tickets", description = "Ticket management API endpoints")
public class TicketApiController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final java.util.Set<String> SORT_FIELDS =
            java.util.Set.of("id", "title", "status", "priority", "createdAt", "updatedAt");

    private final TicketService ticketService;
    private final TicketAttachmentService attachments;

    public TicketApiController(TicketService ticketService, TicketAttachmentService attachments) {
        this.ticketService = ticketService;
        this.attachments = attachments;
    }

    @GetMapping
    @Operation(summary = "Search tickets with the same filters as the technician queue")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public ResponseEntity<PageResponse<TicketDto>> getAllTickets(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sort field") @RequestParam(defaultValue = "updatedAt") String sortBy,
            @Parameter(description = "Sort direction") @RequestParam(defaultValue = "desc") String sortDir,
            @Parameter(description = "Free-text search over title, description, and ticket id") @RequestParam(required = false)
                    String q,
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) Priority priority,
            @RequestParam(required = false) Category category,
            @Parameter(description = "Filter by technician id") @RequestParam(required = false) Long technicianId,
            @Parameter(description = "true = only unassigned tickets, false = only assigned")
                    @RequestParam(required = false)
                    Boolean unassigned,
            @Parameter(description = "Only tickets past their SLA due time") @RequestParam(defaultValue = "false")
                    boolean overdue) {

        Pageable pageable = apiPageable(page, size, sortBy, sortDir);
        Page<Ticket> tickets =
                ticketService.searchPage(status, priority, category, technicianId, unassigned, q, false, overdue, pageable);
        return ResponseEntity.ok(toPageResponse(tickets));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get ticket by ID")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public ResponseEntity<TicketDto> getTicketById(@PathVariable Long id) {
        return ResponseEntity.ok(convertToDto(ticketService.get(id)));
    }

    @PostMapping
    @Operation(summary = "Create a new ticket")
    public ResponseEntity<TicketDto> createTicket(
            @Valid @RequestBody TicketCreateRequest request, Authentication authentication) {
        Ticket ticket = new Ticket();
        ticket.setTitle(request.title());
        ticket.setDescription(request.description());
        ticket.setCategory(request.category());
        ticket.setPriority(request.priority());
        Ticket created = ticketService.create(ticket, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(convertToDto(created));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update title, description, category, or priority")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public ResponseEntity<TicketDto> updateTicket(
            @PathVariable Long id, @Valid @RequestBody TicketUpdateRequest request, Authentication authentication) {
        if (request.version() == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.PRECONDITION_REQUIRED,
                    "Ticket version is required. Reload the ticket and include its version when updating.");
        }
        TicketEditForm form = new TicketEditForm();
        form.setTitle(request.title());
        form.setDescription(request.description());
        form.setCategory(request.category());
        form.setPriority(request.priority());
        form.setVersion(request.version());
        Ticket updated = ticketService.updateTicket(id, form, authentication.getName());
        return ResponseEntity.ok(convertToDto(updated));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update ticket status; transitions follow the ticket state machine")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public ResponseEntity<TicketDto> updateStatus(
            @PathVariable Long id,
            @RequestParam String status,
            @RequestParam(required = false) String resolutionNotes,
            @RequestParam Long version,
            Authentication authentication) {
        TicketStatus nextStatus;
        try {
            nextStatus = TicketStatus.valueOf(status.toUpperCase(java.util.Locale.ROOT));
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Status must be one of OPEN, IN_PROGRESS, RESOLVED, or CLOSED");
        }
        ticketService.updateStatus(id, nextStatus, resolutionNotes, authentication.getName(), version);
        return ResponseEntity.ok(convertToDto(ticketService.get(id)));
    }

    @PatchMapping("/{id}/assign")
    @Operation(summary = "Assign ticket to technician")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public ResponseEntity<TicketDto> assignTicket(
            @PathVariable Long id,
            @RequestParam Long technicianId,
            @RequestParam Long version,
            Authentication authentication) {
        ticketService.assign(id, technicianId, authentication.getName(), version);
        return ResponseEntity.ok(convertToDto(ticketService.get(id)));
    }

    @GetMapping("/{id}/attachments")
    @Operation(summary = "List ticket attachments")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public ResponseEntity<List<AttachmentDto>> listAttachments(@PathVariable Long id) {
        ticketService.get(id);
        return ResponseEntity.ok(attachments.listForTicket(id).stream()
                .map(this::toAttachmentDto)
                .toList());
    }

    @PostMapping("/{id}/attachments")
    @Operation(summary = "Upload a ticket attachment (5 MB max, document/image/archive types)")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public ResponseEntity<AttachmentDto> uploadAttachment(
            @PathVariable Long id, @RequestParam("file") MultipartFile file, Authentication authentication) {
        TicketAttachment saved = attachments.store(id, file, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(toAttachmentDto(TicketAttachmentMetadata.from(saved)));
    }

    @GetMapping("/{id}/attachments/{attachmentId}/download")
    @Operation(summary = "Download a ticket attachment")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public ResponseEntity<Resource> downloadAttachment(
            @PathVariable Long id, @PathVariable Long attachmentId, Authentication authentication) {
        TicketAttachment attachment = attachments.readableContent(id, attachmentId, authentication.getName());
        String contentDisposition = ContentDisposition.builder("attachment")
                .filename(attachment.getFilename())
                .build()
                .toString();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .contentType(MediaType.parseMediaType(attachment.getContentType()))
                .contentLength(attachment.getSizeBytes())
                .body(new ByteArrayResource(attachment.getContent()));
    }

    @GetMapping("/my/{id}/attachments")
    @Operation(summary = "List attachments on one of the current user's tickets")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<List<AttachmentDto>> listMyAttachments(@PathVariable Long id, Authentication authentication) {
        ticketService.getMyTicket(id, authentication.getName());
        return ResponseEntity.ok(attachments.listForTicket(id).stream()
                .map(this::toAttachmentDto)
                .toList());
    }

    private AttachmentDto toAttachmentDto(TicketAttachmentMetadata attachment) {
        return new AttachmentDto(
                attachment.id(),
                attachment.ticketId(),
                attachment.filename(),
                attachment.contentType(),
                attachment.sizeBytes(),
                attachment.uploadedByUsername(),
                attachment.uploadedAt().atOffset(ZoneOffset.UTC));
    }

    @GetMapping("/my/{id}")
    @Operation(summary = "Get one of the current user's tickets")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<TicketDto> getMyTicket(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(convertToDto(ticketService.getMyTicket(id, authentication.getName())));
    }

    @GetMapping("/{id}/comments")
    @Operation(summary = "List ticket comments visible to the current user")
    public ResponseEntity<PageResponse<TicketCommentDto>> getComments(
            @PathVariable Long id,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (max 100)") @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        Page<TicketComment> visible = isTechnician(authentication)
                ? ticketService.allComments(id, commentPageable(page, size))
                : ticketService.publicComments(id, authentication.getName(), commentPageable(page, size));
        List<TicketCommentDto> dtos = visible.getContent().stream().map(this::toCommentDto).toList();
        return ResponseEntity.ok(new PageResponse<>(
                dtos, visible.getNumber(), visible.getTotalPages(), visible.getTotalElements(), visible.getSize()));
    }

    private Pageable commentPageable(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid pagination parameters");
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt"));
    }

    @PostMapping("/{id}/comments")
    @Operation(summary = "Add a public comment or technician-only internal note")
    public ResponseEntity<TicketCommentDto> addComment(
            @PathVariable Long id, @Valid @RequestBody CommentRequest request, Authentication authentication) {
        TicketComment comment;
        if (isTechnician(authentication)) {
            comment = ticketService.addTechnicianComment(
                    id, authentication.getName(), request.content(), request.internalNote());
        } else {
            if (request.internalNote()) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "Employees cannot add internal notes");
            }
            comment = ticketService.addEmployeeComment(id, authentication.getName(), request.content());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(toCommentDto(comment));
    }

    private boolean isTechnician(Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(a -> "ROLE_TECHNICIAN".equals(a.getAuthority()));
    }

    private TicketCommentDto toCommentDto(TicketComment comment) {
        return new TicketCommentDto(
                comment.getId(),
                comment.getAuthor().getFullName(),
                comment.getContent(),
                comment.isInternalNote(),
                comment.getCreatedAt().atOffset(ZoneOffset.UTC));
    }

    public record CommentRequest(@NotBlank @Size(max = 5000) String content, boolean internalNote) {}

    @GetMapping("/my")
    @Operation(summary = "Get current user's tickets")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<PageResponse<TicketDto>> getMyTickets(
            Authentication authentication,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size) {

        String username = authentication.getName();
        Pageable pageable = apiPageable(page, size, "updatedAt", "desc");
        Page<Ticket> tickets = ticketService.getTicketsByReporterPaginated(username, pageable);
        return ResponseEntity.ok(toPageResponse(tickets));
    }

    private PageResponse<TicketDto> toPageResponse(Page<Ticket> tickets) {
        List<TicketDto> ticketDtos =
                tickets.getContent().stream().map(this::convertToDto).collect(Collectors.toList());
        return new PageResponse<>(
                ticketDtos,
                tickets.getNumber(),
                tickets.getTotalPages(),
                tickets.getTotalElements(),
                tickets.getSize());
    }

    private Pageable apiPageable(int page, int size, String sortBy, String sortDir) {
        if (page < 0
                || size < 1
                || size > MAX_PAGE_SIZE
                || !SORT_FIELDS.contains(sortBy)
                || !("asc".equalsIgnoreCase(sortDir) || "desc".equalsIgnoreCase(sortDir))) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid pagination or sorting parameters");
        }
        Sort sort = "asc".equalsIgnoreCase(sortDir)
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        return PageRequest.of(page, size, sort);
    }

    private TicketDto convertToDto(Ticket ticket) {
        TicketDto dto = new TicketDto();
        dto.setId(ticket.getId());
        dto.setTitle(ticket.getTitle());
        dto.setDescription(ticket.getDescription());
        dto.setCategory(ticket.getCategory());
        dto.setPriority(ticket.getPriority());
        dto.setStatus(ticket.getStatus());
        dto.setResolutionNotes(ticket.getResolutionNotes());
        dto.setCreatedAt(ticket.getCreatedAt().atOffset(ZoneOffset.UTC));
        dto.setUpdatedAt(ticket.getUpdatedAt().atOffset(ZoneOffset.UTC));
        dto.setVersion(ticket.getVersion());
        dto.setSlaDueAt(ticket.getSlaDueAt() == null ? null : ticket.getSlaDueAt().atOffset(ZoneOffset.UTC));
        dto.setSlaOverdue(ticket.isSlaOverdue());
        if (ticket.getReporter() != null) {
            dto.setReporterName(ticket.getReporter().getFullName());
        }
        if (ticket.getTechnician() != null) {
            dto.setAssignedTechnicianName(ticket.getTechnician().getFullName());
        }
        return dto;
    }
}
