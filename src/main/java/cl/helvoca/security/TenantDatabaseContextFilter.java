package cl.helvoca.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Installs an explicit database scope for every HTTP request.
 *
 * Authenticated business users are tenant-scoped from the signed JWT. Platform
 * administration and authenticated provider ingress use SYSTEM scope. Every
 * other request receives DENIED scope, so protected tenant tables fail closed.
 */
@Component
public class TenantDatabaseContextFilter extends OncePerRequestFilter {
    private final TenantDatabaseContext databaseContext;

    public TenantDatabaseContextFilter(TenantDatabaseContext databaseContext) {
        this.databaseContext = databaseContext;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try (TenantDatabaseContext.Scope ignored = scopeFor(request)) {
            filterChain.doFilter(request, response);
        }
    }

    private TenantDatabaseContext.Scope scopeFor(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            boolean platformAdmin = authentication.getAuthorities().stream()
                    .anyMatch(a -> "ROLE_PLATFORM_ADMIN".equals(a.getAuthority()));
            if (platformAdmin) return databaseContext.useSystem();

            Object principal = authentication.getPrincipal();
            if (principal instanceof Jwt jwt) {
                String businessId = jwt.getClaimAsString("business_id");
                if (businessId != null && !businessId.isBlank()) {
                    try {
                        return databaseContext.useTenant(UUID.fromString(businessId));
                    } catch (IllegalArgumentException ignored) {
                        return databaseContext.deny();
                    }
                }
            }
        }

        String path = request.getRequestURI();
        if (isPrivilegedPublicIngress(path)) return databaseContext.useSystem();
        return databaseContext.deny();
    }

    private static boolean isPrivilegedPublicIngress(String path) {
        if (path == null) return false;
        return path.equals("/api/v1/auth/login")
                || path.equals("/api/v1/auth/register")
                || path.startsWith("/api/v1/auth/invitations/")
                || path.startsWith("/webhooks/")
                || path.startsWith("/ws/");
    }
}
