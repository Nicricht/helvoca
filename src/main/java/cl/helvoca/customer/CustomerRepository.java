package cl.helvoca.customer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    List<Customer> findAllByBusinessIdOrderByCreatedAtDesc(UUID businessId);
    Optional<Customer> findByIdAndBusinessId(UUID id, UUID businessId);

    /**
     * Security-sensitive resolver used by channel automation. It returns a
     * customer only when the normalized phone has an explicit verified identity
     * and that identity is unambiguous inside the tenant. Otherwise it fails
     * closed with Optional.empty().
     */
    @Query(value = """
            WITH requested AS (
                SELECT CASE
                    WHEN btrim(CAST(:phone AS text)) LIKE '+%' THEN
                        '+' || regexp_replace(substr(btrim(CAST(:phone AS text)), 2), '[^0-9]', '', 'g')
                    WHEN btrim(CAST(:phone AS text)) LIKE '00%' THEN
                        '+' || regexp_replace(substr(btrim(CAST(:phone AS text)), 3), '[^0-9]', '', 'g')
                    ELSE ''
                END AS normalized_phone
            ), candidates AS (
                SELECT DISTINCT ci.customer_id
                FROM customer_identity ci, requested r
                WHERE ci.business_id = :businessId
                  AND ci.identity_type = 'PHONE'
                  AND ci.verification_status IN ('CUSTOMER_VERIFIED','MANUAL_VERIFIED')
                  AND ci.normalized_value = r.normalized_phone
            )
            SELECT c.*
            FROM customer c
            JOIN candidates candidate ON candidate.customer_id = c.id
            WHERE c.business_id = :businessId
              AND (SELECT count(*) FROM candidates) = 1
            LIMIT 1
            """, nativeQuery = true)
    Optional<Customer> findFirstByBusinessIdAndPhone(@Param("businessId") UUID businessId,
                                                       @Param("phone") String phone);

    /** Explicit administrative lookup. It never grants channel identity. */
    @Query(value = """
            SELECT * FROM customer
            WHERE business_id = :businessId AND phone = :phone
            ORDER BY created_at DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<Customer> findFirstRawByBusinessIdAndPhone(@Param("businessId") UUID businessId,
                                                          @Param("phone") String phone);
}
