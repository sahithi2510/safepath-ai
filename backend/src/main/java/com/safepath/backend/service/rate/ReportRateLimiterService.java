package com.safepath.backend.service.rate;

import com.safepath.backend.exception.SafePathException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe sliding-window rate limiter for community report submissions.
 * Prevents spam, denial-of-service, and false incident flooding.
 *
 * Policy: At most 5 reports per user within a rolling 10-minute window (600,000 ms).
 */
@Service
@Slf4j
public class ReportRateLimiterService {

    public static final int MAX_REPORTS_PER_WINDOW = 5;
    public static final long WINDOW_DURATION_MS = 10 * 60 * 1000L; // 10 minutes

    private final Map<UUID, Deque<Long>> userSubmissions = new ConcurrentHashMap<>();

    /**
     * Check if a user is permitted to submit a report, recording the submission timestamp if allowed.
     *
     * @param userId unique user identifier
     * @throws SafePathException with HTTP 429 TOO_MANY_REQUESTS if limit exceeded
     */
    public synchronized void checkAndRecord(UUID userId) {
        long now = System.currentTimeMillis();
        Deque<Long> timestamps = userSubmissions.computeIfAbsent(userId, k -> new ArrayDeque<>());

        // Evict timestamps outside the rolling window
        long windowStart = now - WINDOW_DURATION_MS;
        while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
            timestamps.pollFirst();
        }

        if (timestamps.size() >= MAX_REPORTS_PER_WINDOW) {
            long oldestTimestamp = timestamps.peekFirst();
            long retryAfterSeconds = Math.max(1, (oldestTimestamp + WINDOW_DURATION_MS - now) / 1000);
            log.warn("Rate limit exceeded for user {}: {} submissions in current window", userId, timestamps.size());
            throw new SafePathException(
                    "Rate limit exceeded: You may submit at most " + MAX_REPORTS_PER_WINDOW
                            + " reports per 10 minutes. Please wait " + retryAfterSeconds + " seconds before retrying.",
                    HttpStatus.TOO_MANY_REQUESTS
            );
        }

        timestamps.addLast(now);
    }

    /**
     * Clear submission history for a specific user (useful for testing).
     */
    public synchronized void reset(UUID userId) {
        userSubmissions.remove(userId);
    }

    /**
     * Clear all submission history (useful for test suites).
     */
    public synchronized void resetAll() {
        userSubmissions.clear();
    }
}
