package cl.helvoca.jobs;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class PersistentJobStore {
    private static final RowMapper<PersistentJob> MAPPER = PersistentJobStore::map;

    private final JdbcTemplate jdbc;

    public PersistentJobStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public PersistentJob enqueue(UUID businessId,
                                 UUID operationId,
                                 PersistentJob.Type type,
                                 String idempotencyKey,
                                 String payloadJson,
                                 int maxAttempts,
                                 Instant nextAttemptAt) {
        if (businessId == null || type == null) throw new IllegalArgumentException("businessId and type are required");
        if (idempotencyKey == null || idempotencyKey.isBlank()) throw new IllegalArgumentException("idempotencyKey is required");
        if (idempotencyKey.length() > 220) throw new IllegalArgumentException("idempotencyKey is too long");
        if (maxAttempts < 1 || maxAttempts > 100) throw new IllegalArgumentException("maxAttempts must be between 1 and 100");

        String safePayload = payloadJson == null || payloadJson.isBlank() ? "{}" : payloadJson;
        Instant safeNextAttempt = nextAttemptAt == null ? Instant.now() : nextAttemptAt;
        UUID id = UUID.randomUUID();
        int inserted = jdbc.update("""
                INSERT INTO persistent_job (
                    id, business_id, operation_id, job_type, status, idempotency_key,
                    payload, attempt_count, max_attempts, next_attempt_at,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'PENDING', ?, CAST(? AS jsonb), 0, ?, ?, NOW(), NOW())
                ON CONFLICT (business_id, idempotency_key) DO NOTHING
                """,
                id, businessId, operationId, type.name(), idempotencyKey.trim(), safePayload,
                maxAttempts, Timestamp.from(safeNextAttempt));

        PersistentJob job = inserted == 1
                ? findById(businessId, id).orElseThrow()
                : findByIdempotencyKey(businessId, idempotencyKey.trim()).orElseThrow();

        if (job.jobType() != type || !Objects.equals(job.operationId(), operationId)
                || !payloadMatches(businessId, job.id(), safePayload)) {
            throw new IllegalStateException("Idempotency key is already bound to a different durable job");
        }
        return job;
    }

    @Transactional
    public Optional<PersistentJob> claimNext(String workerId, Duration leaseDuration) {
        if (workerId == null || workerId.isBlank()) throw new IllegalArgumentException("workerId is required");
        long leaseSeconds = Math.max(5, leaseDuration == null ? 120 : leaseDuration.toSeconds());

        // A worker may die after consuming its final allowed attempt. Reap those
        // expired leases before selecting the next claim so RUNNING cannot become
        // a permanent tombstone.
        jdbc.update("""
                UPDATE persistent_job
                   SET status = 'DEAD_LETTER',
                       lease_owner = NULL,
                       lease_expires_at = NULL,
                       last_error_code = COALESCE(last_error_code, 'LEASE_EXHAUSTED'),
                       last_error_message = COALESCE(last_error_message, 'Worker lease expired after the final allowed attempt.'),
                       completed_at = COALESCE(completed_at, NOW()),
                       updated_at = NOW()
                 WHERE status = 'RUNNING'
                   AND lease_expires_at <= NOW()
                   AND attempt_count >= max_attempts
                """);

        List<PersistentJob> claimed = jdbc.query("""
                WITH candidate AS (
                    SELECT id
                      FROM persistent_job
                     WHERE attempt_count < max_attempts
                       AND (
                           (status IN ('PENDING','FAILED') AND next_attempt_at <= NOW())
                           OR (status = 'RUNNING' AND lease_expires_at <= NOW())
                       )
                     ORDER BY
                       CASE WHEN status = 'RUNNING' THEN 0 ELSE 1 END,
                       next_attempt_at ASC,
                       created_at ASC
                     FOR UPDATE SKIP LOCKED
                     LIMIT 1
                )
                UPDATE persistent_job job
                   SET status = 'RUNNING',
                       lease_owner = ?,
                       lease_expires_at = NOW() + make_interval(secs => ?),
                       attempt_count = job.attempt_count + 1,
                       updated_at = NOW()
                  FROM candidate
                 WHERE job.id = candidate.id
                RETURNING job.*
                """, MAPPER, workerId.trim(), (int) Math.min(leaseSeconds, Integer.MAX_VALUE));
        return claimed.stream().findFirst();
    }

    @Transactional
    public void markSucceeded(UUID jobId, String workerId) {
        int updated = jdbc.update("""
                UPDATE persistent_job
                   SET status = 'SUCCEEDED',
                       lease_owner = NULL,
                       lease_expires_at = NULL,
                       last_error_code = NULL,
                       last_error_message = NULL,
                       completed_at = NOW(),
                       updated_at = NOW()
                 WHERE id = ? AND status = 'RUNNING' AND lease_owner = ?
                """, jobId, workerId);
        requireLeaseUpdate(updated);
    }

    @Transactional
    public void markFailed(PersistentJob job,
                           String workerId,
                           String errorCode,
                           String errorMessage,
                           boolean retryable,
                           Duration retryDelay) {
        boolean exhausted = job.attemptCount() >= job.maxAttempts();
        boolean dead = !retryable || exhausted;
        Instant retryAt = Instant.now().plus(retryDelay == null ? Duration.ofSeconds(5) : retryDelay);
        int updated = jdbc.update("""
                UPDATE persistent_job
                   SET status = ?,
                       lease_owner = NULL,
                       lease_expires_at = NULL,
                       last_error_code = ?,
                       last_error_message = ?,
                       next_attempt_at = ?,
                       completed_at = ?,
                       updated_at = NOW()
                 WHERE id = ? AND status = 'RUNNING' AND lease_owner = ?
                """,
                dead ? PersistentJob.Status.DEAD_LETTER.name() : PersistentJob.Status.FAILED.name(),
                safeCode(errorCode), safeMessage(errorMessage), Timestamp.from(retryAt),
                dead ? Timestamp.from(Instant.now()) : null,
                job.id(), workerId);
        requireLeaseUpdate(updated);
    }

    @Transactional
    public boolean cancel(UUID businessId, UUID jobId) {
        return jdbc.update("""
                UPDATE persistent_job
                   SET status = 'CANCELLED',
                       completed_at = NOW(),
                       updated_at = NOW()
                 WHERE id = ? AND business_id = ? AND status IN ('PENDING','FAILED')
                """, jobId, businessId) == 1;
    }

    @Transactional(readOnly = true)
    public Optional<PersistentJob> findById(UUID businessId, UUID id) {
        return jdbc.query("SELECT * FROM persistent_job WHERE id = ? AND business_id = ?", MAPPER, id, businessId)
                .stream().findFirst();
    }

    @Transactional(readOnly = true)
    public Optional<PersistentJob> findByIdempotencyKey(UUID businessId, String idempotencyKey) {
        return jdbc.query("SELECT * FROM persistent_job WHERE business_id = ? AND idempotency_key = ?",
                        MAPPER, businessId, idempotencyKey)
                .stream().findFirst();
    }

    @Transactional(readOnly = true)
    public List<PersistentJob> recent(UUID businessId) {
        return jdbc.query("""
                SELECT * FROM persistent_job
                 WHERE business_id = ?
                 ORDER BY created_at DESC
                 LIMIT 100
                """, MAPPER, businessId);
    }

    private boolean payloadMatches(UUID businessId, UUID jobId, String payloadJson) {
        Boolean matches = jdbc.queryForObject("""
                SELECT payload = CAST(? AS jsonb)
                  FROM persistent_job
                 WHERE id = ? AND business_id = ?
                """, Boolean.class, payloadJson, jobId, businessId);
        return Boolean.TRUE.equals(matches);
    }

    private static PersistentJob map(ResultSet rs, int rowNum) throws SQLException {
        return new PersistentJob(
                rs.getObject("id", UUID.class),
                rs.getObject("business_id", UUID.class),
                rs.getObject("operation_id", UUID.class),
                PersistentJob.Type.valueOf(rs.getString("job_type")),
                PersistentJob.Status.valueOf(rs.getString("status")),
                rs.getString("idempotency_key"),
                rs.getString("payload"),
                rs.getInt("attempt_count"),
                rs.getInt("max_attempts"),
                instant(rs, "next_attempt_at"),
                rs.getString("lease_owner"),
                instant(rs, "lease_expires_at"),
                rs.getString("last_error_code"),
                rs.getString("last_error_message"),
                instant(rs, "completed_at"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static void requireLeaseUpdate(int updated) {
        if (updated != 1) throw new IllegalStateException("Persistent job lease was lost before state transition");
    }

    private static String safeCode(String value) {
        String safe = value == null || value.isBlank() ? "JOB_EXECUTION_FAILED" : value.trim();
        return safe.length() <= 100 ? safe : safe.substring(0, 100);
    }

    private static String safeMessage(String value) {
        String safe = value == null || value.isBlank() ? "Durable job execution failed." : value.trim();
        return safe.length() <= 500 ? safe : safe.substring(0, 500);
    }
}
