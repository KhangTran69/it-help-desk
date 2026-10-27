package com.example.helpdesk.security;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;

/** Per-process login throttle. A shared store is required to coordinate multiple app instances. */
@Service
public class LoginAttemptService {
    private static final int MAX_FAILURES = 5;
    private static final long WINDOW_MILLIS = Duration.ofMinutes(15).toMillis();
    private static final long BLOCK_MILLIS = Duration.ofMinutes(10).toMillis();
    private final ConcurrentHashMap<String, State> attempts = new ConcurrentHashMap<>();
    private final Clock clock;
    private final StringRedisTemplate redis;
    private final boolean sharedStore;

    @Autowired
    public LoginAttemptService(Clock clock, StringRedisTemplate redis,
                               @Value("${helpdesk.login-throttle.shared-store:false}") boolean sharedStore) {
        this.clock = clock;
        this.redis = redis;
        this.sharedStore = sharedStore;
    }

    /** In-memory constructor retained for isolated unit tests and local callers. */
    public LoginAttemptService(Clock clock) {
        this.clock = clock;
        this.redis = null;
        this.sharedStore = false;
    }

    public boolean isBlocked(String username, String remoteAddress) {
        String key = key(username, remoteAddress);
        if (sharedStore) return Boolean.TRUE.equals(redis.hasKey("helpdesk:login:blocked:" + key));
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
        if (sharedStore) {
            String failureKey = "helpdesk:login:failures:" + key;
            Long failures = redis.opsForValue().increment(failureKey);
            if (failures != null && failures == 1L) redis.expire(failureKey, Duration.ofMinutes(15));
            if (failures != null && failures >= MAX_FAILURES) {
                redis.opsForValue().set("helpdesk:login:blocked:" + key, "1", Duration.ofMinutes(10));
            }
            return;
        }
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
        String key = key(username, remoteAddress);
        if (sharedStore) {
            redis.delete(java.util.List.of(
                    "helpdesk:login:failures:" + key,
                    "helpdesk:login:blocked:" + key));
        } else {
            attempts.remove(key);
        }
    }

    private String key(String username, String remoteAddress) {
        String cleanName = username == null ? "" : username.trim().toLowerCase(java.util.Locale.ROOT);
        String cleanAddress = remoteAddress == null ? "unknown" : remoteAddress;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((cleanName + "@" + cleanAddress).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static final class State {
        private final long windowStarted;
        private int failures;
        private long blockedUntil;
        private State(long windowStarted) { this.windowStarted = windowStarted; }
    }
}
