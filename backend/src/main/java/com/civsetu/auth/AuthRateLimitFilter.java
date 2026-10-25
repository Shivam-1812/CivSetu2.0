package com.civsetu.auth;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Per-IP request limits on the public auth endpoints (brute force and sign-up spam).
 * Buckets live in memory and expire when idle; fine for a single instance.
 * Not a @Component: it is added to the security filter chain only.
 */
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private final Map<String, Integer> perMinuteByPath;
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10))
            .maximumSize(100_000)
            .build();

    public AuthRateLimitFilter(AuthProperties.RateLimit limits) {
        this.perMinuteByPath = Map.of(
                "/api/v1/auth/login", limits.loginPerMinute(),
                "/api/v1/auth/register", limits.registerPerMinute(),
                "/api/v1/auth/refresh", limits.refreshPerMinute());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !perMinuteByPath.containsKey(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        int perMinute = perMinuteByPath.get(path);
        Bucket bucket = buckets.get(path + "|" + request.getRemoteAddr(), key -> Bucket.builder()
                .addLimit(limit -> limit.capacity(perMinute).refillGreedy(perMinute, Duration.ofMinutes(1)))
                .build());

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            chain.doFilter(request, response);
            return;
        }
        long retryAfterSeconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1);
        // Written directly: this runs before Spring MVC, so the exception handlers are not involved
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429,"
                + "\"detail\":\"Too many attempts. Try again in " + retryAfterSeconds + " seconds.\","
                + "\"code\":\"RATE_LIMITED\"}");
    }
}
