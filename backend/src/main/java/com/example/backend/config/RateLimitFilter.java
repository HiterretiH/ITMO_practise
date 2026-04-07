package com.example.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Простой рейт-лимит по клиенту (IP или первый X-Forwarded-For при доверии прокси)
 * только для {@code POST /v1/validate}, чтобы не душить опрос {@code GET .../jobs/{id}}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String BODY =
            "{\"code\":\"RATE_LIMITED\",\"message\":\"Слишком много запросов. Повторите позже.\"}";

    private final boolean enabled;
    private final boolean trustXForwardedFor;
    private final SlidingWindowRateLimiter limiter;

    public RateLimitFilter(
            @Value("${vkr.rate-limit.enabled:true}") boolean enabled,
            @Value("${vkr.rate-limit.trust-x-forwarded-for:false}") boolean trustXForwardedFor,
            @Value("${vkr.rate-limit.max-requests-per-window:60}") int maxRequests,
            @Value("${vkr.rate-limit.window-seconds:60}") long windowSeconds) {
        this.enabled = enabled;
        this.trustXForwardedFor = trustXForwardedFor;
        this.limiter = new SlidingWindowRateLimiter(maxRequests, Duration.ofSeconds(Math.max(1L, windowSeconds)));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!enabled) {
            return true;
        }
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getServletPath();
        return !"/v1/validate".equals(path);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String key = clientKey(request);
        if (limiter.tryAcquire(key)) {
            filterChain.doFilter(request, response);
            return;
        }
        long retry = limiter.retryAfterSeconds(key);
        response.setStatus(429);
        response.setHeader("Retry-After", Long.toString(retry));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(BODY);
    }

    private String clientKey(HttpServletRequest request) {
        if (trustXForwardedFor) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                int comma = xff.indexOf(',');
                return comma > 0 ? xff.substring(0, comma).trim() : xff.trim();
            }
        }
        return request.getRemoteAddr();
    }
}
