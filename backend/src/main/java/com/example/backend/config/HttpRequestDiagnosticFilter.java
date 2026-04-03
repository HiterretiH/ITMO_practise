package com.example.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Логирует каждый запрос до разбора multipart и после ответа. Если видите только строку «&gt;&gt;&gt;» без «&lt;&lt;&lt;» —
 * обработка ещё идёт (или завис приём тела).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HttpRequestDiagnosticFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HttpRequestDiagnosticFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long t0 = System.nanoTime();
        String uri = request.getRequestURI();
        String q = request.getQueryString();
        if (q != null) {
            uri = uri + "?" + q;
        }
        String len = request.getContentLengthLong() >= 0
                ? Long.toString(request.getContentLengthLong())
                : "chunked/unknown";
        log.info(
                ">>> HTTP {} {} Content-Length={} from {}",
                request.getMethod(),
                uri,
                len,
                request.getRemoteAddr());
        try {
            filterChain.doFilter(request, response);
        } finally {
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            log.info(
                    "<<< HTTP {} {} status={} {}ms",
                    request.getMethod(),
                    uri,
                    response.getStatus(),
                    ms);
        }
    }
}
