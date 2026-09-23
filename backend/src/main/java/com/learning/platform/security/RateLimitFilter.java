package com.learning.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rate-limits {@code /api/auth/login} and {@code /api/auth/register} per client IP - without
 * this, both are wide open to brute-force password guessing and to spamming the registration
 * endpoint (each call does a real BCrypt hash, which is deliberately slow, making unthrottled
 * registration a cheap way to burn CPU on the server).
 *
 * <p>Bucketed per-IP in an in-process map via Bucket4j's token-bucket algorithm - adequate for a
 * single instance, and explicitly not adequate beyond one: scaling this service horizontally
 * would need the bucket state shared (Bucket4j supports a Redis/Hazelcast-backed
 * {@code ProxyManager} for exactly this), which is real follow-up work, not implemented here to
 * keep the scope of this specific change contained. Noted in the README, not hidden.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final int requestsPerMinute;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RateLimitFilter(@Value("${app.rate-limit.auth-requests-per-minute:10}") int requestsPerMinute) {
        this.requestsPerMinute = requestsPerMinute;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.equals("/api/auth/login") || path.equals("/api/auth/register"));
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String clientIp = request.getRemoteAddr();
        Bucket bucket = buckets.computeIfAbsent(clientIp, ip -> newBucket());

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", 429);
        body.put("error", "Too many requests - please wait a moment and try again");
        objectMapper.writeValue(response.getWriter(), body);
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.classic(requestsPerMinute, io.github.bucket4j.Refill.greedy(requestsPerMinute, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    /**
     * Test-only hook. Integration tests share one Spring context (and therefore one instance of
     * this filter, with its in-memory {@code buckets} map) across many test classes, all of which
     * call register/login through MockMvc's fixed "127.0.0.1" remote address - without clearing
     * state between tests, unrelated tests would exhaust the same per-IP bucket. See
     * IntegrationTestBase's per-test @BeforeEach.
     */
    public void resetForTests() {
        buckets.clear();
    }
}
