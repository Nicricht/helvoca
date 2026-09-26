package cl.helvoca.operations;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface BusinessOperationRepository extends JpaRepository<BusinessOperation, UUID> {
    Optional<BusinessOperation> findByIdAndBusinessId(UUID id, UUID businessId);

    @Query(value = """
            SELECT * FROM business_operation
            WHERE business_id = :businessId
              AND type = 'REQUEST'
              AND metadata_json ->> 'certificationRunId' = :runId
            ORDER BY created_at ASC
            LIMIT 1
            """, nativeQuery = true)
    Optional<BusinessOperation> findFirstSandboxCertificationJourney(
            @Param("businessId") UUID businessId,
            @Param("runId") String runId);
}
