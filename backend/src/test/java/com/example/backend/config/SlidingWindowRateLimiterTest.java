package com.example.backend.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlidingWindowRateLimiterTest {

    @Test
    void allowsUpToMaxThenDeniesUntilWindowSlides() throws InterruptedException {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(2, Duration.ofMillis(200));
        assertTrue(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("a"));
        assertFalse(limiter.tryAcquire("a"));
        Thread.sleep(210);
        assertTrue(limiter.tryAcquire("a"));
    }

    @Test
    void keysAreIndependent() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(1, Duration.ofMinutes(1));
        assertTrue(limiter.tryAcquire("x"));
        assertFalse(limiter.tryAcquire("x"));
        assertTrue(limiter.tryAcquire("y"));
    }
}
