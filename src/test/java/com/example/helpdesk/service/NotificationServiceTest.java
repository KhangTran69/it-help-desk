package com.example.helpdesk.service;

import com.example.helpdesk.events.TicketEvent;
import com.example.helpdesk.model.Role;
import com.example.helpdesk.model.Ticket;
import com.example.helpdesk.model.TicketNotification;
import com.example.helpdesk.model.User;
import com.example.helpdesk.repo.TicketNotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private TicketNotificationRepository notifications;
    @InjectMocks
    private NotificationService service;

    private Ticket ticket() {
        Ticket ticket = new Ticket();
        ticket.setId(5L);
        ticket.setTitle("Password reset request");
        return ticket;
    }

    @Test
    void statusChangeNotifiesTheReporter() {
        Ticket ticket = ticket();
        User reporter = new User("employee", "pw", "Employee Name", Role.EMPLOYEE);
        User technician = new User("tech", "pw", "Tech Name", Role.TECHNICIAN);
        ticket.setReporter(reporter);

        service.onTicketEvent(new TicketEvent(new Object(), ticket, technician, "STATUS_CHANGED",
                "Status updated", "status", "OPEN", "RESOLVED"));

        ArgumentCaptor<TicketNotification> captured = ArgumentCaptor.forClass(TicketNotification.class);
        verify(notifications).save(captured.capture());
        assertThat(captured.getValue().getRecipient()).isSameAs(reporter);
        assertThat(captured.getValue().getTitle()).contains("#5");
        assertThat(captured.getValue().getMessage()).contains("Password reset request")
                .contains("RESOLVED");
    }

    @Test
    void assignmentNotifiesTechnicianAndReporterWithoutSelfNotifications() {
        Ticket ticket = ticket();
        User actor = new User("tech", "pw", "Tech Name", Role.TECHNICIAN);
        User reporter = new User("employee", "pw", "Employee Name", Role.EMPLOYEE);
        ticket.setReporter(reporter);
        ticket.setTechnician(actor);

        service.onTicketEvent(new TicketEvent(new Object(), ticket, actor, "ASSIGNED", "Ticket assigned",
                "technician", null, "tech"));

        ArgumentCaptor<TicketNotification> captured = ArgumentCaptor.forClass(TicketNotification.class);
        verify(notifications).save(captured.capture());
        // The acting technician gets nothing; the reporter hears their ticket is handled.
        assertThat(captured.getValue().getRecipient()).isSameAs(reporter);
        assertThat(captured.getValue().getMessage()).contains("being handled");
    }

    @Test
    void createdEventsStayOutOfTheFeed() {
        service.onTicketEvent(new TicketEvent(new Object(), ticket(),
                new User("employee", "pw", "Employee Name", Role.EMPLOYEE), "CREATED", "Ticket submitted"));
        verify(notifications, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void missingRecipientIsSkippedSafely() {
        service.onTicketEvent(new TicketEvent(new Object(), ticket(), null, "STATUS_CHANGED",
                "Status updated", "status", "OPEN", "RESOLVED"));
        verify(notifications, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
