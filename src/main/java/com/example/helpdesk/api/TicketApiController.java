package com.example.helpdesk.api;

import com.example.helpdesk.api.dto.PageResponse;
import com.example.helpdesk.api.dto.TicketDto;
import com.example.helpdesk.model.Ticket;
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
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tickets")
@Tag(name = "Tickets", description = "Ticket management API endpoints")
public class TicketApiController {

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

        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
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
    public ResponseEntity<TicketDto> updateTicket(@PathVariable Long id, @Valid @RequestBody TicketDto ticketDto) {
        Ticket ticket = convertToEntity(ticketDto);
        Ticket updated = ticketService.updateTicket(id, ticket);
        return ResponseEntity.ok(convertToDto(updated));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update ticket status")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public ResponseEntity<TicketDto> updateStatus(
            @PathVariable Long id,
            @RequestParam String status,
            @RequestParam(required = false) String resolutionNotes) {
        Ticket updated = ticketService.updateStatus(id, status, resolutionNotes);
        return ResponseEntity.ok(convertToDto(updated));
    }

    @PatchMapping("/{id}/assign")
    @Operation(summary = "Assign ticket to technician")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public ResponseEntity<TicketDto> assignTicket(@PathVariable Long id, @RequestParam Long technicianId) {
        Ticket updated = ticketService.assignTicket(id, technicianId);
        return ResponseEntity.ok(convertToDto(updated));
    }

    @GetMapping("/my")
    @Operation(summary = "Get current user's tickets")
    public ResponseEntity<PageResponse<TicketDto>> getMyTickets(
            Authentication authentication,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size) {

        String username = authentication.getName();
        Pageable pageable = PageRequest.of(page, size, Sort.by("updatedAt").descending());
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

    private TicketDto convertToDto(Ticket ticket) {
        TicketDto dto = new TicketDto();
        dto.setId(ticket.getId());
        dto.setTitle(ticket.getTitle());
        dto.setDescription(ticket.getDescription());
        dto.setCategory(ticket.getCategory());
        dto.setPriority(ticket.getPriority());
        dto.setStatus(ticket.getStatus());
        dto.setResolutionNotes(ticket.getResolutionNotes());
        dto.setCreatedAt(ticket.getCreatedAt());
        dto.setUpdatedAt(ticket.getUpdatedAt());
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
