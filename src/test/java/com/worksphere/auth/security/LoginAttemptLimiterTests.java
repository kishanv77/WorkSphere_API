package com.worksphere.auth.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.worksphere.common.exception.TooManyAttemptsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginAttemptLimiterTests {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    private final LoginAttemptLimiter limiter = new LoginAttemptLimiter(clock, 3, 5, Duration.ofMinutes(15));

    @Test
    void blocksAnEmailAfterTooManyFailuresAndReportsWhenToRetry() {
        for (int i = 0; i < 3; i++) {
            limiter.checkAllowed("a@x.test", "1.1.1.1");
            limiter.recordFailure("a@x.test", "1.1.1.1");
        }

        TooManyAttemptsException blocked =
                assertThrows(TooManyAttemptsException.class, () -> limiter.checkAllowed("a@x.test", "2.2.2.2"));
        assertEquals(15 * 60, blocked.getRetryAfterSeconds());
        assertDoesNotThrow(() -> limiter.checkAllowed("b@x.test", "2.2.2.2"));
    }

    @Test
    void failuresAgeOutOfTheWindow() {
        for (int i = 0; i < 3; i++) {
            limiter.recordFailure("a@x.test", "1.1.1.1");
        }
        clock.advance(Duration.ofMinutes(15).plusSeconds(1));

        assertDoesNotThrow(() -> limiter.checkAllowed("a@x.test", "1.1.1.1"));
    }

    @Test
    void successClearsTheEmailButNotTheClient() {
        limiter.recordFailure("a@x.test", "1.1.1.1");
        limiter.recordFailure("a@x.test", "1.1.1.1");
        limiter.recordSuccess("a@x.test");
        limiter.recordFailure("a@x.test", "1.1.1.1");
        limiter.recordFailure("a@x.test", "1.1.1.1");
        assertDoesNotThrow(() -> limiter.checkAllowed("a@x.test", "1.1.1.1"));

        limiter.recordFailure("c@x.test", "1.1.1.1");
        assertThrows(TooManyAttemptsException.class, () -> limiter.checkAllowed("d@x.test", "1.1.1.1"));
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
