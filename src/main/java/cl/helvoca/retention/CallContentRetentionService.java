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
public class CallContentRetentionService {
    private final JdbcTemplate jdbc;
    private final TenantProvider tenantProvider;
    private final Clock clock;

    @Autowired
    public CallContentRetentionService(JdbcTemplate jdbc, TenantProvider tenantProvider) {
        this(jdbc, tenantProvider, Clock.systemUTC());
    }

    CallContentRetentionService(JdbcTemplate jdbc, TenantProvider tenantProvider, Clock clock) {
        this.jdbc = jdbc;
        this.tenantProvider = tenantProvider;
        this.clock = clock;
    }

    @Transactional
    public Result purgeExpiredContentForCurrentTenant() {
        UUID businessId = tenantProvider.requireBusinessId();
        Instant cutoff = DataRetentionPolicy.cutoffs(clock.instant()).callContentBefore();

        int transcripts = jdbc.update("""
                DELETE FROM call_transcript
                WHERE call_id IN (
                    SELECT c.id
                    FROM call_session c
                    WHERE c.business_id = ?
                      AND c.ended_at IS NOT NULL
                      AND c.ended_at < ?
                      AND NOT EXISTS (
                          SELECT 1
                          FROM retention_legal_hold h
                          WHERE h.business_id = c.business_id
                            AND h.target_type = 'CALL_SESSION'
                            AND h.target_id = c.id
                            AND h.released_at IS NULL
                      )
                )
                """, businessId, cutoff);

        int summaries = jdbc.update("""
                DELETE FROM call_summary
                WHERE call_id IN (
                    SELECT c.id
                    FROM call_session c
                    WHERE c.business_id = ?
                      AND c.ended_at IS NOT NULL
                      AND c.ended_at < ?
                      AND NOT EXISTS (
                          SELECT 1
                          FROM retention_legal_hold h
                          WHERE h.business_id = c.business_id
                            AND h.target_type = 'CALL_SESSION'
                            AND h.target_id = c.id
                            AND h.released_at IS NULL
                      )
                )
                """, businessId, cutoff);

        int actions = jdbc.update("""
                DELETE FROM call_action
                WHERE call_id IN (
                    SELECT c.id
                    FROM call_session c
                    WHERE c.business_id = ?
                      AND c.ended_at IS NOT NULL
                      AND c.ended_at < ?
                      AND NOT EXISTS (
                          SELECT 1
                          FROM retention_legal_hold h
                          WHERE h.business_id = c.business_id
                            AND h.target_type = 'CALL_SESSION'
                            AND h.target_id = c.id
                            AND h.released_at IS NULL
                      )
                )
                """, businessId, cutoff);

        return new Result(cutoff, transcripts, summaries, actions);
    }

    public record Result(
            Instant cutoff,
            int transcriptsDeleted,
            int summariesDeleted,
            int actionsDeleted
    ) {}
}
