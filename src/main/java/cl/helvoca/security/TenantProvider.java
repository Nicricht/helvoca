package cl.helvoca.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TenantProvider {
    public UUID requireBusinessId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof Jwt jwt)) {
            throw new AccessDeniedException("Authenticated JWT required");
        }
        String value = jwt.getClaimAsString("business_id");
        if (value == null || value.isBlank()) {
            throw new AccessDeniedException("Token has no business_id claim");
        }
        return UUID.fromString(value);
    }
}
