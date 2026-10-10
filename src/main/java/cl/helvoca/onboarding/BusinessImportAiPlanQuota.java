package cl.helvoca.onboarding;

import cl.helvoca.security.TenantProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Subscription-cycle quota, separate from estimated-cost budget and the V98
 * provider token evidence. V41 usage events are durable per-attempt reservations.
 * Denials fail closed; failed/uncertain provider calls are never refunded.
 */
@Service
public class BusinessImportAiPlanQuota {
    private final JdbcTemplate jdbc;
    private final TenantProvider tenant;

    @Value("${app.onboarding.import-ai.enabled:false}")
    private boolean enabled;

    public BusinessImportAiPlanQuota(JdbcTemplate jdbc, TenantProvider tenant) {
        this.jdbc = jdbc;
        this.tenant = tenant;
    }

    @Transactional(readOnly = true)
    public Snapshot current() {
        return evaluate(tenant.requireBusinessId(), false);
    }

    /**
     * Lock the tenant subscription through the entire read-and-insert sequence.
     * Concurrent requests in any instance cannot both claim the final slot.
     * An identical attempt UUID cannot be recorded or charged twice.
     */
    @Transactional
    public boolean reserve(UUID attemptId) {
        if (attemptId == null) throw new IllegalArgumentException("Attempt ID required");
        UUID businessId = tenant.requireBusinessId();
        Snapshot snapshot = evaluate(businessId, true);
        if (!"AVAILABLE".equals(snapshot.status())) return false;

        int written = jdbc.update("""
                INSERT INTO public.usage_meter_event (
                    business_id, meter_key, quantity, unit, source_type,
                    source_id, provider, idempotency_key, occurred_at
                ) VALUES (?, 'AI_IMPORT_REQUESTS', 1, 'REQUESTS', 'BUSINESS_IMPORT_AI',
                          ?, 'OPENAI', ?, ?)
                ON CONFLICT (business_id, idempotency_key) DO NOTHING
                """, businessId, attemptId.toString(),
                "BUSINESS_IMPORT_AI:" + attemptId, Timestamp.from(Instant.now()));
        return written == 1;
    }

    private Snapshot evaluate(UUID businessId, boolean lock) {
        String sql = """
                SELECT b.status AS business_status, s.status AS subscription_status,
                       s.plan_code, s.current_period_start, s.current_period_end,
                       p.active AS plan_active,
                       e.kind, e.meter_key, e.limit_value, e.unit, e.hard_limit
                  FROM public.business_subscription s
                  JOIN public.business b ON b.id = s.business_id
                  LEFT JOIN public.commercial_plan p ON p.code = s.plan_code
                  LEFT JOIN public.commercial_plan_entitlement e
                    ON e.plan_code = s.plan_code AND e.entitlement_key = 'AI_IMPORT_REQUESTS'
                 WHERE s.business_id = ?
                """ + (lock ? " FOR UPDATE OF s" : "");
        List<PlanState> rows = jdbc.query(sql, (rs, index) -> new PlanState(
                rs.getString("business_status"), rs.getString("subscription_status"),
                rs.getString("plan_code"),
                timestamp(rs.getTimestamp("current_period_start")),
                timestamp(rs.getTimestamp("current_period_end")),
                Boolean.TRUE.equals(rs.getObject("plan_active")),
                rs.getString("kind"), rs.getString("meter_key"),
                rs.getBigDecimal("limit_value"), rs.getString("unit"),
                Boolean.TRUE.equals(rs.getObject("hard_limit"))), businessId);
        if (rows.size() != 1) {
            return new Snapshot("UNAVAILABLE", null, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, null, null, enabled);
        }

        PlanState p = rows.getFirst();
        Instant now = Instant.now();
        String status;
        if (!enabled) status = "DISABLED";
        else if (!"ACTIVE".equals(p.businessStatus())) status = "BUSINESS_INACTIVE";
        else if (!("ACTIVE".equals(p.subscriptionStatus()) || "TRIALING".equals(p.subscriptionStatus())))
            status = "SUBSCRIPTION_INACTIVE";
        else if (p.start() == null || p.end() == null
                || now.isBefore(p.start()) || !now.isBefore(p.end())) status = "PERIOD_EXPIRED";
        else if (!p.planActive() || !"USAGE".equals(p.kind())
                || !"AI_IMPORT_REQUESTS".equals(p.meterKey())
                || !"REQUESTS".equals(p.unit()) || !p.hardLimit()
                || p.limit() == null || p.limit().signum() < 0) status = "UNAVAILABLE";
        else if (p.limit().signum() == 0) status = "NOT_INCLUDED";
        else status = "AVAILABLE";

        BigDecimal used = BigDecimal.ZERO;
        if (p.start() != null && p.end() != null && p.start().isBefore(p.end())) {
            BigDecimal sum = jdbc.queryForObject("""
                    SELECT COALESCE(SUM(quantity), 0)
                      FROM public.usage_meter_event
                     WHERE business_id = ? AND meter_key = 'AI_IMPORT_REQUESTS'
                       AND occurred_at >= ? AND occurred_at < ?
                    """, BigDecimal.class, businessId, Timestamp.from(p.start()), Timestamp.from(p.end()));
            if (sum != null) used = sum;
        }

        BigDecimal allowance = p.limit() == null ? BigDecimal.ZERO : p.limit().max(BigDecimal.ZERO);
        BigDecimal remaining = allowance.subtract(used).max(BigDecimal.ZERO);
        if ("AVAILABLE".equals(status) && remaining.compareTo(BigDecimal.ONE) < 0) status = "LIMIT_REACHED";
        return new Snapshot(status, p.planCode(), allowance, used, remaining,
                p.start(), p.end(), enabled);
    }

    private static Instant timestamp(Timestamp time) {
        return time == null ? null : time.toInstant();
    }

    private record PlanState(
            String businessStatus, String subscriptionStatus, String planCode,
            Instant start, Instant end, boolean planActive, String kind,
            String meterKey, BigDecimal limit, String unit, boolean hardLimit) { }

    public record Snapshot(
            String status, String planCode, BigDecimal limit, BigDecimal used,
            BigDecimal remaining, Instant currentPeriodStart, Instant currentPeriodEnd,
            boolean masterEnabled) { }
}
