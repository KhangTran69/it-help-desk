package com.example.helpdesk.service;

import com.example.helpdesk.model.Priority;
import com.example.helpdesk.model.Ticket;
import com.example.helpdesk.model.TicketStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class SlaPolicyTest {
    @Test
    void stopsWhenResolvedResumesWithRemainingTimeAndMarksOverdue() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        SlaPolicy policy = new SlaPolicy(clock, 200, 100, 60);
        Ticket ticket = new Ticket();
        ticket.setPriority(Priority.MEDIUM);
        ticket.setStatus(TicketStatus.OPEN);
        policy.start(ticket);

        clock.advanceSeconds(40);
        policy.statusChanged(ticket, TicketStatus.OPEN, TicketStatus.RESOLVED);
        assertEquals(40, ticket.getSlaElapsedSeconds());
        assertNull(ticket.getSlaDueAt());
        clock.advanceSeconds(500);
        assertFalse(policy.isOverdue(ticket));

        policy.statusChanged(ticket, TicketStatus.RESOLVED, TicketStatus.IN_PROGRESS);
        assertEquals(clock.localNow().plusSeconds(60), ticket.getSlaDueAt());
        clock.advanceSeconds(60);
        assertTrue(policy.isOverdue(ticket));
    }

    @Test
    void changingPriorityRecalculatesRemainingElapsedTarget() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        SlaPolicy policy = new SlaPolicy(clock, 200, 100, 60);
        Ticket ticket = new Ticket();
        ticket.setPriority(Priority.LOW);
        ticket.setStatus(TicketStatus.OPEN);
        policy.start(ticket);
        clock.advanceSeconds(30);
        policy.priorityChanged(ticket, Priority.HIGH);
        assertEquals(30, ticket.getSlaElapsedSeconds());
        assertEquals(clock.localNow().plusSeconds(30), ticket.getSlaDueAt());
    }

    private static final class MutableClock extends Clock {
        private Instant now;
        private MutableClock(Instant now) { this.now = now; }
        void advanceSeconds(long seconds) { now = now.plusSeconds(seconds); }
        java.time.LocalDateTime localNow() { return java.time.LocalDateTime.ofInstant(now, ZoneId.of("UTC")); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
