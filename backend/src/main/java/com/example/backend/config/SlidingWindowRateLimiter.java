package com.example.backend.config;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory лимит: не более {@code maxRequests} событий на ключ за скользящий интервал {@code window}.
 */
public final class SlidingWindowRateLimiter {

    private final int maxRequests;
    private final long windowMs;
    private final ConcurrentHashMap<String, Deque<Long>> buckets = new ConcurrentHashMap<>();

    public SlidingWindowRateLimiter(int maxRequests, Duration window) {
        if (maxRequests < 1) {
            throw new IllegalArgumentException("maxRequests must be >= 1");
        }
        this.maxRequests = maxRequests;
        this.windowMs = window.toMillis();
        if (this.windowMs < 1L) {
            throw new IllegalArgumentException("window must be positive");
        }
    }

    /**
     * @return true, если запрос разрешён; false, если лимит исчерпан
     */
    public boolean tryAcquire(String key) {
        long now = System.currentTimeMillis();
        Deque<Long> dq = buckets.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (dq) {
            while (!dq.isEmpty() && dq.peekFirst() < now - windowMs) {
                dq.pollFirst();
            }
            if (dq.size() >= maxRequests) {
                return false;
            }
            dq.addLast(now);
            return true;
        }
    }

    /**
     * Секунды до появления слота (после отказа {@link #tryAcquire}).
     */
    public long retryAfterSeconds(String key) {
        long now = System.currentTimeMillis();
        Deque<Long> dq = buckets.get(key);
        if (dq == null) {
            return 1L;
        }
        synchronized (dq) {
            if (dq.isEmpty()) {
                return 1L;
            }
            long oldest = dq.peekFirst();
            long waitMs = oldest + windowMs - now;
            return Math.max(1L, (waitMs + 999L) / 1000L);
        }
    }
}
