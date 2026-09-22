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
public class OutboundMessageContentRetentionService {
    private static final String REDACTED = "[redacted]";

    private final JdbcTemplate jdbc;
    private final TenantProvider tenantProvider;
    private final Clock clock;

    @Autowired
    public OutboundMessageContentRetentionService(JdbcTemplate jdbc, TenantProvider tenantProvider) {
        this(jdbc, tenantProvider, Clock.systemUTC());
    }

    OutboundMessageContentRetentionService(
            JdbcTemplate jdbc,
            TenantProvider tenantProvider,
            Clock clock) {
        this.jdbc = jdbc;
        this.tenantProvider = tenantProvider;
        this.clock = clock;
    }

    @Transactional
    public Result redactExpiredContentForCurrentTenant() {
        UUID businessId = tenantProvider.requireBusinessId();
        Instant cutoff = DataRetentionPolicy.cutoffs(clock.instant()).messageContentBefore();

        int redacted = jdbc.update("""
                UPDATE outbound_message om
                   SET recipient_address = ?,
                       content_text = ?,
                       provider_message_id = NULL,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE om.business_id = ?
                   AND om.status IN ('SENT', 'FAILED', 'CANCELLED', 'BLOCKED')
                   AND om.updated_at < ?
                   AND (
                       om.recipient_address <> ?
                       OR om.content_text <> ?
                       OR om.provider_message_id IS NOT NULL
                   )
                   AND NOT EXISTS (
                       SELECT 1
                       FROM retention_legal_hold h
                       WHERE h.business_id = om.business_id
                         AND h.target_type = 'OUTBOUND_MESSAGE'
                         AND h.target_id = om.id
                         AND h.released_at IS NULL
                   )
                """,
                REDACTED,
                REDACTED,
                businessId,
                cutoff,
                REDACTED,
                REDACTED);

        return new Result(cutoff, redacted);
    }

    public record Result(
            Instant cutoff,
            int outboundMessagesRedacted
    ) {}
}
