package cl.helvoca.billing;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class CommercialEntitlementService {
    private static final String USAGE = "USAGE";
    private static final String CAPACITY = "CAPACITY";

    private final BusinessSubscriptionRepository subscriptions;
    private final CommercialPlanCatalogService catalog;
    private final NamedParameterJdbcTemplate jdbc;

    public CommercialEntitlementService(BusinessSubscriptionRepository subscriptions,
                                        CommercialPlanCatalogService catalog,
                                        NamedParameterJdbcTemplate jdbc) {
        this.subscriptions = subscriptions;
        this.catalog = catalog;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public SubscriptionEntitlements snapshot(UUID businessId) {
        if (businessId == null) throw new IllegalArgumentException("businessId is required");
        BusinessSubscription subscription = subscriptions.findByBusinessId(businessId)
                .orElseThrow(() -> new IllegalStateException("Business subscription is not initialized"));
        String planCode = String.valueOf(subscription.getPlanCode());
        CommercialPlanCatalogService.Plan plan = catalog.requireByCode(planCode);

        Instant now = Instant.now();
        boolean serviceAllowed = subscription.getStatus().allowsService(now, subscription.getGraceUntil())
                && (subscription.getStatus() != SubscriptionStatus.TRIALING
                    || now.isBefore(subscription.getCurrentPeriodEnd()));

        List<EntitlementUsage> entitlements = plan.entitlements().stream()
                .map(rule -> evaluate(businessId, subscription, rule))
                .toList();
        boolean hardUsageExceeded = entitlements.stream()
                .anyMatch(value -> USAGE.equalsIgnoreCase(value.kind()) && value.hardExceeded());
        if (hardUsageExceeded) serviceAllowed = false;

        return new SubscriptionEntitlements(
                businessId,
                plan.code(),
                plan.publicCode(),
                plan.displayName(),
                subscription.getStatus().name(),
                serviceAllowed,
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd(),
                subscription.getGraceUntil(),
                notBlank(subscription.getExternalSubscriptionId()),
                entitlements);
    }

    @Transactional(readOnly = true)
    public int capacity(UUID businessId, String entitlementKey) {
        EntitlementUsage entitlement = snapshot(businessId).requireEntitlement(entitlementKey);
        if (!CAPACITY.equalsIgnoreCase(entitlement.kind())) {
            throw new IllegalStateException(entitlement.key() + " is not a capacity entitlement");
        }
        try {
            return entitlement.limit().intValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalStateException(entitlement.key() + " capacity is not an integer", e);
        }
    }

    private EntitlementUsage evaluate(UUID businessId,
                                      BusinessSubscription subscription,
                                      CommercialPlanCatalogService.EntitlementRule rule) {
        BigDecimal used = BigDecimal.ZERO;
        BigDecimal remaining = rule.limitValue();
        BigDecimal overage = BigDecimal.ZERO;
        boolean hardExceeded = false;

        if (USAGE.equalsIgnoreCase(rule.kind())) {
            if (!notBlank(rule.meterKey())) {
                throw new IllegalStateException(rule.key() + " usage entitlement has no meter key");
            }
            used = usage(businessId, rule.meterKey(),
                    subscription.getCurrentPeriodStart(), subscription.getCurrentPeriodEnd());
            BigDecimal delta = rule.limitValue().subtract(used);
            remaining = delta.max(BigDecimal.ZERO);
            overage = used.subtract(rule.limitValue()).max(BigDecimal.ZERO);
            hardExceeded = rule.hardLimit() && overage.signum() > 0;
        } else if (!CAPACITY.equalsIgnoreCase(rule.kind())) {
            throw new IllegalStateException("Unsupported entitlement kind: " + rule.kind());
        }

        return new EntitlementUsage(
                rule.key(),
                rule.kind(),
                rule.meterKey(),
                rule.limitValue(),
                rule.unit(),
                rule.hardLimit(),
                used,
                remaining,
                overage,
                hardExceeded,
                rule.overageUnitSize(),
                rule.overagePriceClp());
    }

    private BigDecimal usage(UUID businessId, String meterKey, Instant from, Instant to) {
        if (from == null || to == null || !from.isBefore(to)) {
            throw new IllegalStateException("Subscription has an invalid commercial period");
        }
        BigDecimal value = jdbc.queryForObject("""
                SELECT COALESCE(SUM(quantity), 0)
                  FROM usage_meter_event
                 WHERE business_id = :businessId
                   AND meter_key = :meterKey
                   AND occurred_at >= :from
                   AND occurred_at < :to
                """, new MapSqlParameterSource()
                .addValue("businessId", businessId)
                .addValue("meterKey", normalizeToken(meterKey))
                .addValue("from", Timestamp.from(from))
                .addValue("to", Timestamp.from(to)), BigDecimal.class);
        return value == null ? BigDecimal.ZERO : value.max(BigDecimal.ZERO);
    }

    private static String normalizeToken(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("meterKey is required");
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    public record SubscriptionEntitlements(
            UUID businessId,
            String planCode,
            String publicPlanCode,
            String planName,
            String status,
            boolean serviceAllowed,
            Instant currentPeriodStart,
            Instant currentPeriodEnd,
            Instant graceUntil,
            boolean billingProviderConnected,
            List<EntitlementUsage> entitlements) {

        public EntitlementUsage requireEntitlement(String key) {
            if (key == null || key.isBlank()) throw new IllegalArgumentException("entitlement key is required");
            String normalized = key.trim().toUpperCase(Locale.ROOT);
            return entitlements.stream()
                    .filter(value -> value.key().equalsIgnoreCase(normalized))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Required entitlement is missing: " + normalized));
        }
    }

    public record EntitlementUsage(
            String key,
            String kind,
            String meterKey,
            BigDecimal limit,
            String unit,
            boolean hardLimit,
            BigDecimal used,
            BigDecimal remaining,
            BigDecimal overage,
            boolean hardExceeded,
            BigDecimal overageUnitSize,
            Integer overagePriceClp) { }
}
