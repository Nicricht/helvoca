package cl.helvoca.telephony.twilio;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class TwilioCertificationCommandStore {
    private static final Pattern RUN_ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{7,127}$");
    private static final Pattern TOKEN = Pattern.compile("^[a-f0-9-]{36}$");
    private static final Pattern CALL_SID = Pattern.compile("^CA[0-9a-fA-F]{32}$");
    private static final Duration TOKEN_TTL = Duration.ofMinutes(5);

    private final JdbcTemplate jdbc;

    public TwilioCertificationCommandStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean enqueue(String runId, String requestedBy) {
        return enqueue(runId, requestedBy, null);
    }

    public boolean enqueue(String runId, String requestedBy, UUID businessId) {
        if (!validRunId(runId)) return false;
        String actor = requestedBy == null || requestedBy.isBlank()
                ? "unknown"
                : requestedBy.trim().substring(0, Math.min(requestedBy.trim().length(), 180));
        int inserted = businessId == null
                ? jdbc.update("""
                        INSERT INTO twilio_certification_command (run_id, requested_by)
                        VALUES (?, ?) ON CONFLICT (run_id) DO NOTHING
                        """, runId.trim(), actor)
                : jdbc.update("""
                        INSERT INTO twilio_certification_command (run_id, requested_by, business_id)
                        VALUES (?, ?, ?) ON CONFLICT (run_id) DO NOTHING
                        """, runId.trim(), actor, businessId);
        return inserted == 1;
    }

    public Optional<CommandStatus> find(String runId) {
        if (!validRunId(runId)) return Optional.empty();
        List<CommandStatus> rows = jdbc.query("""
                SELECT run_id, status, requested_by, requested_at, claimed_at,
                       expires_at, provider_call_sid, completed_at, failure_reason
                  FROM twilio_certification_command
                 WHERE run_id = ?
                """, TwilioCertificationCommandStore::commandStatus, runId.trim());
        return rows.stream().findFirst();
    }

    public Optional<CommandStatus> findForChatGptAurora(String runId, UUID businessId) {
        if (!validRunId(runId) || !runId.trim().startsWith("chatgpt-aurora-") || businessId == null) {
            return Optional.empty();
        }
        List<CommandStatus> rows = jdbc.query("""
                SELECT run_id, status, requested_by, requested_at, claimed_at,
                       expires_at, provider_call_sid, completed_at, failure_reason
                  FROM twilio_certification_command
                 WHERE run_id = ?
                   AND business_id = ?
                   AND requested_by LIKE 'chatgpt-aurora:%'
                """, TwilioCertificationCommandStore::commandStatus, runId.trim(), businessId);
        return rows.stream().findFirst();
    }

    public boolean isLatencyCertificationProviderCall(String providerCallSid) {
        if (!validCallSid(providerCallSid)) return false;
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM twilio_certification_command
                 WHERE provider_call_sid = ?
                   AND run_id LIKE 'latency-%'
                """, Integer.class, providerCallSid.trim());
        return count != null && count > 0;
    }

    public Optional<ClaimedCommand> claimNext() {
        return claimNextForActorScope(false, null);
    }

    /** One authorized bootstrap command; an uncertain carrier result is never made PENDING again. */
    public Optional<ClaimedCommand> claimExactRunId(String runId, UUID businessId, String actor) {
        if (!validRunId(runId) || businessId == null || !"startup-bootstrap".equals(actor)) {
            return Optional.empty();
        }
        String token = UUID.randomUUID().toString();
        List<ClaimedCommand> rows = jdbc.query("""
                WITH exact_command AS (
                    SELECT c.run_id, c.business_id
                      FROM twilio_certification_command c
                     WHERE c.run_id = ?
                       AND c.business_id = ?
                       AND c.requested_by = ?
                       AND c.status = 'PENDING'
                     FOR UPDATE OF c SKIP LOCKED
                )
                UPDATE twilio_certification_command c
                   SET status = 'CLAIMED', callback_token = ?,
                       claimed_at = CURRENT_TIMESTAMP,
                       expires_at = CURRENT_TIMESTAMP + (? * INTERVAL '1 second')
                  FROM exact_command n
                 WHERE c.run_id = n.run_id AND c.status = 'PENDING'
                RETURNING c.run_id, c.business_id
                "", (rs, rowNum) -> new ClaimedCommand(rs.getString(1), token,
                        rs.getObject(2, UUID.class)),
                runId.trim(), businessId, actor, token, TOKEN_TTL.toSeconds());
        return rows.stream().findFirst();
    }

    public Optional<ClaimedCommand> claimNextForChatGptAurora(UUID businessId) {
        if (businessId == null) return Optional.empty();
        return claimNextForActorScope(true, businessId);
    }

    private Optional<ClaimedCommand> claimNextForActorScope(boolean chatGptAuroraOnly, UUID businessId) {
        String token = UUID.randomUUID().toString();
        String actorPredicate = chatGptAuroraOnly
                ? "c.requested_by LIKE 'chatgpt-aurora:%'"
                : "c.requested_by NOT LIKE 'chatgpt-aurora:%'";
        String businessPredicate = chatGptAuroraOnly ? "AND c.business_id = ?" : "";
        String sql = """
                WITH next_command AS (
                    SELECT c.run_id, c.business_id
                    FROM twilio_certification_command c
                    WHERE c.status = 'PENDING'
                      AND c.business_id IS NOT NULL
                      AND %s
                      %s
                    ORDER BY c.requested_at, c.run_id
                    FOR UPDATE OF c SKIP LOCKED
                    LIMIT 1
                )
                UPDATE twilio_certification_command c
                SET status = 'CLAIMED',
                    callback_token = ?,
                    claimed_at = CURRENT_TIMESTAMP,
                    expires_at = CURRENT_TIMESTAMP + (? * INTERVAL '1 second')
                FROM next_command n
                WHERE c.run_id = n.run_id
                  AND c.status = 'PENDING'
                RETURNING c.run_id, c.business_id
                """.formatted(actorPredicate, businessPredicate);
        List<ClaimedCommand> rows = chatGptAuroraOnly
                ? jdbc.query(sql,
                        (rs, rowNum) -> new ClaimedCommand(rs.getString(1), token,
                                rs.getObject(2, UUID.class)),
                        businessId, token, TOKEN_TTL.toSeconds())
                : jdbc.query(sql,
                        (rs, rowNum) -> new ClaimedCommand(rs.getString(1), token,
                                rs.getObject(2, UUID.class)),
                        token, TOKEN_TTL.toSeconds());
        if (rows.isEmpty()) return Optional.empty();
        return Optional.of(rows.getFirst());
    }

    public int cancelPendingChatGptAurora(UUID businessId) {
        if (businessId == null) return 0;
        return jdbc.update("""
                UPDATE twilio_certification_command
                   SET status = 'FAILED',
                       failure_reason = 'ChatGPT integration key revoked before call',
                       completed_at = CURRENT_TIMESTAMP
                 WHERE business_id = ?
                   AND requested_by LIKE 'chatgpt-aurora:%'
                   AND status = 'PENDING'
                """, businessId);
    }

    public boolean consumeIngressToken(String token, String callSid, UUID businessId) {
        if (!validToken(token) || !validCallSid(callSid) || businessId == null) return false;
        int updated = jdbc.update("""
                UPDATE twilio_certification_command
                SET status = 'INGRESS_CONSUMED', provider_call_sid = ?, completed_at = CURRENT_TIMESTAMP
                WHERE callback_token = ? AND business_id = ? AND status = 'CLAIMED'
                  AND expires_at > CURRENT_TIMESTAMP
                  AND (provider_call_sid IS NULL OR provider_call_sid = ?)
                """, callSid, token, businessId, callSid);
        return updated == 1;
    }

    public boolean consumeIngressToken(String token, String callSid) {
        if (!validToken(token) || !validCallSid(callSid)) return false;
        int updated = jdbc.update("""
                UPDATE twilio_certification_command
                SET status = 'INGRESS_CONSUMED',
                    provider_call_sid = ?,
                    completed_at = CURRENT_TIMESTAMP
                WHERE callback_token = ?
                  AND status = 'CLAIMED'
                  AND expires_at > CURRENT_TIMESTAMP
                  AND (provider_call_sid IS NULL OR provider_call_sid = ?)
                """, callSid, token, callSid);
        return updated == 1;
    }

    public void recordProviderCall(String runId, String token, String callSid) {
        if (!validRunId(runId) || !validToken(token) || !validCallSid(callSid)) return;
        jdbc.update("""
                UPDATE twilio_certification_command
                SET provider_call_sid = COALESCE(provider_call_sid, ?)
                WHERE run_id = ?
                  AND callback_token = ?
                  AND status IN ('CLAIMED', 'INGRESS_CONSUMED')
                  AND (provider_call_sid IS NULL OR provider_call_sid = ?)
                """, callSid, runId, token, callSid);
    }

    public void markFailed(String runId, String reason) {
        if (!validRunId(runId)) return;
        String safeReason = reason == null ? "unknown" : reason.substring(0, Math.min(reason.length(), 400));
        jdbc.update("""
                UPDATE twilio_certification_command
                SET status = 'FAILED',
                    failure_reason = ?,
                    completed_at = CURRENT_TIMESTAMP
                WHERE run_id = ?
                  AND status = 'CLAIMED'
                """, safeReason, runId);
    }

    public static boolean validRunId(String value) {
        return value != null && RUN_ID.matcher(value.trim()).matches();
    }

    static boolean validToken(String value) {
        return value != null && TOKEN.matcher(value.trim()).matches();
    }

    static boolean validCallSid(String value) {
        return value != null && CALL_SID.matcher(value.trim()).matches();
    }

    private static CommandStatus commandStatus(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new CommandStatus(
                rs.getString("run_id"),
                rs.getString("status"),
                rs.getString("requested_by"),
                instant(rs.getTimestamp("requested_at")),
                instant(rs.getTimestamp("claimed_at")),
                instant(rs.getTimestamp("expires_at")),
                rs.getString("provider_call_sid"),
                instant(rs.getTimestamp("completed_at")),
                rs.getString("failure_reason"));
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    public record ClaimedCommand(String runId, String callbackToken, UUID businessId) {
        public ClaimedCommand(String runId, String callbackToken) { this(runId, callbackToken, null); }
    }

    public record CommandStatus(
            String runId,
            String status,
            String requestedBy,
            Instant requestedAt,
            Instant claimedAt,
            Instant expiresAt,
            String providerCallSid,
            Instant completedAt,
            String failureReason
    ) {}
}
