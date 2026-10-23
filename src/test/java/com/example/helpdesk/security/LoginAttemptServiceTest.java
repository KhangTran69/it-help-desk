package com.example.helpdesk.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class LoginAttemptServiceTest {
    @Test
    void blocksAfterFiveFailuresAndUnlocksAfterTenMinutes() {
        MutableClock clock = new MutableClock();
        LoginAttemptService service = new LoginAttemptService(clock);
        for (int i = 0; i < 4; i++) {
            service.recordFailure("alice", "127.0.0.1");
            assertFalse(service.isBlocked("alice", "127.0.0.1"));
        }
        service.recordFailure("alice", "127.0.0.1");
        assertTrue(service.isBlocked("Alice", "127.0.0.1"));
        assertFalse(service.isBlocked("alice", "127.0.0.2"));
        clock.advance(Duration.ofMinutes(10));
        assertFalse(service.isBlocked("alice", "127.0.0.1"));
    }

    @Test
    void successfulLoginClearsFailures() {
        LoginAttemptService service = new LoginAttemptService(new MutableClock());
        for (int i = 0; i < 4; i++) service.recordFailure("alice", "127.0.0.1");
        service.recordSuccess("alice", "127.0.0.1");
        service.recordFailure("alice", "127.0.0.1");
        assertFalse(service.isBlocked("alice", "127.0.0.1"));
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-01-01T00:00:00Z");
        void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
