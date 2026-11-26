package com.worksphere.auth.security;

import com.worksphere.common.exception.TooManyAttemptsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Slows down password guessing: after too many failed logins for one email, or from one client address,
 * within a sliding window, further attempts are refused with 429 until the oldest failure ages out.
 *
 * <p>State is in memory, so limits apply per application instance and reset on restart. Behind a reverse
 * proxy, enable {@code server.forward-headers-strategy} so the client address is the real one.
 */
@Component
public class LoginAttemptLimiter {

    static final int DEFAULT_MAX_FAILURES_PER_EMAIL = 5;
    static final int DEFAULT_MAX_FAILURES_PER_CLIENT = 20;
    static final Duration DEFAULT_WINDOW = Duration.ofMinutes(15);
    private static final int PRUNE_THRESHOLD = 10_000;

    private final Map<String, Deque<Instant>> failures = new HashMap<>();
    private final Clock clock;
    private final int maxFailuresPerEmail;
    private final int maxFailuresPerClient;
    private final Duration window;

    @Autowired
    public LoginAttemptLimiter(Clock clock) {
        this(clock, DEFAULT_MAX_FAILURES_PER_EMAIL, DEFAULT_MAX_FAILURES_PER_CLIENT, DEFAULT_WINDOW);
    }

    LoginAttemptLimiter(Clock clock, int maxFailuresPerEmail, int maxFailuresPerClient, Duration window) {
        this.clock = clock;
        this.maxFailuresPerEmail = maxFailuresPerEmail;
        this.maxFailuresPerClient = maxFailuresPerClient;
        this.window = window;
    }

    /** Throws {@link TooManyAttemptsException} if either the email or the client is currently blocked. */
    public synchronized void checkAllowed(String email, String clientAddress) {
        Instant now = clock.instant();
        long retryAfter = Math.max(
                secondsUntilAllowed(emailKey(email), maxFailuresPerEmail, now),
                secondsUntilAllowed(clientKey(clientAddress), maxFailuresPerClient, now));
        if (retryAfter > 0) {
            throw new TooManyAttemptsException(retryAfter);
        }
    }

    public synchronized void recordFailure(String email, String clientAddress) {
        Instant now = clock.instant();
        if (failures.size() > PRUNE_THRESHOLD) {
            failures.values().removeIf(attempts -> prune(attempts, now).isEmpty());
        }
        failures.computeIfAbsent(emailKey(email), key -> new ArrayDeque<>()).addLast(now);
        failures.computeIfAbsent(clientKey(clientAddress), key -> new ArrayDeque<>()).addLast(now);
    }

    /** A successful login clears that email's failures (the client's count is kept). */
    public synchronized void recordSuccess(String email) {
        failures.remove(emailKey(email));
    }

    private long secondsUntilAllowed(String key, int maxFailures, Instant now) {
        Deque<Instant> attempts = failures.get(key);
        if (attempts == null || prune(attempts, now).size() < maxFailures) {
            return 0;
        }
        Instant oldestCounted = attempts.peekFirst();
        return Math.max(1, Duration.between(now, oldestCounted.plus(window)).toSeconds());
    }

    private Deque<Instant> prune(Deque<Instant> attempts, Instant now) {
        Instant cutoff = now.minus(window);
        while (!attempts.isEmpty() && !attempts.peekFirst().isAfter(cutoff)) {
            attempts.pollFirst();
        }
        return attempts;
    }

    private static String emailKey(String email) {
        return "email:" + email;
    }

    private static String clientKey(String clientAddress) {
        return "client:" + clientAddress;
    }
}
