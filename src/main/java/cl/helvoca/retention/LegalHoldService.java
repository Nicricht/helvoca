package cl.helvoca.retention;

import cl.helvoca.security.TenantProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class LegalHoldService {
    private final JdbcTemplate jdbc;
    private final TenantProvider tenantProvider;

    public LegalHoldService(JdbcTemplate jdbc, TenantProvider tenantProvider) {
        this.jdbc = jdbc;
        this.tenantProvider = tenantProvider;
    }

    @Transactional(readOnly = true)
    public boolean hasActiveHold(TargetType targetType, UUID targetId) {
        if (targetType == null) throw new IllegalArgumentException("targetType is required");
        if (targetId == null) throw new IllegalArgumentException("targetId is required");

        UUID businessId = tenantProvider.requireBusinessId();

        Boolean held = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM retention_legal_hold
                    WHERE business_id = ?
                      AND target_type = ?
                      AND target_id = ?
                      AND released_at IS NULL
                )
                """,
                Boolean.class,
                businessId,
                targetType.name(),
                targetId);

        return Boolean.TRUE.equals(held);
    }

    public enum TargetType {
        CUSTOMER,
        CALL_SESSION,
        MESSAGING_CONVERSATION,
        BUSINESS_OPERATION,
        AUDIT_LOG,
        OUTBOUND_MESSAGE
    }
}
