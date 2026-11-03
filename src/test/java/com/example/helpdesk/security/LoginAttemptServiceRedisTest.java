package com.example.helpdesk.security;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LoginAttemptServiceRedisTest {
    @Test
    void failureAfterTenMinuteBlockDoesNotImmediatelyBlockAgain() {
        MutableClock clock = new MutableClock();
        Map<String, Long> counters = new HashMap<>();
        Map<String, Long> counterExpiry = new HashMap<>();
        Map<String, Long> blockExpiry = new HashMap<>();
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.increment(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            if (counterExpiry.getOrDefault(key, Long.MAX_VALUE) <= clock.millis()) counters.remove(key);
            return counters.merge(key, 1L, Long::sum);
        });
        when(redis.expire(anyString(), any(Duration.class))).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            long expiry = clock.millis() + invocation.<Duration>getArgument(1).toMillis();
            if (key.startsWith("helpdesk:login:failures:")) counterExpiry.put(key, expiry);
            return true;
        });
        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            blockExpiry.put(key, clock.millis() + invocation.<Duration>getArgument(2).toMillis());
            return null;
        }).when(values).set(anyString(), eq("1"), any(Duration.class));
        when(redis.hasKey(anyString())).thenAnswer(invocation -> {
            Long expiresAt = blockExpiry.get(invocation.getArgument(0));
            return expiresAt != null && expiresAt > clock.millis();
        });

        LoginAttemptService service = new LoginAttemptService(clock, redis, true);
        for (int i = 0; i < 5; i++) service.recordFailure("alice", "127.0.0.1");
        assertTrue(service.isBlocked("alice", "127.0.0.1"));

        clock.advance(Duration.ofMinutes(10));
        assertFalse(service.isBlocked("alice", "127.0.0.1"));
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
