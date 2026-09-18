package cl.helvoca.audit;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    List<AuditLog> findTop200ByBusinessIdOrderByCreatedAtDesc(UUID businessId);

    @Query("""
            SELECT a
              FROM AuditLog a
             WHERE a.businessId = :businessId
               AND (:actor IS NULL
                    OR LOWER(COALESCE(a.actorName, '')) LIKE LOWER(CONCAT('%', :actor, '%'))
                    OR LOWER(COALESCE(a.actorEmail, '')) LIKE LOWER(CONCAT('%', :actor, '%')))
               AND (:action IS NULL OR a.action = :action)
               AND (:resourceType IS NULL OR a.resourceType = :resourceType)
               AND (:fromAt IS NULL OR a.createdAt >= :fromAt)
               AND (:toAt IS NULL OR a.createdAt < :toAt)
             ORDER BY a.createdAt DESC
            """)
    List<AuditLog> search(
            @Param("businessId") UUID businessId,
            @Param("actor") String actor,
            @Param("action") String action,
            @Param("resourceType") String resourceType,
            @Param("fromAt") Instant fromAt,
            @Param("toAt") Instant toAt,
            Pageable pageable);

    List<AuditLog> findByBusinessIdAndResourceTypeAndResourceIdOrderByCreatedAtAsc(
            UUID businessId,
            String resourceType,
            UUID resourceId);
}
