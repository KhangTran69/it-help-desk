package com.example.helpdesk.service;

import com.example.helpdesk.dto.TicketEditForm;
import com.example.helpdesk.exception.ResourceNotFoundException;
import com.example.helpdesk.model.*;
import com.example.helpdesk.repo.TicketHistoryRepository;
import com.example.helpdesk.repo.TicketRepository;
import com.example.helpdesk.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock
    TicketRepository tickets;
    @Mock
    UserRepository users;
    @Mock
    TicketHistoryRepository history;

    private TicketService service;
    private User employeeAlice;
    private User employeeBob;
    private User technicianCharlie;

    @BeforeEach
    void setUp() {
        service = new TicketService(tickets, users, history);

        employeeAlice = new User("alice", "pass", "Alice Adams", Role.EMPLOYEE);
        employeeAlice.setId(101L);

        employeeBob = new User("bob", "pass", "Bob Baker", Role.EMPLOYEE);
        employeeBob.setId(102L);

        technicianCharlie = new User("charlie", "pass", "Charlie Tech", Role.TECHNICIAN);
        technicianCharlie.setId(201L);
    }

    @Test
    void employeeCanCreateOpenTicket() {
        when(users.findByUsername("alice")).thenReturn(Optional.of(employeeAlice));
        Ticket input = new Ticket();
        input.setTitle("Wi-Fi issue");
        input.setDescription("No connection");
        input.setCategory(Category.NETWORK);
        when(tickets.save(input)).thenReturn(input);

        Ticket result = service.create(input, "alice");

        assertSame(employeeAlice, result.getReporter());
        assertEquals(TicketStatus.OPEN, result.getStatus());
        assertEquals(Priority.MEDIUM, result.getPriority());
        assertNotNull(result.getCreatedAt());
        assertNotNull(result.getUpdatedAt());
        verify(history).save(any(TicketHistory.class));
    }

    @Test
    void resolvedTicketRequiresNotes() {
        Ticket ticket = new Ticket();
        assertThrows(IllegalArgumentException.class, () -> service.updateStatus(1L, TicketStatus.RESOLVED, "", "tech"));
        assertThrows(IllegalArgumentException.class, () -> service.updateStatus(1L, TicketStatus.CLOSED, "   ", "tech"));
    }

    @Test
    void employeeCanEditOwnOpenTicket_ModifiesExistingRecordAndPreservesCoreFields() {
        LocalDateTime originalCreated = LocalDateTime.of(2026, 1, 15, 10, 0);
        LocalDateTime originalUpdated = LocalDateTime.of(2026, 1, 15, 10, 0);

        Ticket existing = new Ticket();
        existing.setId(1L);
        existing.setTitle("Old Title");
        existing.setDescription("Old Description");
        existing.setCategory(Category.HARDWARE);
        existing.setPriority(Priority.LOW);
        existing.setStatus(TicketStatus.OPEN);
        existing.setReporter(employeeAlice);
        existing.setCreatedAt(originalCreated);
        existing.setUpdatedAt(originalUpdated);

        when(users.findByUsername("alice")).thenReturn(Optional.of(employeeAlice));

        TicketEditForm form = new TicketEditForm();
        form.setTitle("Updated Title");
        form.setDescription("Updated Description");
        form.setCategory(Category.SOFTWARE);
        form.setPriority(Priority.HIGH);
        // Employee attempts to supply technician fields in HTTP request
        form.setStatus(TicketStatus.RESOLVED);
        form.setTechnicianId(999L);
        form.setResolutionNotes("Should be ignored for employee");

        assertThrows(AccessDeniedException.class, () -> service.updateTicket(1L, form, "alice"));
        verify(tickets, never()).save(any(Ticket.class));
    }

    @Test
    void employeeCannotEditAnotherEmployeesTicket() {
        Ticket existing = new Ticket();
        existing.setId(2L);
        existing.setTitle("Bob's Issue");
        existing.setDescription("Desc");
        existing.setCategory(Category.OTHER);
        existing.setPriority(Priority.LOW);
        existing.setStatus(TicketStatus.OPEN);
        existing.setReporter(employeeBob);

        when(users.findByUsername("alice")).thenReturn(Optional.of(employeeAlice));

        TicketEditForm form = new TicketEditForm();
        form.setTitle("Alice Attempt");
        form.setDescription("New Desc");
        form.setCategory(Category.OTHER);
        form.setPriority(Priority.LOW);

        assertThrows(AccessDeniedException.class, () -> service.updateTicket(2L, form, "alice"));
        verify(tickets, never()).save(any(Ticket.class));
    }

    @Test
    void employeeCannotEditTicketThatIsNotOpen() {
        Ticket existing = new Ticket();
        existing.setId(3L);
        existing.setTitle("In Progress Issue");
        existing.setDescription("Desc");
        existing.setCategory(Category.NETWORK);
        existing.setPriority(Priority.MEDIUM);
        existing.setStatus(TicketStatus.IN_PROGRESS);
        existing.setReporter(employeeAlice);

        when(users.findByUsername("alice")).thenReturn(Optional.of(employeeAlice));

        TicketEditForm form = new TicketEditForm();
        form.setTitle("Alice Attempt");
        form.setDescription("New Desc");
        form.setCategory(Category.NETWORK);
        form.setPriority(Priority.MEDIUM);

        assertThrows(AccessDeniedException.class, () -> service.updateTicket(3L, form, "alice"));
        verify(tickets, never()).save(any(Ticket.class));
    }

    @Test
    void editingNonexistentTicketReturnsNotFoundError() {
        when(tickets.findById(999L)).thenReturn(Optional.empty());
        when(users.findByUsername("alice")).thenReturn(Optional.of(technicianCharlie));

        TicketEditForm form = new TicketEditForm();
        form.setTitle("Title");
        form.setDescription("Description");
        form.setCategory(Category.HARDWARE);
        form.setPriority(Priority.LOW);

        assertThrows(ResourceNotFoundException.class, () -> service.updateTicket(999L, form, "alice"));
    }

    @Test
    void invalidTitleOrMissingFieldsRejected() {
        Ticket existing = new Ticket();
        existing.setId(4L);

        TicketEditForm blankTitle = new TicketEditForm();
        blankTitle.setTitle("   ");
        blankTitle.setDescription("Valid Description");
        blankTitle.setCategory(Category.HARDWARE);
        blankTitle.setPriority(Priority.LOW);

        assertThrows(IllegalArgumentException.class, () -> service.updateTicket(4L, blankTitle, "alice"));

        TicketEditForm missingDesc = new TicketEditForm();
        missingDesc.setTitle("Valid Title");
        missingDesc.setDescription("");
        missingDesc.setCategory(Category.HARDWARE);
        missingDesc.setPriority(Priority.LOW);

        assertThrows(IllegalArgumentException.class, () -> service.updateTicket(4L, missingDesc, "alice"));

        TicketEditForm missingCategory = new TicketEditForm();
        missingCategory.setTitle("Valid Title");
        missingCategory.setDescription("Valid Description");
        missingCategory.setCategory(null);
        missingCategory.setPriority(Priority.LOW);

        assertThrows(IllegalArgumentException.class, () -> service.updateTicket(4L, missingCategory, "alice"));
    }

    @Test
    void technicianCanEditAndAssignTickets() {
        Ticket existing = new Ticket();
        existing.setId(5L);
        existing.setTitle("Server Disk Full");
        existing.setDescription("Need cleanup");
        existing.setCategory(Category.HARDWARE);
        existing.setPriority(Priority.MEDIUM);
        existing.setStatus(TicketStatus.OPEN);
        existing.setReporter(employeeAlice);

        when(tickets.findById(5L)).thenReturn(Optional.of(existing));
        when(users.findByUsername("charlie")).thenReturn(Optional.of(technicianCharlie));
        when(users.findById(201L)).thenReturn(Optional.of(technicianCharlie));
        when(tickets.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        TicketEditForm form = new TicketEditForm();
        form.setTitle("Server Disk Full - Production");
        form.setDescription("Need cleanup on /var/log");
        form.setCategory(Category.HARDWARE);
        form.setPriority(Priority.HIGH);
        form.setStatus(TicketStatus.IN_PROGRESS);
        form.setTechnicianId(201L);

        Ticket updated = service.updateTicket(5L, form, "charlie");

        assertEquals(Priority.HIGH, updated.getPriority());
        assertEquals(TicketStatus.IN_PROGRESS, updated.getStatus());
        assertSame(technicianCharlie, updated.getTechnician());

        ArgumentCaptor<TicketHistory> captor = ArgumentCaptor.forClass(TicketHistory.class);
        verify(history, atLeastOnce()).save(captor.capture());
        assertTrue(captor.getAllValues().stream().anyMatch(h -> "ASSIGNED".equals(h.getAction())));
        assertTrue(captor.getAllValues().stream().anyMatch(h -> "STATUS_CHANGED".equals(h.getAction())));
    }

    @Test
    void resolvingTicketWithoutNotesRejected() {
        Ticket existing = new Ticket();
        existing.setId(6L);
        existing.setTitle("Issue");
        existing.setDescription("Desc");
        existing.setCategory(Category.NETWORK);
        existing.setPriority(Priority.LOW);
        existing.setStatus(TicketStatus.IN_PROGRESS);
        existing.setReporter(employeeAlice);

        when(tickets.findById(6L)).thenReturn(Optional.of(existing));
        when(users.findByUsername("charlie")).thenReturn(Optional.of(technicianCharlie));

        TicketEditForm form = new TicketEditForm();
        form.setTitle("Issue");
        form.setDescription("Desc");
        form.setCategory(Category.NETWORK);
        form.setPriority(Priority.LOW);
        form.setStatus(TicketStatus.RESOLVED);
        form.setResolutionNotes("   "); // Blank notes

        assertThrows(IllegalArgumentException.class, () -> service.updateTicket(6L, form, "charlie"));
    }

    @Test
    void auditHistoryRecordsFieldChangedAndValues() {
        Ticket existing = new Ticket();
        existing.setId(7L);
        existing.setTitle("Old Subject");
        existing.setDescription("Old Desc");
        existing.setCategory(Category.HARDWARE);
        existing.setPriority(Priority.LOW);
        existing.setStatus(TicketStatus.OPEN);
        existing.setReporter(employeeAlice);

        when(tickets.findById(7L)).thenReturn(Optional.of(existing));
        when(tickets.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        TicketEditForm form = new TicketEditForm();
        form.setTitle("New Subject");
        form.setDescription("Old Desc");
        form.setCategory(Category.SOFTWARE);
        form.setPriority(Priority.HIGH);

        when(users.findByUsername("charlie")).thenReturn(Optional.of(technicianCharlie));
        service.updateTicket(7L, form, "charlie");

        ArgumentCaptor<TicketHistory> captor = ArgumentCaptor.forClass(TicketHistory.class);
        verify(history, times(3)).save(captor.capture());

        TicketHistory titleChange = captor.getAllValues().stream()
                .filter(h -> "title".equals(h.getFieldChanged()))
                .findFirst().orElseThrow();
        assertEquals("Old Subject", titleChange.getPreviousValue());
        assertEquals("New Subject", titleChange.getNewValue());
        assertSame(technicianCharlie, titleChange.getActor());

        TicketHistory prioChange = captor.getAllValues().stream()
                .filter(h -> "priority".equals(h.getFieldChanged()))
                .findFirst().orElseThrow();
        assertEquals("LOW", prioChange.getPreviousValue());
        assertEquals("HIGH", prioChange.getNewValue());
    }
}
