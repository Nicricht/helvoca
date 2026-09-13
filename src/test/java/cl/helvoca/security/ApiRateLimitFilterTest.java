package cl.helvoca.security;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ApiRateLimitFilterTest {

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void loginLimitReturns429WithRetryAfter() throws Exception {
        ApiRateLimitProperties properties = new ApiRateLimitProperties();
        DistributedRateLimiter limiter = mock(DistributedRateLimiter.class);
        when(limiter.consume(anyString(), eq(20), eq(60), any(Instant.class)))
                .thenReturn(new DistributedRateLimiter.Result(false, 21, 0, Instant.now().getEpochSecond() + 30));

        ApiRateLimitFilter filter = new ApiRateLimitFilter(properties, limiter, new SimpleMeterRegistry());
        HttpServletRequest request = request("POST", "/api/v1/auth/login", "203.0.113.10");
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(response).setStatus(429);
        verify(response).setHeader(eq("Retry-After"), anyString());
        verify(chain, never()).doFilter(any(), any());
        assertTrue(body.toString().contains("RATE_LIMITED"));
    }

    @Test
    void webhookTrafficBypassesGenericRateLimiter() throws Exception {
        ApiRateLimitProperties properties = new ApiRateLimitProperties();
        DistributedRateLimiter limiter = mock(DistributedRateLimiter.class);
        ApiRateLimitFilter filter = new ApiRateLimitFilter(properties, limiter, new SimpleMeterRegistry());
        HttpServletRequest request = request("POST", "/webhooks/v1/twilio/whatsapp", "203.0.113.11");
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verifyNoInteractions(limiter);
        verify(chain).doFilter(request, response);
    }

    @Test
    void authenticatedApiUsesTenantScopedPolicy() throws Exception {
        ApiRateLimitProperties properties = new ApiRateLimitProperties();
        DistributedRateLimiter limiter = mock(DistributedRateLimiter.class);
        when(limiter.consume(startsWith("authenticated:"), eq(600), eq(60), any(Instant.class)))
                .thenReturn(new DistributedRateLimiter.Result(true, 1, 599, Instant.now().getEpochSecond() + 60));

        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(300),
                Map.of("alg", "none"), Map.of("sub", "user-1", "business_id", "tenant-a"));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        ApiRateLimitFilter filter = new ApiRateLimitFilter(properties, limiter, new SimpleMeterRegistry());
        HttpServletRequest request = request("GET", "/api/v1/bookings", "203.0.113.12");
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(limiter).consume(startsWith("authenticated:"), eq(600), eq(60), any(Instant.class));
        verify(chain).doFilter(request, response);
        verify(response).setHeader("X-RateLimit-Remaining", "599");
    }

    @Test
    void clientAddressPrefersRailwayRealIpAndFallsBackToForwardedFor() {
        HttpServletRequest railway = mock(HttpServletRequest.class);
        when(railway.getHeader("X-Real-IP")).thenReturn("198.51.100.7");
        when(railway.getHeader("X-Forwarded-For")).thenReturn("203.0.113.77, 10.0.0.1");
        assertEquals("198.51.100.7", ApiRateLimitFilter.clientAddress(railway));

        HttpServletRequest fallback = mock(HttpServletRequest.class);
        when(fallback.getHeader("X-Forwarded-For")).thenReturn("198.51.100.9, 10.0.0.1");
        assertEquals("198.51.100.9", ApiRateLimitFilter.clientAddress(fallback));
    }

    private static HttpServletRequest request(String method, String uri, String remoteAddr) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn(method);
        when(request.getRequestURI()).thenReturn(uri);
        when(request.getRemoteAddr()).thenReturn(remoteAddr);
        return request;
    }
}
