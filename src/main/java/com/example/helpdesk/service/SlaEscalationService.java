package com.example.helpdesk.service;

import com.example.helpdesk.metrics.BusinessMetrics;
import com.example.helpdesk.model.Ticket;
import com.example.helpdesk.model.TicketNotification;
import com.example.helpdesk.model.TicketStatus;
import com.example.helpdesk.repo.TicketNotificationRepository;
import com.example.helpdesk.repo.TicketRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Periodic SLA breach sweep. Every ticket past its SLA due time gets one
 * notification (reporter plus assigned technician) and a helpdesk.sla.breached
 * metric increment; sla_breach_notified_at keeps the sweep idempotent.
 */
@Component
public class SlaEscalationService {

    private static final Logger log = LoggerFactory.getLogger(SlaEscalationService.class);

    private final TicketRepository tickets;
    private final TicketNotificationRepository notifications;
    private final BusinessMetrics businessMetrics;
    private final MailNotifier mailNotifier;
    private final boolean enabled;

    public SlaEscalationService(
            TicketRepository tickets,
            TicketNotificationRepository notifications,
            BusinessMetrics businessMetrics,
            MailNotifier mailNotifier,
            @Value("${helpdesk.sla.escalation.enabled:true}") boolean enabled) {
        this.tickets = tickets;
        this.notifications = notifications;
        this.businessMetrics = businessMetrics;
        this.mailNotifier = mailNotifier;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${helpdesk.sla.escalation.sweep-interval-ms:900000}", initialDelayString = "60000")
    @SchedulerLock(name = "slaEscalationSweep", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    @Transactional
    public void sweep() {
        if (!enabled) return;
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        List<Ticket> breached = tickets.findBySlaDueAtBeforeAndSlaBreachNotifiedAtIsNull(now);
        for (Ticket ticket : breached) {
            if (ticket.getStatus() != TicketStatus.RESOLVED && ticket.getStatus() != TicketStatus.CLOSED) {
                notify(
                        ticket,
                        ticket.getReporter(),
                        "Ticket #" + ticket.getId() + " is overdue",
                        ticket.getTitle() + " has passed its SLA target and is still " + ticket.getStatus() + ".");
                if (ticket.getTechnician() != null) {
                    notify(
                            ticket,
                            ticket.getTechnician(),
                            "Ticket #" + ticket.getId() + " breached its SLA",
                            ticket.getTitle() + " is overdue and assigned to you. Please prioritize it.");
                }
                if (businessMetrics != null) businessMetrics.incrementSlaBreached();
            }
            ticket.setSlaBreachNotifiedAt(now);
            tickets.save(ticket);
        }
        if (!breached.isEmpty()) {
            log.info("SLA escalation sweep processed {} ticket(s)", breached.size());
        }
    }

    private void notify(Ticket ticket, com.example.helpdesk.model.User recipient, String title, String message) {
        if (recipient == null) return;
        notifications.save(new TicketNotification(ticket, recipient, title, message));
        mailNotifier.send(recipient, title, message);
    }
}
