package cl.helvoca.retention;

import cl.helvoca.audit.AuditService;
import cl.helvoca.common.ConflictException;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.security.TenantProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CustomerProfileAnonymizationService {
    private final JdbcTemplate jdbc;
    private final TenantProvider tenantProvider;
    private final AuditService auditService;

    public CustomerProfileAnonymizationService(
            JdbcTemplate jdbc,
            TenantProvider tenantProvider,
            AuditService auditService) {
        this.jdbc = jdbc;
        this.tenantProvider = tenantProvider;
        this.auditService = auditService;
    }

    @Transactional
    public Result anonymizeCurrentTenantCustomer(UUID customerId) {
        if (customerId == null) throw new IllegalArgumentException("customerId is required");

        UUID businessId = tenantProvider.requireBusinessId();

        Boolean held = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM retention_legal_hold h
                    WHERE h.business_id = ?
                      AND h.target_type = 'CUSTOMER'
                      AND h.target_id = ?
                      AND h.released_at IS NULL
                )
                """,
                Boolean.class,
                businessId,
                customerId);

        if (Boolean.TRUE.equals(held)) {
            throw new ConflictException("Customer is protected by an active legal hold");
        }

        int customersUpdated = jdbc.update("""
                UPDATE customer
                SET name = NULL,
                    phone = NULL,
                    email = NULL,
                    notes = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND business_id = ?
                  AND NOT EXISTS (
                      SELECT 1
                      FROM retention_legal_hold h
                      WHERE h.business_id = customer.business_id
                        AND h.target_type = 'CUSTOMER'
                        AND h.target_id = customer.id
                        AND h.released_at IS NULL
                  )
                """, customerId, businessId);

        if (customersUpdated != 1) {
            throw new NotFoundException("Customer not found");
        }

        int identitiesDeleted = jdbc.update("""
                DELETE FROM customer_identity
                WHERE business_id = ?
                  AND customer_id = ?
                """, businessId, customerId);

        auditService.humanSuccess(
                businessId,
                "CUSTOMER_PROFILE_ANONYMIZE",
                "CUSTOMER",
                customerId);

        return new Result(customerId, identitiesDeleted);
    }

    public record Result(
            UUID customerId,
            int identitiesDeleted
    ) {}
}
