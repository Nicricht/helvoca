package cl.helvoca.security;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

@Component
public class ApiRateLimitFilter extends OncePerRequestFilter {
    private final ApiRateLimitProperties properties;
    private final DistributedRateLimiter limiter;
    private final MeterRegistry meters;

    public ApiRateLimitFilter(ApiRateLimitProperties properties,
                              DistributedRateLimiter limiter,
                              MeterRegistry meters) {
        this.properties = properties;
        this.limiter = limiter;
        this.meters = meters;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Policy policy = policyFor(request);
        if (!properties.isEnabled() || policy == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String identity = policy.tenantScoped() ? authenticatedIdentity(request) : clientAddress(request);
        String bucketKey = policy.name() + ":" + sha256(identity);
        Instant now = Instant.now();
        DistributedRateLimiter.Result result = limiter.consume(bucketKey, policy.limit(), policy.windowSeconds(), now);

        response.setHeader("X-RateLimit-Limit", Integer.toString(policy.limit()));
        response.setHeader("X-RateLimit-Remaining", Integer.toString(result.remaining()));
        response.setHeader("X-RateLimit-Reset", Long.toString(result.resetEpochSeconds()));

        if (!result.allowed()) {
            long retryAfter = Math.max(1, result.resetEpochSeconds() - now.getEpochSecond());
            response.setStatus(429);
            response.setHeader("Retry-After", Long.toString(retryAfter));
            response.setContentType("application/json");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            meters.counter("helvoca.api.rate_limit.rejected", "policy", policy.name()).increment();
            response.getWriter().write("{\"error\":\"RATE_LIMITED\",\"message\":\"Too many requests\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private Policy policyFor(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return null;
        String path = request.getRequestURI();
        if (path == null || !path.startsWith("/api/v1/")) return null;

        if ("/api/v1/auth/login".equals(path)) return from("login", properties.getLogin(), false);
        if ("/api/v1/auth/register".equals(path)) return from("register", properties.getRegister(), false);
        if (path.startsWith("/api/v1/public/")) return from("public", properties.getPublicApi(), false);
        if (path.startsWith("/api/v1/billing/")) return from("billing", properties.getBilling(), true);
        return from("authenticated", properties.getAuthenticatedApi(), true);
    }

    private static Policy from(String name, ApiRateLimitProperties.Limit limit, boolean tenantScoped) {
        return new Policy(name, limit.getRequests(), limit.getWindowSeconds(), tenantScoped);
    }

    private String authenticatedIdentity(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt) {
            String businessId = jwt.getToken().getClaimAsString("business_id");
            if (businessId != null && !businessId.isBlank()) return "tenant:" + businessId;
            String subject = jwt.getToken().getSubject();
            if (subject != null && !subject.isBlank()) return "user:" + subject;
        }
        return "ip:" + clientAddress(request);
    }

    static String clientAddress(HttpServletRequest request) {
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) return bounded(realIp.trim());

        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            String first = comma >= 0 ? forwarded.substring(0, comma) : forwarded;
            if (!first.isBlank()) return bounded(first.trim());
        }

        String remote = request.getRemoteAddr();
        return remote == null || remote.isBlank() ? "unknown" : bounded(remote);
    }

    private static String bounded(String value) {
        return value.substring(0, Math.min(128, value.length()));
    }

    static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private record Policy(String name, int limit, int windowSeconds, boolean tenantScoped) {}
}
