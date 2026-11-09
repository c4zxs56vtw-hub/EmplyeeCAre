package com.employeecare.api.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class PublicEndpointRateLimitFilter extends OncePerRequestFilter {

    static final int MAX_ANONYMOUS_CREATE_PER_WINDOW = 5;
    static final int MAX_PUBLIC_AUTH_OR_TRACKING_PER_WINDOW = 20;
    static final long WINDOW_DURATION_MILLIS = 60_000L;
    private static final int MAX_TRACKED_KEYS = 10_000;

    private static final String ANONYMOUS_CREATE_PATH = "/public/tickets/anonymous";

    private static final Set<String> OTHER_PUBLIC_POST_PATHS = Set.of(
            "/auth/login",
            "/auth/activate-account",
            "/auth/forgot-password",
            "/public/tickets/anonymous/lookup",
            "/public/tickets/anonymous/review",
            "/public/tickets/anonymous/close"
    );

    private final ConcurrentHashMap<String, WindowCounter> counters =
            new ConcurrentHashMap<>();

    private final Clock clock;

    public PublicEndpointRateLimitFilter() {
        this(Clock.systemUTC());
    }

    PublicEndpointRateLimitFilter(Clock clock) {
        this.clock = clock;
    }

    private static final class WindowCounter {
        private long windowStartMillis;
        private final AtomicInteger count;

        private WindowCounter(long windowStartMillis) {
            this.windowStartMillis = windowStartMillis;
            this.count = new AtomicInteger(0);
        }

        synchronized int incrementAndGet(long nowMillis) {
            if (nowMillis - windowStartMillis >= WINDOW_DURATION_MILLIS) {
                windowStartMillis = nowMillis;
                count.set(1);
                return 1;
            }
            return count.incrementAndGet();
        }

        synchronized boolean isExpired(long nowMillis) {
            return nowMillis - windowStartMillis >= WINDOW_DURATION_MILLIS;
        }
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = normalizePath(request);
        int limit;
        String bucketCategory;

        if (ANONYMOUS_CREATE_PATH.equals(path)) {
            limit = MAX_ANONYMOUS_CREATE_PER_WINDOW;
            bucketCategory = "ANONYMOUS_CREATE";
        } else if (OTHER_PUBLIC_POST_PATHS.contains(path)) {
            limit = MAX_PUBLIC_AUTH_OR_TRACKING_PER_WINDOW;
            bucketCategory = "PUBLIC_ACTION:" + path;
        } else {
            filterChain.doFilter(request, response);
            return;
        }

        long nowMillis = clock.millis();
        evictExpiredIfNecessary(nowMillis);

        String clientIp = resolveClientIp(request);
        String key = clientIp + "|" + bucketCategory;

        WindowCounter counter = counters.computeIfAbsent(
                key,
                ignored -> new WindowCounter(nowMillis)
        );

        int currentCount = counter.incrementAndGet(nowMillis);

        if (currentCount > limit) {
            writeTooManyRequests(request, response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String normalizePath(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        if (servletPath != null && !servletPath.isBlank()) {
            return servletPath;
        }

        String uri = request.getRequestURI();
        if (uri == null) {
            return "";
        }

        String contextPath = request.getContextPath();
        if (contextPath != null
                && !contextPath.isBlank()
                && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }

        return uri;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        return (remoteAddr == null || remoteAddr.isBlank())
                ? "unknown"
                : remoteAddr.trim();
    }

    private void evictExpiredIfNecessary(long nowMillis) {
        if (counters.size() < MAX_TRACKED_KEYS) {
            return;
        }
        counters.entrySet().removeIf(
                entry -> entry.getValue().isExpired(nowMillis)
        );
    }

    private void writeTooManyRequests(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.RETRY_AFTER, "60");

        String path = request.getRequestURI() != null
                ? request.getRequestURI().replace("\"", "")
                : "";

        String json = """
                {"timestamp":"%s","status":429,"error":"Too Many Requests","message":"Trop de requêtes. Veuillez patienter une minute avant de réessayer.","path":"%s"}"""
                .formatted(
                        OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC),
                        path
                );

        response.getWriter().write(json);
    }
}
