package cl.helvoca.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class DistributedRateLimiter {
    private final JdbcTemplate jdbc;
    private final AtomicLong cleanupCounter = new AtomicLong();

    public DistributedRateLimiter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Result consume(String bucketKey, int limit, int windowSeconds, Instant now) {
        long epoch = now.getEpochSecond();
        long windowStart = (epoch / windowSeconds) * windowSeconds;
        Instant expiresAt = Instant.ofEpochSecond(windowStart + windowSeconds * 2L);

        Integer count = jdbc.queryForObject("""
                INSERT INTO api_rate_limit_bucket(bucket_key, window_start, request_count, expires_at)
                VALUES (?, ?, 1, ?)
                ON CONFLICT (bucket_key, window_start)
                DO UPDATE SET request_count = api_rate_limit_bucket.request_count + 1,
                              expires_at = GREATEST(api_rate_limit_bucket.expires_at, EXCLUDED.expires_at)
                RETURNING request_count
                """, Integer.class, bucketKey, windowStart, Timestamp.from(expiresAt));

        if (cleanupCounter.incrementAndGet() % 500 == 0) {
            jdbc.update("DELETE FROM api_rate_limit_bucket WHERE expires_at < ?", Timestamp.from(now));
        }

        int used = count == null ? 1 : count;
        long resetEpochSeconds = windowStart + windowSeconds;
        return new Result(used <= limit, used, Math.max(0, limit - used), resetEpochSeconds);
    }

    public record Result(boolean allowed, int used, int remaining, long resetEpochSeconds) {}
}
