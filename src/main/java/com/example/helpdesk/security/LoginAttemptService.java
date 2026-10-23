package com.example.helpdesk.security;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/** Per-process login throttle. A shared store is required to coordinate multiple app instances. */
@Service
public class LoginAttemptService {
    private static final int MAX_FAILURES = 5;
    private static final long WINDOW_MILLIS = Duration.ofMinutes(15).toMillis();
    private static final long BLOCK_MILLIS = Duration.ofMinutes(10).toMillis();
    private final ConcurrentHashMap<String, State> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginAttemptService(Clock clock) {
        this.clock = clock;
    }

    public boolean isBlocked(String username, String remoteAddress) {
        String key = key(username, remoteAddress);
        long now = clock.millis();
        State state = attempts.get(key);
        if (state == null) return false;
        synchronized (state) {
            if (state.blockedUntil > now) return true;
            if (state.blockedUntil != 0 || now - state.windowStarted > WINDOW_MILLIS) attempts.remove(key, state);
            return false;
        }
    }

    public void recordFailure(String username, String remoteAddress) {
        String key = key(username, remoteAddress);
        long now = clock.millis();
        attempts.compute(key, (ignored, current) -> {
            State state = current == null || now - current.windowStarted > WINDOW_MILLIS ? new State(now) : current;
            synchronized (state) {
                state.failures++;
                if (state.failures >= MAX_FAILURES) state.blockedUntil = now + BLOCK_MILLIS;
            }
            return state;
        });
        if (attempts.size() > 10_000) attempts.entrySet().removeIf(entry -> now - entry.getValue().windowStarted > WINDOW_MILLIS);
    }

    public void recordSuccess(String username, String remoteAddress) {
        attempts.remove(key(username, remoteAddress));
    }

    private String key(String username, String remoteAddress) {
        String cleanName = username == null ? "" : username.trim().toLowerCase(java.util.Locale.ROOT);
        String cleanAddress = remoteAddress == null ? "unknown" : remoteAddress;
        return cleanName + "@" + cleanAddress;
    }

    private static final class State {
        private final long windowStarted;
        private int failures;
        private long blockedUntil;
        private State(long windowStarted) { this.windowStarted = windowStarted; }
    }
}
