package com.example.helpdesk.service;

import com.example.helpdesk.dto.TicketEditForm;
import com.example.helpdesk.exception.ResourceNotFoundException;
import com.example.helpdesk.exception.TicketEditConflictException;
import com.example.helpdesk.model.*;
import com.example.helpdesk.repo.TicketHistoryRepository;
import com.example.helpdesk.repo.TicketCommentRepository;
import com.example.helpdesk.repo.TicketRepository;
import com.example.helpdesk.repo.UserRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

@Service
public class TicketService {
    private final TicketRepository tickets;
    private final UserRepository users;
    private final TicketHistoryRepository history;
    private final TicketCommentRepository comments;

    @Autowired
    public TicketService(TicketRepository tickets, UserRepository users, TicketHistoryRepository history, TicketCommentRepository comments) {
        this.tickets = tickets;
        this.users = users;
        this.history = history;
        this.comments = comments;
    }

    public TicketService(TicketRepository tickets, UserRepository users, TicketHistoryRepository history) {
        this(tickets, users, history, null);
    }

    @Transactional(readOnly = true)
    public Page<Ticket> searchPage(TicketStatus status, Priority priority, Category category,
                                   Long technicianId, Boolean unassigned, String query, Pageable pageable) {
        User technician = technicianId == null ? null : users.findById(technicianId)
                .orElseThrow(() -> new ResourceNotFoundException("Technician not found"));
        if (technician != null && technician.getRole() != Role.TECHNICIAN) throw new IllegalArgumentException("Assignee must be a technician");
        return tickets.pageTickets(null, status, priority, category, technician, unassigned,
                query == null || query.isBlank() ? null : query.trim(), pageable);
    }

    @Transactional(readOnly = true)
    public List<Ticket> search(TicketStatus status, Priority priority, Category category,
                               Long technicianId, Boolean unassigned, String query) {
        User technician = technicianId == null ? null : users.findById(technicianId)
                .orElseThrow(() -> new ResourceNotFoundException("Technician not found"));
        if (technician != null && technician.getRole() != Role.TECHNICIAN) {
            throw new IllegalArgumentException("Assignee must be a technician");
        }
        return tickets.filterTickets(null, status, priority, category, technician, unassigned,
                query == null || query.isBlank() ? null : query.trim());
    }

    public List<Ticket> visible(User user, String query, Priority priority) {
        if (user.getRole() != Role.TECHNICIAN) {
            throw new AccessDeniedException("Only technicians can view the ticket queue");
        }
        return search(null, priority, null, null, null, query);
    }

    @Transactional
    @CacheEvict(cacheNames = {"ticketCounts", "dashboardCounters"}, allEntries = true)
    public Ticket create(Ticket ticket, String username) {
        if (ticket.getPriority() == null) ticket.setPriority(Priority.MEDIUM);
        validateRequired(ticket);
        User reporter = users.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        ticket.setTitle(ticket.getTitle().trim());
        ticket.setDescription(ticket.getDescription().trim());
        ticket.setReporter(reporter);
        ticket.setStatus(TicketStatus.OPEN);
        Ticket saved = tickets.save(ticket);
        history.save(new TicketHistory(saved, reporter, "CREATED", "Ticket submitted"));
        return saved;
    }

    private void validateRequired(Ticket ticket) {
        if (ticket.getTitle() == null || ticket.getTitle().isBlank()) throw new IllegalArgumentException("Title is required");
        if (ticket.getTitle().length() > 160) throw new IllegalArgumentException("Title cannot exceed 160 characters");
        if (ticket.getDescription() == null || ticket.getDescription().isBlank()) throw new IllegalArgumentException("Description is required");
        if (ticket.getCategory() == null) throw new IllegalArgumentException("Category is required");
        if (ticket.getPriority() == null) throw new IllegalArgumentException("Priority is required");
    }

    @Transactional(readOnly = true)
    public Ticket get(Long id) {
        return tickets.findById(id).orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<Ticket> findMyTickets(String username) {
        return tickets.findByReporterUsernameOrderByUpdatedAtDesc(username);
    }

    @Transactional(readOnly = true)
    public Page<Ticket> findMyTickets(String username, Pageable pageable) {
        return tickets.findByReporterUsername(username, pageable);
    }

    /** Returns 404 for both missing and non-owned tickets to avoid disclosing another user's records. */
    @Transactional(readOnly = true)
    public Ticket getMyTicket(Long id, String username) {
        return tickets.findByIdAndReporterUsername(id, username)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
    }

    @Transactional(readOnly = true)
    public List<TicketHistory> progressHistory(Long id, String username) {
        Ticket ticket = getMyTicket(id, username);
        return history.findByTicketOrderByCreatedAtAsc(ticket).stream()
                .filter(item -> "STATUS_CHANGED".equals(item.getAction()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TicketHistory> history(Long id) {
        return history.findByTicketOrderByCreatedAtDesc(get(id));
    }

    @Transactional(readOnly = true)
    public List<TicketComment> publicComments(Long id, String username) {
        return comments.findByTicketAndInternalNoteFalseOrderByCreatedAtAsc(getMyTicket(id, username));
    }

    @Transactional(readOnly = true)
    public List<TicketComment> allComments(Long id) {
        return comments.findByTicketOrderByCreatedAtAsc(get(id));
    }

    @Transactional
    @CacheEvict(cacheNames = {"ticketCounts", "dashboardCounters"}, allEntries = true)
    public void addEmployeeComment(Long id, String username, String content) {
        Ticket ticket = getMyTicket(id, username);
        addComment(ticket, findUser(username), content, false);
    }

    @Transactional
    @CacheEvict(cacheNames = {"ticketCounts", "dashboardCounters"}, allEntries = true)
    public void addTechnicianComment(Long id, String username, String content, boolean internalNote) {
        Ticket ticket = get(id);
        addComment(ticket, findTechnician(username), content, internalNote);
    }

    private void addComment(Ticket ticket, User author, String content, boolean internalNote) {
        if (content == null || content.isBlank()) throw new IllegalArgumentException("Comment cannot be empty");
        if (content.length() > 5000) throw new IllegalArgumentException("Comment cannot exceed 5000 characters");
        comments.save(new TicketComment(ticket, author, content.trim(), internalNote));
        ticket.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        tickets.save(ticket);
        history.save(new TicketHistory(ticket, author, "COMMENT_ADDED", internalNote ? "Internal note added" : "Comment added"));
    }

    private User findUser(String username) {
        return users.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    @Transactional
    @CacheEvict(cacheNames = {"ticketCounts", "dashboardCounters"}, allEntries = true)
    public Ticket updateTicket(Long id, TicketEditForm form, String username) {
        validateRequired(form);
        User actor = findTechnician(username);
        Ticket ticket = get(id);
        if (form.getVersion() == null || !Objects.equals(form.getVersion(), ticket.getVersion())) {
            throw new TicketEditConflictException();
        }

        saveChange(ticket, actor, "title", ticket.getTitle(), form.getTitle());
        saveChange(ticket, actor, "description", ticket.getDescription(), form.getDescription());
        saveChange(ticket, actor, "category", value(ticket.getCategory()), value(form.getCategory()));
        saveChange(ticket, actor, "priority", value(ticket.getPriority()), value(form.getPriority()));
        ticket.setTitle(form.getTitle().trim());
        ticket.setDescription(form.getDescription().trim());
        ticket.setCategory(form.getCategory());
        ticket.setPriority(form.getPriority());

        if (form.getTechnicianId() != null) assignTo(ticket, actor, form.getTechnicianId());
        if (form.getStatus() != null && form.getStatus() != ticket.getStatus()) {
            updateStatusValue(ticket, actor, form.getStatus(), form.getResolutionNotes());
        } else if (form.getResolutionNotes() != null && !Objects.equals(form.getResolutionNotes(), ticket.getResolutionNotes())) {
            if (requiresResolutionNotes(ticket.getStatus()) && form.getResolutionNotes().isBlank()) {
                throw new IllegalArgumentException("Resolution notes are required");
            }
            saveChange(ticket, actor, "resolutionNotes", ticket.getResolutionNotes(), form.getResolutionNotes());
            ticket.setResolutionNotes(form.getResolutionNotes().trim());
        }
        ticket.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        return tickets.save(ticket);
    }

    private void validateRequired(TicketEditForm form) {
        if (form.getTitle() == null || form.getTitle().isBlank()) throw new IllegalArgumentException("Title is required");
        if (form.getTitle().length() > 160) throw new IllegalArgumentException("Title cannot exceed 160 characters");
        if (form.getDescription() == null || form.getDescription().isBlank()) throw new IllegalArgumentException("Description is required");
        if (form.getCategory() == null) throw new IllegalArgumentException("Category is required");
        if (form.getPriority() == null) throw new IllegalArgumentException("Priority is required");
    }

    @Transactional
    public Ticket updateFields(Long id, String title, String description, Category category, Priority priority, String username) {
        TicketEditForm form = new TicketEditForm();
        form.setTitle(title); form.setDescription(description); form.setCategory(category); form.setPriority(priority);
        return updateTicket(id, form, username);
    }

    @Transactional
    @CacheEvict(cacheNames = {"ticketCounts", "dashboardCounters"}, allEntries = true)
    public void updateStatus(Long id, TicketStatus status, String notes, String username) {
        if (status == null) throw new IllegalArgumentException("Status is required");
        if (requiresResolutionNotes(status) && (notes == null || notes.isBlank())) {
            throw new IllegalArgumentException("Resolution notes are required when resolving or closing a ticket");
        }
        User actor = findTechnician(username);
        Ticket ticket = get(id);
        updateStatusValue(ticket, actor, status, notes);
        ticket.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        tickets.save(ticket);
    }

    @Transactional
    @CacheEvict(cacheNames = {"ticketCounts", "dashboardCounters"}, allEntries = true)
    public void assign(Long id, Long technicianId, String username) {
        User actor = findTechnician(username);
        Ticket ticket = get(id);
        assignTo(ticket, actor, technicianId);
        ticket.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        tickets.save(ticket);
    }

    private void assignTo(Ticket ticket, User actor, Long technicianId) {
        User assignee = users.findById(technicianId)
                .orElseThrow(() -> new ResourceNotFoundException("Technician not found"));
        if (assignee.getRole() != Role.TECHNICIAN) throw new IllegalArgumentException("Assignee must be a technician");
        String previous = ticket.getTechnician() == null ? null : ticket.getTechnician().getUsername();
        if (!Objects.equals(previous, assignee.getUsername())) {
            history.save(new TicketHistory(ticket, actor, "ASSIGNED", "Ticket assigned", "technician", previous, assignee.getUsername()));
            ticket.setTechnician(assignee);
        }
    }

    private void updateStatusValue(Ticket ticket, User actor, TicketStatus status, String notes) {
        if (requiresResolutionNotes(status) && (notes == null || notes.isBlank())) {
            throw new IllegalArgumentException("Resolution notes are required when resolving or closing a ticket");
        }
        TicketStatus previous = ticket.getStatus();
        String cleanNotes = notes == null ? null : notes.trim();
        if (status != previous) {
            history.save(new TicketHistory(ticket, actor, "STATUS_CHANGED", "Status updated", "status", value(previous), value(status)));
        }
        if (cleanNotes != null && !Objects.equals(cleanNotes, ticket.getResolutionNotes())) {
            saveChange(ticket, actor, "resolutionNotes", ticket.getResolutionNotes(), cleanNotes);
        }
        ticket.setStatus(status);
        if (cleanNotes != null) ticket.setResolutionNotes(cleanNotes);
    }

    private boolean requiresResolutionNotes(TicketStatus status) {
        return status == TicketStatus.RESOLVED || status == TicketStatus.CLOSED;
    }

    private User findTechnician(String username) {
        User user = users.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getRole() != Role.TECHNICIAN) throw new AccessDeniedException("Only technicians can manage tickets");
        return user;
    }

    private void saveChange(Ticket ticket, User actor, String field, String oldValue, String newValue) {
        if (!Objects.equals(oldValue, newValue)) {
            history.save(new TicketHistory(ticket, actor, "FIELD_CHANGED", "Ticket field updated", field, oldValue, newValue));
        }
    }

    private String value(Object value) { return value == null ? null : value.toString(); }

    @Cacheable(cacheNames = "ticketCounts", key = "#status.name()")
    public long count(TicketStatus status) { return tickets.countByStatus(status); }

    @Cacheable(cacheNames = "dashboardCounters", key = "'highPriority'")
    public long countHighPriority() { return tickets.countByPriority(Priority.HIGH); }

    @Cacheable(cacheNames = "dashboardCounters", key = "'unassigned'")
    public long countUnassigned() { return tickets.countByTechnicianIsNull(); }

    @Cacheable(cacheNames = "dashboardCounters", key = "'assigned:' + #technician.id")
    public long countAssignedTo(User technician) { return tickets.countByTechnician(technician); }

    @Transactional(readOnly = true)
    public List<Ticket> recent(int limit) { return tickets.findTop10ByOrderByUpdatedAtDesc().stream().limit(limit).toList(); }
}
