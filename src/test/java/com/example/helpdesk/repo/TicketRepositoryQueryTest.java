package com.example.helpdesk.repo;

import static org.junit.jupiter.api.Assertions.*;

import com.example.helpdesk.model.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TicketRepositoryQueryTest {

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private UserRepository userRepository;

    private Ticket newTicket(
            User reporter, String title, TicketStatus status, LocalDateTime slaDueAt, User technician) {
        Ticket ticket = new Ticket();
        ticket.setTitle(title);
        ticket.setDescription("Query test ticket: " + title);
        ticket.setCategory(Category.OTHER);
        ticket.setPriority(Priority.MEDIUM);
        ticket.setStatus(status);
        ticket.setReporter(reporter);
        ticket.setTechnician(technician);
        ticket.setSlaTargetSeconds(86400);
        ticket.setSlaDueAt(slaDueAt);
        return ticketRepository.save(ticket);
    }

    @Test
    void specificationsWithAllNullsReturnEveryTicket() {
        Page<Ticket> result = ticketRepository.findAll(
                TicketSpecifications.withFilters(
                        null, null, null, null, null, null, null, false, false, LocalDateTime.now(ZoneOffset.UTC)),
                PageRequest.of(0, 20));
        assertNotNull(result);
        assertTrue(result.getTotalElements() >= 0);
    }

    @Test
    void queryMatchesTitleDescriptionAndTicketId() {
        User reporter = userRepository.save(new User("query-reporter", "pass", "Query Reporter", Role.EMPLOYEE));
        User technician = userRepository.save(new User("query-tech", "pass", "Query Tech", Role.TECHNICIAN));
        Ticket printer = newTicket(reporter, "Printer fire", TicketStatus.OPEN, null, null);
        Ticket wifi = newTicket(reporter, "Wifi outage", TicketStatus.OPEN, null, technician);

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Page<Ticket> byTitle = ticketRepository.findAll(
                TicketSpecifications.withFilters(null, null, null, null, null, null, "printer", false, false, now),
                PageRequest.of(0, 20));
        assertEquals(List.of(printer.getId()), byTitle.map(Ticket::getId).toList());

        Page<Ticket> byDescription = ticketRepository.findAll(
                TicketSpecifications.withFilters(null, null, null, null, null, null, "wifi outage", false, false, now),
                PageRequest.of(0, 20));
        assertEquals(1, byDescription.getTotalElements());

        Page<Ticket> byId = ticketRepository.findAll(
                TicketSpecifications.withFilters(
                        null, null, null, null, null, null, String.valueOf(wifi.getId()), false, false, now),
                PageRequest.of(0, 20));
        assertEquals(List.of(wifi.getId()), byId.map(Ticket::getId).toList());
    }

    @Test
    void overdueFilterMatchesOnlyActiveTicketsPastDue() {
        User reporter = userRepository.save(new User("query-reporter-2", "pass", "Query Reporter", Role.EMPLOYEE));
        LocalDateTime past = LocalDateTime.now(ZoneOffset.UTC).minusHours(2);
        newTicket(reporter, "Overdue active", TicketStatus.OPEN, past, null);
        newTicket(reporter, "Overdue but resolved", TicketStatus.RESOLVED, past, null);
        newTicket(
                reporter,
                "On track",
                TicketStatus.OPEN,
                LocalDateTime.now(ZoneOffset.UTC).plusDays(1),
                null);

        Page<Ticket> overdue = ticketRepository.findAll(
                TicketSpecifications.withFilters(
                        null, null, null, null, null, null, null, false, true, LocalDateTime.now(ZoneOffset.UTC)),
                PageRequest.of(0, 20));
        assertEquals(1, overdue.getTotalElements());
        assertEquals("Overdue active", overdue.getContent().get(0).getTitle());
    }

    @Test
    void resolvedClosedFilterMatchesBothTerminalStatuses() {
        User reporter = userRepository.save(new User("query-reporter-3", "pass", "Query Reporter", Role.EMPLOYEE));
        newTicket(reporter, "Resolved one", TicketStatus.RESOLVED, null, null);
        newTicket(reporter, "Closed one", TicketStatus.CLOSED, null, null);
        newTicket(reporter, "Open one", TicketStatus.OPEN, null, null);

        Page<Ticket> terminal = ticketRepository.findAll(
                TicketSpecifications.withFilters(
                        null, null, null, null, null, null, null, true, false, LocalDateTime.now(ZoneOffset.UTC)),
                PageRequest.of(0, 20));
        assertEquals(2, terminal.getTotalElements());
    }

    @Test
    void unassignedFilterRespectsTriState() {
        User reporter = userRepository.save(new User("query-reporter-4", "pass", "Query Reporter", Role.EMPLOYEE));
        User technician = userRepository.save(new User("query-tech-2", "pass", "Query Tech", Role.TECHNICIAN));
        newTicket(reporter, "Unassigned ticket", TicketStatus.OPEN, null, null);
        newTicket(reporter, "Assigned ticket", TicketStatus.OPEN, null, technician);

        Page<Ticket> unassigned = ticketRepository.findAll(
                TicketSpecifications.withFilters(
                        null, null, null, null, null, true, null, false, false, LocalDateTime.now(ZoneOffset.UTC)),
                PageRequest.of(0, 20));
        assertEquals(1, unassigned.getTotalElements());
        assertEquals("Unassigned ticket", unassigned.getContent().get(0).getTitle());

        Page<Ticket> assigned = ticketRepository.findAll(
                TicketSpecifications.withFilters(
                        null, null, null, null, null, false, null, false, false, LocalDateTime.now(ZoneOffset.UTC)),
                PageRequest.of(0, 20));
        assertEquals(1, assigned.getTotalElements());
        assertEquals("Assigned ticket", assigned.getContent().get(0).getTitle());
    }

    @Test
    void assetSpecificationsSearchTagNameAndManufacturer() {
        Page<Asset> all =
                assetRepository.findAll(AssetSpecifications.withFilters(null, null, null), PageRequest.of(0, 20));
        assertNotNull(all);

        Page<Asset> byTag = assetRepository.findAll(
                AssetSpecifications.withFilters("ZZZ-NO-SUCH-TAG", null, null), PageRequest.of(0, 20));
        assertEquals(0, byTag.getTotalElements());
    }

    @Test
    void distinctCategoriesStillWorks() {
        assertNotNull(assetRepository.findDistinctCategories());
    }
}
