package cl.helvoca.billing;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class CommercialPlanCatalogService {
    private final NamedParameterJdbcTemplate jdbc;

    public CommercialPlanCatalogService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<Plan> activePlans() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT code, public_code, display_name, monthly_price_clp, currency,
                       custom_pricing, recommended, active, sort_order
                  FROM commercial_plan
                 WHERE active = TRUE
                 ORDER BY sort_order, code
                """, new MapSqlParameterSource());
        return rows.stream().map(this::mapPlan).toList();
    }

    @Transactional(readOnly = true)
    public Plan findActiveByPublicCode(String publicCode) {
        String normalized = normalizeCode(publicCode, "public plan code");
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT code, public_code, display_name, monthly_price_clp, currency,
                       custom_pricing, recommended, active, sort_order
                  FROM commercial_plan
                 WHERE UPPER(public_code) = :publicCode
                   AND active = TRUE
                """, new MapSqlParameterSource("publicCode", normalized));
        if (rows.size() != 1) throw new IllegalArgumentException("Unknown or inactive commercial plan");
        return mapPlan(rows.getFirst());
    }

    @Transactional(readOnly = true)
    public Plan requireByCode(String code) {
        String normalized = normalizeCode(code, "plan code");
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT code, public_code, display_name, monthly_price_clp, currency,
                       custom_pricing, recommended, active, sort_order
                  FROM commercial_plan
                 WHERE UPPER(code) = :code
                   AND active = TRUE
                """, new MapSqlParameterSource("code", normalized));
        if (rows.size() != 1) throw new IllegalStateException("Subscription references an unknown or inactive commercial plan");
        return mapPlan(rows.getFirst());
    }

    private Plan mapPlan(Map<String, Object> row) {
        String code = String.valueOf(row.get("code"));
        return new Plan(
                code,
                String.valueOf(row.get("public_code")),
                String.valueOf(row.get("display_name")),
                integer(row.get("monthly_price_clp")),
                String.valueOf(row.get("currency")),
                bool(row.get("custom_pricing")),
                bool(row.get("recommended")),
                bool(row.get("active")),
                integer(row.get("sort_order")) == null ? 0 : integer(row.get("sort_order")),
                entitlements(code));
    }

    private List<EntitlementRule> entitlements(String planCode) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT entitlement_key, kind, meter_key, limit_value, unit,
                       hard_limit, overage_unit_size, overage_price_clp
                  FROM commercial_plan_entitlement
                 WHERE plan_code = :planCode
                 ORDER BY entitlement_key
                """, new MapSqlParameterSource("planCode", planCode));
        return rows.stream().map(row -> new EntitlementRule(
                String.valueOf(row.get("entitlement_key")),
                String.valueOf(row.get("kind")),
                nullableString(row.get("meter_key")),
                decimal(row.get("limit_value")),
                String.valueOf(row.get("unit")),
                bool(row.get("hard_limit")),
                nullableDecimal(row.get("overage_unit_size")),
                integer(row.get("overage_price_clp")))).toList();
    }

    private static String normalizeCode(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean bool(Object value) {
        if (value instanceof Boolean bool) return bool;
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static Integer integer(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        return Integer.valueOf(String.valueOf(value));
    }

    private static BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof Number number) return new BigDecimal(number.toString());
        return new BigDecimal(String.valueOf(value));
    }

    private static BigDecimal nullableDecimal(Object value) {
        return value == null ? null : decimal(value);
    }

    private static String nullableString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public record Plan(
            String code,
            String publicCode,
            String displayName,
            Integer monthlyPriceClp,
            String currency,
            boolean customPricing,
            boolean recommended,
            boolean active,
            int sortOrder,
            List<EntitlementRule> entitlements) {
        public EntitlementRule entitlement(String key) {
            if (key == null || key.isBlank()) return null;
            String normalized = key.trim().toUpperCase(Locale.ROOT);
            return entitlements.stream().filter(rule -> rule.key().equalsIgnoreCase(normalized)).findFirst().orElse(null);
        }
    }

    public record EntitlementRule(
            String key,
            String kind,
            String meterKey,
            BigDecimal limitValue,
            String unit,
            boolean hardLimit,
            BigDecimal overageUnitSize,
            Integer overagePriceClp) { }
}
