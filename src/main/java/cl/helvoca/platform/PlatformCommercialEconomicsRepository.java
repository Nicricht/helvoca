package cl.helvoca.platform;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public class PlatformCommercialEconomicsRepository {
    private final JdbcTemplate jdbc;

    public PlatformCommercialEconomicsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<BusinessEconomicsSource> currentBusinesses() {
        return jdbc.query("""
                SELECT b.id AS business_id,
                       b.name AS business_name,
                       bs.plan_code,
                       cp.display_name AS plan_name,
                       bs.status,
                       cp.monthly_price_clp,
                       voice.limit_value AS included_seconds,
                       COALESCE((
                           SELECT SUM(u.quantity)
                             FROM usage_meter_event u
                            WHERE u.business_id = bs.business_id
                              AND u.meter_key = 'VOICE_SECONDS'
                              AND u.occurred_at >= bs.current_period_start
                              AND u.occurred_at < bs.current_period_end
                       ), 0) AS used_seconds,
                       voice.overage_unit_size,
                       voice.overage_price_clp,
                       COALESCE((
                           SELECT SUM(COALESCE(u.actual_cost_usd, u.estimated_cost_usd, 0))
                             FROM usage_meter_event u
                            WHERE u.business_id = bs.business_id
                              AND u.occurred_at >= bs.current_period_start
                              AND u.occurred_at < bs.current_period_end
                       ), 0) AS estimated_cost_usd
                  FROM business_subscription bs
                  JOIN business b ON b.id = bs.business_id
                  JOIN commercial_plan cp ON cp.code = bs.plan_code
                  JOIN commercial_plan_entitlement voice
                    ON voice.plan_code = bs.plan_code
                   AND voice.entitlement_key = 'VOICE_SECONDS'
                 ORDER BY b.name, b.id
                """, (rs, rowNum) -> new BusinessEconomicsSource(
                rs.getObject("business_id", UUID.class),
                rs.getString("business_name"),
                rs.getString("plan_code"),
                rs.getString("plan_name"),
                rs.getString("status"),
                nullableInteger(rs.getObject("monthly_price_clp")),
                decimal(rs.getObject("included_seconds")),
                decimal(rs.getObject("used_seconds")),
                nullableDecimal(rs.getObject("overage_unit_size")),
                nullableInteger(rs.getObject("overage_price_clp")),
                decimal(rs.getObject("estimated_cost_usd"))));
    }

    @Transactional(readOnly = true)
    public List<ProviderCostSource> providerBreakdown() {
        return jdbc.query("""
                SELECT LOWER(COALESCE(cs.ai_provider, 'unknown')) AS ai_provider,
                       COALESCE(NULLIF(cs.ai_model, ''), 'unknown') AS ai_model,
                       COUNT(*) AS call_count,
                       COALESCE(SUM(cs.duration_seconds), 0) AS duration_seconds,
                       COALESCE(SUM(cs.estimated_telephony_cost_usd), 0) AS telephony_cost_usd,
                       COALESCE(SUM(cs.estimated_ai_cost_usd), 0) AS ai_cost_usd,
                       COALESCE(SUM(cs.estimated_total_cost_usd), 0) AS total_cost_usd
                  FROM call_session cs
                  JOIN business_subscription bs ON bs.business_id = cs.business_id
                 WHERE cs.ended_at IS NOT NULL
                   AND cs.ended_at >= bs.current_period_start
                   AND cs.ended_at < bs.current_period_end
                   AND cs.certification = FALSE
                   AND cs.ai_provider IS NOT NULL
                 GROUP BY LOWER(COALESCE(cs.ai_provider, 'unknown')),
                          COALESCE(NULLIF(cs.ai_model, ''), 'unknown')
                 ORDER BY total_cost_usd DESC, ai_provider, ai_model
                """, (rs, rowNum) -> new ProviderCostSource(
                rs.getString("ai_provider"),
                rs.getString("ai_model"),
                rs.getLong("call_count"),
                decimal(rs.getObject("duration_seconds")),
                decimal(rs.getObject("telephony_cost_usd")),
                decimal(rs.getObject("ai_cost_usd")),
                decimal(rs.getObject("total_cost_usd"))));
    }

    private static BigDecimal decimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof Number number) return new BigDecimal(number.toString());
        return new BigDecimal(String.valueOf(value));
    }

    private static BigDecimal nullableDecimal(Object value) {
        return value == null ? null : decimal(value);
    }

    private static Integer nullableInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        return Integer.valueOf(String.valueOf(value));
    }

    public record BusinessEconomicsSource(
            UUID businessId,
            String businessName,
            String planCode,
            String planName,
            String status,
            Integer monthlyPriceClp,
            BigDecimal includedSeconds,
            BigDecimal usedSeconds,
            BigDecimal overageUnitSize,
            Integer overagePriceClp,
            BigDecimal estimatedCostUsd) {
    }

    public record ProviderCostSource(
            String aiProvider,
            String aiModel,
            long callCount,
            BigDecimal durationSeconds,
            BigDecimal estimatedTelephonyCostUsd,
            BigDecimal estimatedAiCostUsd,
            BigDecimal estimatedTotalCostUsd) {
    }
}
