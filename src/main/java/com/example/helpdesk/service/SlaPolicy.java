package com.example.helpdesk.service;

import com.example.helpdesk.model.Priority;
import com.example.helpdesk.model.Ticket;
import com.example.helpdesk.model.TicketStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

/** Elapsed-time SLA clock. Resolved and closed tickets pause; reopening resumes remaining time. */
@Component
public class SlaPolicy {
    private final Clock clock;
    private final Map<Priority, Long> targets;

    public SlaPolicy(Clock clock,
                     @Value("${helpdesk.sla.targets.low:1209600}") long low,
                     @Value("${helpdesk.sla.targets.medium:259200}") long medium,
                     @Value("${helpdesk.sla.targets.high:86400}") long high) {
        this.clock = clock;
        this.targets = Map.of(Priority.LOW, low, Priority.MEDIUM, medium, Priority.HIGH, high);
    }

    public void start(Ticket ticket) {
        LocalDateTime now = now();
        ticket.setSlaTargetSeconds(targetFor(ticket.getPriority()));
        ticket.setSlaElapsedSeconds(0);
        ticket.setSlaStartedAt(now);
        ticket.setSlaDueAt(now.plusSeconds(ticket.getSlaTargetSeconds()));
    }

    public boolean isOverdue(Ticket ticket) {
        return ticket.getSlaTargetSeconds() > 0 && elapsedAt(ticket, now()) >= ticket.getSlaTargetSeconds();
    }

    public void priorityChanged(Ticket ticket, Priority priority) {
        if (priority == ticket.getPriority()) return;
        LocalDateTime now = now();
        long elapsed = elapsedAt(ticket, now);
        ticket.setSlaElapsedSeconds(elapsed);
        ticket.setSlaTargetSeconds(targetFor(priority));
        if (ticket.getSlaStartedAt() != null) {
            ticket.setSlaStartedAt(now);
            ticket.setSlaDueAt(now.plusSeconds(Math.max(0, ticket.getSlaTargetSeconds() - elapsed)));
        }
    }

    public void statusChanged(Ticket ticket, TicketStatus previous, TicketStatus next) {
        LocalDateTime now = now();
        boolean wasStopped = stopped(previous);
        boolean willStop = stopped(next);
        if (!wasStopped && willStop && ticket.getSlaStartedAt() != null) {
            ticket.setSlaElapsedSeconds(elapsedAt(ticket, now));
            ticket.setSlaStartedAt(null);
            ticket.setSlaDueAt(null);
        } else if (wasStopped && !willStop) {
            ticket.setSlaStartedAt(now);
            ticket.setSlaDueAt(now.plusSeconds(Math.max(0, ticket.getSlaTargetSeconds() - ticket.getSlaElapsedSeconds())));
        }
    }

    private long targetFor(Priority priority) {
        return targets.getOrDefault(priority, targets.get(Priority.MEDIUM));
    }

    private boolean stopped(TicketStatus status) {
        return status == TicketStatus.RESOLVED || status == TicketStatus.CLOSED;
    }

    private long elapsedAt(Ticket ticket, LocalDateTime now) {
        long running = ticket.getSlaStartedAt() == null ? 0 : Math.max(0, Duration.between(ticket.getSlaStartedAt(), now).getSeconds());
        return ticket.getSlaElapsedSeconds() + running;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
