package cl.helvoca.billing;

import cl.helvoca.security.TenantProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class UsageMeterService {
    private static final Pattern TOKEN = Pattern.compile("[A-Z0-9][A-Z0-9_.:-]*");

    private final NamedParameterJdbcTemplate jdbc;
    private final TenantProvider tenantProvider;

    public UsageMeterService(NamedParameterJdbcTemplate jdbc, TenantProvider tenantProvider) {
        this.jdbc = jdbc;
        this.tenantProvider = tenantProvider;
    }

    /**
     * Records usage for the authenticated tenant. Idempotency is enforced again
     * by PostgreSQL so retries and concurrent workers cannot double count.
     */
    @Transactional
    public boolean record(UsageRecord record) {
        Objects.requireNonNull(record, "record");
        UsageRecord normalized = normalize(record);
        UUID businessId = tenantProvider.requireBusinessId();

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("businessId", businessId)
                .addValue("meterKey", normalized.meterKey())
                .addValue("quantity", normalized.quantity())
                .addValue("unit", normalized.unit())
                .addValue("estimatedCostUsd", normalized.estimatedCostUsd())
                .addValue("actualCostUsd", normalized.actualCostUsd())
                .addValue("sourceType", normalized.sourceType())
                .addValue("sourceId", normalized.sourceId())
                .addValue("provider", normalized.provider())
                .addValue("idempotencyKey", normalized.idempotencyKey())
                .addValue("occurredAt", Timestamp.from(normalized.occurredAt()));

        int inserted = jdbc.update("""
                INSERT INTO usage_meter_event (
                    business_id, meter_key, quantity, unit,
                    estimated_cost_usd, actual_cost_usd,
                    source_type, source_id, provider, idempotency_key, occurred_at
                ) VALUES (
                    :businessId, :meterKey, :quantity, :unit,
                    :estimatedCostUsd, :actualCostUsd,
                    :sourceType, :sourceId, :provider, :idempotencyKey, :occurredAt
                )
                ON CONFLICT (business_id, idempotency_key) DO NOTHING
                """, params);
        return inserted == 1;
    }

    @Transactional(readOnly = true)
    public List<UsageSummary> summarize(Instant fromInclusive, Instant toExclusive) {
        if (fromInclusive == null || toExclusive == null || !fromInclusive.isBefore(toExclusive)) {
            throw new IllegalArgumentException("Usage interval must satisfy from < to");
        }
        UUID businessId = tenantProvider.requireBusinessId();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("businessId", businessId)
                .addValue("from", Timestamp.from(fromInclusive))
                .addValue("to", Timestamp.from(toExclusive));

        return jdbc.query("""
                SELECT meter_key,
                       unit,
                       SUM(quantity) AS quantity,
                       SUM(estimated_cost_usd) AS estimated_cost_usd,
                       SUM(actual_cost_usd) AS actual_cost_usd,
                       COUNT(*) AS event_count
                  FROM usage_meter_event
                 WHERE business_id = :businessId
                   AND occurred_at >= :from
                   AND occurred_at < :to
                 GROUP BY meter_key, unit
                 ORDER BY meter_key, unit
                """, params, (rs, rowNum) -> new UsageSummary(
                rs.getString("meter_key"),
                rs.getString("unit"),
                rs.getBigDecimal("quantity"),
                rs.getBigDecimal("estimated_cost_usd"),
                rs.getBigDecimal("actual_cost_usd"),
                rs.getLong("event_count")));
    }

    private static UsageRecord normalize(UsageRecord value) {
        String meterKey = token(value.meterKey(), "meterKey", 80);
        String unit = token(value.unit(), "unit", 30);
        String sourceType = token(value.sourceType(), "sourceType", 50);
        String sourceId = required(value.sourceId(), "sourceId", 180);
        String idempotencyKey = required(value.idempotencyKey(), "idempotencyKey", 240);
        String provider = optional(value.provider(), "provider", 60);
        BigDecimal quantity = nonNegative(value.quantity(), "quantity");
        BigDecimal estimated = nullableNonNegative(value.estimatedCostUsd(), "estimatedCostUsd");
        BigDecimal actual = nullableNonNegative(value.actualCostUsd(), "actualCostUsd");
        Instant occurredAt = Objects.requireNonNull(value.occurredAt(), "occurredAt");
        return new UsageRecord(meterKey, quantity, unit, estimated, actual,
                sourceType, sourceId, provider, idempotencyKey, occurredAt);
    }

    private static String token(String value, String field, int maxLength) {
        String normalized = required(value, field, maxLength).toUpperCase(Locale.ROOT);
        if (!TOKEN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(field + " contains unsupported characters");
        }
        return normalized;
    }

    private static String required(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        String normalized = value.trim();
        if (normalized.length() > maxLength) throw new IllegalArgumentException(field + " is too long");
        return normalized;
    }

    private static String optional(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > maxLength) throw new IllegalArgumentException(field + " is too long");
        return normalized;
    }

    private static BigDecimal nonNegative(BigDecimal value, String field) {
        if (value == null) throw new IllegalArgumentException(field + " is required");
        if (value.signum() < 0) throw new IllegalArgumentException(field + " must be non-negative");
        return value;
    }

    private static BigDecimal nullableNonNegative(BigDecimal value, String field) {
        if (value != null && value.signum() < 0) {
            throw new IllegalArgumentException(field + " must be non-negative");
        }
        return value;
    }

    public record UsageRecord(
            String meterKey,
            BigDecimal quantity,
            String unit,
            BigDecimal estimatedCostUsd,
            BigDecimal actualCostUsd,
            String sourceType,
            String sourceId,
            String provider,
            String idempotencyKey,
            Instant occurredAt) { }

    public record UsageSummary(
            String meterKey,
            String unit,
            BigDecimal quantity,
            BigDecimal estimatedCostUsd,
            BigDecimal actualCostUsd,
            long eventCount) { }
}
