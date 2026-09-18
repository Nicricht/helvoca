package cl.helvoca.audit;

import cl.helvoca.security.TenantProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
}
