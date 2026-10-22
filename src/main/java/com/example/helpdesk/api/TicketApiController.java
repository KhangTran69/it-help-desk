package com.example.helpdesk.api;

import com.example.helpdesk.api.dto.PageResponse;
import com.example.helpdesk.api.dto.TicketDto;
import com.example.helpdesk.api.dto.TicketCommentDto;
import com.example.helpdesk.dto.TicketEditForm;
import com.example.helpdesk.model.Ticket;
import com.example.helpdesk.model.TicketComment;
import com.example.helpdesk.model.TicketStatus;
import com.example.helpdesk.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.time.ZoneOffset;
import java.util.stream.Collectors;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/tickets")
@Tag(name = "Tickets", description = "Ticket management API endpoints")
public class TicketApiController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final java.util.Set<String> SORT_FIELDS = java.util.Set.of("id", "title", "status", "priority", "createdAt", "updatedAt");

    private final TicketService ticketService;

    public TicketApiController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping
    @Operation(summary = "Get all tickets with pagination")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public ResponseEntity<PageResponse<TicketDto>> getAllTickets(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sort field") @RequestParam(defaultValue = "updatedAt") String sortBy,
            @Parameter(description = "Sort direction") @RequestParam(defaultValue = "desc") String sortDir) {

        Pageable pageable = apiPageable(page, size, sortBy, sortDir);
        Page<Ticket> tickets = ticketService.getAllTicketsPaginated(pageable);

        List<TicketDto> ticketDtos = tickets.getContent().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());

        PageResponse<TicketDto> response = new PageResponse<>(
                ticketDtos,
                tickets.getNumber(),
                tickets.getTotalPages(),
                tickets.getTotalElements(),
                tickets.getSize()
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get ticket by ID")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public ResponseEntity<TicketDto> getTicketById(@PathVariable Long id) {
        Ticket ticket = ticketService.getTicketById(id);
        return ResponseEntity.ok(convertToDto(ticket));
    }

    @PostMapping
    @Operation(summary = "Create a new ticket")
    public ResponseEntity<TicketDto> createTicket(@Valid @RequestBody TicketDto ticketDto, Authentication authentication) {
        String username = authentication.getName();
        Ticket ticket = convertToEntity(ticketDto);
        Ticket created = ticketService.createTicket(ticket, username);
        return ResponseEntity.status(HttpStatus.CREATED).body(convertToDto(created));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing ticket")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public ResponseEntity<TicketDto> updateTicket(@PathVariable Long id, @Valid @RequestBody TicketDto ticketDto,
                                                  Authentication authentication) {
        if (ticketDto.getVersion() == null) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED,
                    "Ticket version is required. Reload the ticket and include its version when updating.");
        }
        TicketEditForm form = new TicketEditForm();
        form.setTitle(ticketDto.getTitle());
        form.setDescription(ticketDto.getDescription());
        form.setCategory(ticketDto.getCategory());
        form.setPriority(ticketDto.getPriority());
        form.setVersion(ticketDto.getVersion());
        Ticket updated = ticketService.updateTicket(id, form, authentication.getName());
        return ResponseEntity.ok(convertToDto(updated));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update ticket status")
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
        Ticket updated = ticketService.getTicketById(id);
        return ResponseEntity.ok(convertToDto(updated));
    }

    @PatchMapping("/{id}/assign")
    @Operation(summary = "Assign ticket to technician")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public ResponseEntity<TicketDto> assignTicket(@PathVariable Long id, @RequestParam Long technicianId,
                                                   @RequestParam Long version,
                                                   Authentication authentication) {
        ticketService.assign(id, technicianId, authentication.getName(), version);
        Ticket updated = ticketService.getTicketById(id);
        return ResponseEntity.ok(convertToDto(updated));
    }

    @GetMapping("/my/{id}")
    @Operation(summary = "Get one of the current user's tickets")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<TicketDto> getMyTicket(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(convertToDto(ticketService.getMyTicket(id, authentication.getName())));
    }

    @GetMapping("/{id}/comments")
    @Operation(summary = "List ticket comments visible to the current user")
    public ResponseEntity<List<TicketCommentDto>> getComments(@PathVariable Long id, Authentication authentication) {
        List<TicketComment> visible = isTechnician(authentication)
                ? ticketService.allComments(id)
                : ticketService.publicComments(id, authentication.getName());
        return ResponseEntity.ok(visible.stream().map(this::toCommentDto).toList());
    }

    @PostMapping("/{id}/comments")
    @Operation(summary = "Add a public comment or technician-only internal note")
    public ResponseEntity<TicketCommentDto> addComment(@PathVariable Long id,
                                                        @Valid @RequestBody CommentRequest request,
                                                        Authentication authentication) {
        TicketComment comment;
        if (isTechnician(authentication)) {
            comment = ticketService.addTechnicianComment(id, authentication.getName(), request.content(), request.internalNote());
        } else {
            if (request.internalNote()) {
                throw new org.springframework.security.access.AccessDeniedException("Employees cannot add internal notes");
            }
            comment = ticketService.addEmployeeComment(id, authentication.getName(), request.content());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(toCommentDto(comment));
    }

    private boolean isTechnician(Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(a -> "ROLE_TECHNICIAN".equals(a.getAuthority()));
    }

    private TicketCommentDto toCommentDto(TicketComment comment) {
        return new TicketCommentDto(comment.getId(), comment.getAuthor().getFullName(), comment.getContent(),
                comment.isInternalNote(), comment.getCreatedAt().atOffset(ZoneOffset.UTC));
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

        List<TicketDto> ticketDtos = tickets.getContent().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());

        PageResponse<TicketDto> response = new PageResponse<>(
                ticketDtos,
                tickets.getNumber(),
                tickets.getTotalPages(),
                tickets.getTotalElements(),
                tickets.getSize()
        );

        return ResponseEntity.ok(response);
    }

    private Pageable apiPageable(int page, int size, String sortBy, String sortDir) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE || !SORT_FIELDS.contains(sortBy)
                || !("asc".equalsIgnoreCase(sortDir) || "desc".equalsIgnoreCase(sortDir))) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid pagination or sorting parameters");
        }
        Sort sort = "asc".equalsIgnoreCase(sortDir) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
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
        if (ticket.getReporter() != null) {
            dto.setReporterName(ticket.getReporter().getFullName());
        }
        if (ticket.getTechnician() != null) {
            dto.setAssignedTechnicianName(ticket.getTechnician().getFullName());
        }
        return dto;
    }

    private Ticket convertToEntity(TicketDto dto) {
        Ticket ticket = new Ticket();
        ticket.setTitle(dto.getTitle());
        ticket.setDescription(dto.getDescription());
        ticket.setCategory(dto.getCategory());
        ticket.setPriority(dto.getPriority());
        ticket.setResolutionNotes(dto.getResolutionNotes());
        return ticket;
    }
}
