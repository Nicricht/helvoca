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
public class CallSessionRetentionService {
    private final JdbcTemplate jdbc;
    private final TenantProvider tenantProvider;
    private final Clock clock;

    @Autowired
    public CallSessionRetentionService(JdbcTemplate jdbc, TenantProvider tenantProvider) {
        this(jdbc, tenantProvider, Clock.systemUTC());
    }

    CallSessionRetentionService(JdbcTemplate jdbc, TenantProvider tenantProvider, Clock clock) {
        this.jdbc = jdbc;
        this.tenantProvider = tenantProvider;
        this.clock = clock;
    }

    @Transactional
    public Result purgeExpiredSessionsForCurrentTenant() {
        UUID businessId = tenantProvider.requireBusinessId();
        Instant cutoff = DataRetentionPolicy.cutoffs(clock.instant()).callSessionsBefore();

        int actionsDeleted = jdbc.update("""
                DELETE FROM call_action a
                WHERE a.call_id IN (
                    SELECT c.id
                    FROM call_session c
                    WHERE c.business_id = ?
                      AND c.ended_at IS NOT NULL
                      AND c.ended_at < ?
                      AND NOT EXISTS (
                          SELECT 1 FROM business_request r WHERE r.call_id = c.id
                      )
                      AND NOT EXISTS (
                          SELECT 1 FROM unanswered_question q WHERE q.call_id = c.id
                      )
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

        int sessionsDeleted = jdbc.update("""
                DELETE FROM call_session c
                WHERE c.business_id = ?
                  AND c.ended_at IS NOT NULL
                  AND c.ended_at < ?
                  AND NOT EXISTS (
                      SELECT 1 FROM business_request r WHERE r.call_id = c.id
                  )
                  AND NOT EXISTS (
                      SELECT 1 FROM unanswered_question q WHERE q.call_id = c.id
                  )
                  AND NOT EXISTS (
                      SELECT 1
                      FROM retention_legal_hold h
                      WHERE h.business_id = c.business_id
                        AND h.target_type = 'CALL_SESSION'
                        AND h.target_id = c.id
                        AND h.released_at IS NULL
                  )
                """, businessId, cutoff);

        return new Result(cutoff, actionsDeleted, sessionsDeleted);
    }

    public record Result(
            Instant cutoff,
            int actionsDeleted,
            int sessionsDeleted
    ) {}
}
