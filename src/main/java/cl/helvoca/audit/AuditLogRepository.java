package cl.helvoca.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    List<AuditLog> findTop200ByBusinessIdOrderByCreatedAtDesc(UUID businessId);

    List<AuditLog> findByBusinessIdAndResourceTypeAndResourceIdOrderByCreatedAtAsc(
            UUID businessId,
            String resourceType,
            UUID resourceId);
}
