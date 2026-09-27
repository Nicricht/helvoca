package cl.helvoca.telephony.twilio;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
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

    static boolean validRunId(String value) {
        return value != null && RUN_ID.matcher(value.trim()).matches();
    }

    static boolean validToken(String value) {
        return value != null && TOKEN.matcher(value.trim()).matches();
    }

    static boolean validCallSid(String value) {
        return value != null && CALL_SID.matcher(value.trim()).matches();
    }

    public record ClaimedCommand(String runId, String callbackToken) {}
}
