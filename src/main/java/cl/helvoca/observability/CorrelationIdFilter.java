package cl.helvoca.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Establishes one safe correlation id for every HTTP request.
 *
 * Client supplied ids are accepted only when they use a bounded low-risk
 * character set. Invalid or missing values are replaced instead of being
 * reflected into logs. The id is echoed in the response and stored in MDC for
 * the lifetime of the request only.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class CorrelationIdFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._:-]{8,128}");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = safeCorrelationId(request.getHeader(HEADER));
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    static String safeCorrelationId(String candidate) {
        if (candidate != null) {
            String trimmed = candidate.trim();
            if (SAFE.matcher(trimmed).matches()) return trimmed;
        }
        return UUID.randomUUID().toString();
    }
}
