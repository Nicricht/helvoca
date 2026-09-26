package cl.helvoca.audit;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AuditService {
    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) { this.repository = repository; }

    public void success(UUID businessId, String action, String resourceType, UUID resourceId) {
        repository.save(successLog(businessId, action, resourceType, resourceId));
    }

    public void humanSuccess(UUID businessId, String action, String resourceType, UUID resourceId) {
        humanSuccess(businessId, action, resourceType, resourceId, null, null);
    }

    public void platformHumanSuccess(
            UUID businessId,
            String action,
            String resourceType,
            UUID resourceId,
            Map<String, Object> beforeState,
            Map<String, Object> afterState) {
        Jwt jwt = requireAuthenticatedJwt();
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles == null || !roles.contains("PLATFORM_ADMIN")) {
            throw new AccessDeniedException("Authenticated user is not a platform administrator");
        }

        AuditLog log = successLog(businessId, action, resourceType, resourceId);
        log.setActorType("HUMAN");
        log.setActorUserId(requireUuid(jwt.getSubject(), "Token has invalid subject"));
        log.setActorName(requireClaim(jwt, "name"));
        log.setActorEmail(requireClaim(jwt, "email"));
        log.setActorRole("PLATFORM_ADMIN");
        log.setBeforeState(beforeState);
        log.setAfterState(afterState);
        repository.save(log);
    }

    public void humanSuccess(
            UUID businessId,
            String action,
            String resourceType,
            UUID resourceId,
            Map<String, Object> beforeState,
            Map<String, Object> afterState) {
        Jwt jwt = requireAuthenticatedJwt();
        UUID tokenBusinessId = requireUuid(jwt.getClaimAsString("business_id"), "Token has invalid business_id");
        if (!businessId.equals(tokenBusinessId)) {
            throw new AccessDeniedException("Authenticated user does not belong to this business");
        }

        AuditLog log = successLog(businessId, action, resourceType, resourceId);
        log.setActorType("HUMAN");
        log.setActorUserId(requireUuid(jwt.getSubject(), "Token has invalid subject"));
        log.setActorName(requireClaim(jwt, "name"));
        log.setActorEmail(requireClaim(jwt, "email"));
        log.setActorRole(requireBusinessRole(jwt));
        log.setBeforeState(beforeState);
        log.setAfterState(afterState);
        repository.save(log);
    }

    private static AuditLog successLog(UUID businessId, String action, String resourceType, UUID resourceId) {
        AuditLog log = new AuditLog();
        log.setBusinessId(businessId);
        log.setAction(action);
        log.setResourceType(resourceType);
        log.setResourceId(resourceId);
        log.setResult("SUCCESS");
        return log;
    }

    private static Jwt requireAuthenticatedJwt() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new AccessDeniedException("Authenticated JWT required for human audit");
        }
        return jwt;
    }

    private static UUID requireUuid(String value, String message) {
        if (value == null || value.isBlank()) throw new AccessDeniedException(message);
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new AccessDeniedException(message);
        }
    }

    private static String requireClaim(Jwt jwt, String claim) {
        String value = jwt.getClaimAsString(claim);
        if (value == null || value.isBlank()) {
            throw new AccessDeniedException("Token has no " + claim + " claim");
        }
        return value;
    }

    private static String requireBusinessRole(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles == null) throw new AccessDeniedException("Token has no roles claim");
        if (roles.contains("BUSINESS_ADMIN")) return "BUSINESS_ADMIN";
        if (roles.contains("OPERATOR")) return "OPERATOR";
        throw new AccessDeniedException("Token has no business role");
    }
}
