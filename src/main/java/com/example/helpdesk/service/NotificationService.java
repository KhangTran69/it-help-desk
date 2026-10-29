package com.example.helpdesk.service;

import com.example.helpdesk.events.TicketEvent;
import com.example.helpdesk.model.Ticket;
import com.example.helpdesk.model.TicketNotification;
import com.example.helpdesk.model.User;
import com.example.helpdesk.repo.TicketNotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns ticket lifecycle events into in-app notifications for the employee
 * and the assigned technician. The acting user never notifies themselves.
 * Email delivery remains a documented future step (no SMTP dependency yet).
 */
@Component
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final TicketNotificationRepository notifications;

    public NotificationService(TicketNotificationRepository notifications) {
        this.notifications = notifications;
    }

    @EventListener
    @Transactional
    public void onTicketEvent(TicketEvent event) {
        Ticket ticket = event.getTicket();
        User actor = event.getActor();
        switch (event.getAction()) {
            case "STATUS_CHANGED" -> notify(ticket, ticket.getReporter(), actor,
                    "Ticket #" + ticket.getId() + " status changed",
                    ticket.getTitle() + " is now " + event.getNewValue() + ". "
                            + safeDetails(event.getDetails()));
            case "ASSIGNED" -> {
                notify(ticket, ticket.getTechnician(), actor,
                        "Ticket #" + ticket.getId() + " assigned to you",
                        "You were assigned: " + ticket.getTitle());
                notify(ticket, ticket.getReporter(), actor,
                        "Ticket #" + ticket.getId() + " assigned",
                        ticket.getTitle() + " is being handled by "
                                + (ticket.getTechnician() != null ? ticket.getTechnician().getUsername() : "a technician")
                                + ".");
            }
            case "FIELD_CHANGED" -> {
                if ("priority".equals(event.getFieldChanged())) {
                    notify(ticket, ticket.getReporter(), actor,
                            "Ticket #" + ticket.getId() + " priority changed",
                            ticket.getTitle() + ": priority changed from "
                                    + safeValue(event.getPreviousValue()) + " to " + safeValue(event.getNewValue()) + ".");
                }
            }
            default -> {
                // CREATED and comment events stay out of the notification feed.
            }
        }
        log.debug("Ticket event {} processed for notifications", event.getAction());
    }

    private void notify(Ticket ticket, User recipient, User actor, String title, String message) {
        if (recipient == null || actor == null) return;
        if (recipient.getUsername() != null && recipient.getUsername().equals(actor.getUsername())) return;
        notifications.save(new TicketNotification(ticket, recipient, title, message));
    }

    private String safeDetails(String details) {
        return details == null ? "" : details;
    }

    private String safeValue(String value) {
        return value == null ? "—" : value;
    }
}
