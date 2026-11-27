package com.example.helpdesk.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.helpdesk.metrics.BusinessMetrics;
import com.example.helpdesk.model.Category;
import com.example.helpdesk.model.Priority;
import com.example.helpdesk.model.Role;
import com.example.helpdesk.model.Ticket;
import com.example.helpdesk.model.TicketNotification;
import com.example.helpdesk.model.TicketStatus;
import com.example.helpdesk.model.User;
import com.example.helpdesk.repo.TicketNotificationRepository;
import com.example.helpdesk.repo.TicketRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SlaEscalationServiceTest {

    @Mock
    private TicketRepository tickets;

    @Mock
    private TicketNotificationRepository notifications;

    @Mock
    private BusinessMetrics businessMetrics;

    private SlaEscalationService service;

    @BeforeEach
    void setUp() {
        service = new SlaEscalationService(tickets, notifications, businessMetrics, mock(MailNotifier.class), true);
    }

    private Ticket ticket(TicketStatus status, User reporter, User technician) {
        Ticket ticket = new Ticket();
        ticket.setId(7L);
        ticket.setTitle("Overdue request");
        ticket.setCategory(Category.OTHER);
        ticket.setPriority(Priority.HIGH);
        ticket.setStatus(status);
        ticket.setReporter(reporter);
        ticket.setTechnician(technician);
        ticket.setSlaTargetSeconds(86400);
        ticket.setSlaDueAt(LocalDateTime.now(ZoneOffset.UTC).minusHours(3));
        return ticket;
    }

    @Test
    void activeBreachedTicketNotifiesReporterAndTechnicianOnce() {
        User reporter = new User("employee", "pw", "Employee", Role.EMPLOYEE);
        User technician = new User("tech", "pw", "Technician", Role.TECHNICIAN);
        Ticket active = ticket(TicketStatus.IN_PROGRESS, reporter, technician);
        when(tickets.findBySlaDueAtBeforeAndSlaBreachNotifiedAtIsNull(any(LocalDateTime.class)))
                .thenReturn(List.of(active));

        service.sweep();

        ArgumentCaptor<TicketNotification> captured = ArgumentCaptor.forClass(TicketNotification.class);
        verify(notifications, times(2)).save(captured.capture());
        verify(businessMetrics).incrementSlaBreached();
        verify(tickets).save(active);
        org.assertj.core.api.Assertions.assertThat(active.getSlaBreachNotifiedAt())
                .isNotNull();
        org.assertj.core.api.Assertions.assertThat(captured.getAllValues())
                .extracting(TicketNotification::getRecipient)
                .containsExactlyInAnyOrder(reporter, technician);
    }

    @Test
    void alreadyResolvedTicketsAreMarkedWithoutNotification() {
        Ticket resolved = ticket(TicketStatus.RESOLVED, new User("employee", "pw", "Employee", Role.EMPLOYEE), null);
        when(tickets.findBySlaDueAtBeforeAndSlaBreachNotifiedAtIsNull(any(LocalDateTime.class)))
                .thenReturn(List.of(resolved));

        service.sweep();

        verify(notifications, never()).save(any(TicketNotification.class));
        verify(businessMetrics, never()).incrementSlaBreached();
        verify(tickets).save(resolved);
        org.assertj.core.api.Assertions.assertThat(resolved.getSlaBreachNotifiedAt())
                .isNotNull();
    }

    @Test
    void disabledSweepDoesNothing() {
        SlaEscalationService off =
                new SlaEscalationService(tickets, notifications, businessMetrics, mock(MailNotifier.class), false);
        off.sweep();
        verify(tickets, never()).findBySlaDueAtBeforeAndSlaBreachNotifiedAtIsNull(any(LocalDateTime.class));
    }
}
