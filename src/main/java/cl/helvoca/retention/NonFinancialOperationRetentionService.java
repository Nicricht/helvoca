package cl.helvoca.retention;

import cl.helvoca.security.TenantProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class NonFinancialOperationRetentionService {
    private final JdbcTemplate jdbc;
    private final TenantProvider tenantProvider;
    private final Clock clock;

    @Autowired
    public NonFinancialOperationRetentionService(JdbcTemplate jdbc, TenantProvider tenantProvider) {
        this(jdbc, tenantProvider, Clock.systemUTC());
    }

    NonFinancialOperationRetentionService(JdbcTemplate jdbc, TenantProvider tenantProvider, Clock clock) {
        this.jdbc = jdbc;
        this.tenantProvider = tenantProvider;
        this.clock = clock;
    }

    @Transactional
    public Result purgeExpiredUnprojectedOperationsForCurrentTenant() {
        UUID businessId = tenantProvider.requireBusinessId();
        Instant cutoff = DataRetentionPolicy.cutoffs(clock.instant()).operationsBefore();

        int deleted = jdbc.update("""
                DELETE FROM business_operation o
                WHERE o.business_id = ?
                  AND o.type <> 'PAYMENT'
                  AND o.status IN ('COMPLETED', 'CANCELLED', 'EXPIRED', 'FAILED')
                  AND o.updated_at < ?
                  AND NOT EXISTS (SELECT 1 FROM business_order x WHERE x.operation_id = o.id)
                  AND NOT EXISTS (SELECT 1 FROM business_quote x WHERE x.operation_id = o.id)
                  AND NOT EXISTS (SELECT 1 FROM business_lead x WHERE x.operation_id = o.id)
                  AND NOT EXISTS (SELECT 1 FROM business_request x WHERE x.operation_id = o.id)
                  AND NOT EXISTS (SELECT 1 FROM booking x WHERE x.operation_id = o.id)
                  AND NOT EXISTS (SELECT 1 FROM business_delivery x WHERE x.operation_id = o.id)
                  AND NOT EXISTS (
                      SELECT 1
                      FROM business_payment x
                      WHERE x.operation_id = o.id
                         OR x.target_operation_id = o.id
                  )
                  AND NOT EXISTS (SELECT 1 FROM outbound_message x WHERE x.operation_id = o.id)
                  AND NOT EXISTS (SELECT 1 FROM persistent_job x WHERE x.operation_id = o.id)
                  AND NOT EXISTS (
                      SELECT 1
                      FROM retention_legal_hold h
                      WHERE h.business_id = o.business_id
                        AND h.target_type = 'BUSINESS_OPERATION'
                        AND h.target_id = o.id
                        AND h.released_at IS NULL
                  )
                """, businessId, cutoff);

        return new Result(cutoff, deleted);
    }

    public record Result(Instant cutoff, int operationsDeleted) {}
}
