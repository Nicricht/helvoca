package cl.helvoca.audit;

import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class AuditService {
    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) { this.repository = repository; }

    public void success(UUID businessId, String action, String resourceType, UUID resourceId) {
        AuditLog log = new AuditLog();
        log.setBusinessId(businessId);
        log.setAction(action);
        log.setResourceType(resourceType);
        log.setResourceId(resourceId);
        log.setResult("SUCCESS");
        repository.save(log);
    }
}
