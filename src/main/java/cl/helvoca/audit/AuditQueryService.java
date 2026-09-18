package cl.helvoca.audit;

import cl.helvoca.security.TenantProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuditQueryService {
    private final AuditLogRepository repository;
    private final TenantProvider tenantProvider;

    public AuditQueryService(AuditLogRepository repository, TenantProvider tenantProvider) {
        this.repository = repository;
        this.tenantProvider = tenantProvider;
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> recent() {
        UUID businessId = tenantProvider.requireBusinessId();
        return repository.findTop200ByBusinessIdOrderByCreatedAtDesc(businessId)
                .stream()
                .map(AuditLogResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> search(
            String actor,
            String action,
            String resourceType,
            Instant fromAt,
            Instant toAt) {
        UUID businessId = tenantProvider.requireBusinessId();
        String normalizedActor = blankToNull(actor);
        String normalizedAction = upperOrNull(action);
        String normalizedResourceType = upperOrNull(resourceType);

        if (fromAt != null && toAt != null && !fromAt.isBefore(toAt)) {
            throw new IllegalArgumentException("El rango de fechas de auditoría no es válido");
        }

        return repository.search(
                        businessId,
                        normalizedActor,
                        normalizedAction,
                        normalizedResourceType,
                        fromAt,
                        toAt,
                        PageRequest.of(0, 200))
                .stream()
                .map(AuditLogResponse::from)
                .toList();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private static String upperOrNull(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }
}
