package cl.helvoca.retention;

import cl.helvoca.security.TenantProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogRetentionService {
    private final JdbcTemplate jdbc;
    private final TenantProvider tenantProvider;

    public AuditLogRetentionService(JdbcTemplate jdbc, TenantProvider tenantProvider) {
        this.jdbc = jdbc;
        this.tenantProvider = tenantProvider;
    }

    @Transactional
    public Result purgeExpiredForCurrentTenant() {
        tenantProvider.requireBusinessId();

        Integer deleted = jdbc.queryForObject(
                "SELECT public.purge_expired_audit_log_for_current_tenant()",
                Integer.class);

        return new Result(deleted == null ? 0 : deleted);
    }

    public record Result(int auditLogsDeleted) {}
}
