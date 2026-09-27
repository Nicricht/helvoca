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
        if (!validRunId(runId)) return false;
        String actor = requestedBy == null || requestedBy.isBlank()
                ? "unknown"
                : requestedBy.trim().substring(0, Math.min(requestedBy.trim().length(), 180));
        int inserted = jdbc.update("""
                INSERT INTO twilio_certification_command (run_id, requested_by)
                VALUES (?, ?)
                ON CONFLICT (run_id) DO NOTHING
                """, runId.trim(), actor);
        return inserted == 1;
    }

    public Optional<CommandStatus> find(String runId) {
        if (!validRunId(runId)) return Optional.empty();
        List<CommandStatus> rows = jdbc.query("""
                SELECT run_id, status, requested_by, requested_at, claimed_at,
                       expires_at, provider_call_sid, completed_at, failure_reason
                  FROM twilio_certification_command
                 WHERE run_id = ?
                """, (rs, rowNum) -> new CommandStatus(
                        rs.getString("run_id"),
                        rs.getString("status"),
                        rs.getString("requested_by"),
                        instant(rs.getTimestamp("requested_at")),
                        instant(rs.getTimestamp("claimed_at")),
                        instant(rs.getTimestamp("expires_at")),
                        rs.getString("provider_call_sid"),
                        instant(rs.getTimestamp("completed_at")),
                        rs.getString("failure_reason")),
                runId.trim());
        return rows.stream().findFirst();
    }

    public Optional<ClaimedCommand> claimNext() {
        String token = UUID.randomUUID().toString();
        List<String> rows = jdbc.query("""
                WITH next_command AS (
                    SELECT run_id
                    FROM twilio_certification_command
                    WHERE status = 'PENDING'
                    ORDER BY requested_at
                    FOR UPDATE SKIP LOCKED
                    LIMIT 1
                )
                UPDATE twilio_certification_command c
                SET status = 'CLAIMED',
                    callback_token = ?,
                    claimed_at = CURRENT_TIMESTAMP,
                    expires_at = CURRENT_TIMESTAMP + (? * INTERVAL '1 second')
                FROM next_command n
                WHERE c.run_id = n.run_id
                RETURNING c.run_id
                """,
                (rs, rowNum) -> rs.getString(1),
                token,
                TOKEN_TTL.toSeconds());
        if (rows.isEmpty()) return Optional.empty();
        return Optional.of(new ClaimedCommand(rows.getFirst(), token));
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

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    public record ClaimedCommand(String runId, String callbackToken) {}

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
