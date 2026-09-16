package cl.helvoca.billing;

import cl.helvoca.security.TenantProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class BusinessSubscriptionService {
    private static final int TRIAL_DAYS = 14;
    private static final BigDecimal SECONDS_PER_MINUTE = BigDecimal.valueOf(60);

    private final BusinessSubscriptionRepository subscriptions;
    private final CommercialEntitlementService entitlements;
    private final TenantProvider tenantProvider;
    private final CommercialPlanCatalogService catalog;

    public BusinessSubscriptionService(BusinessSubscriptionRepository subscriptions,
                                       CommercialEntitlementService entitlements,
                                       TenantProvider tenantProvider,
                                       CommercialPlanCatalogService catalog) {
        this.subscriptions = subscriptions;
        this.entitlements = entitlements;
        this.tenantProvider = tenantProvider;
        this.catalog = catalog;
    }

    @Transactional
    public BusinessSubscription startBasicTrial(UUID businessId) {
        return subscriptions.findByBusinessId(businessId).orElseGet(() -> {
            Instant now = Instant.now();
            BusinessSubscription subscription = new BusinessSubscription();
            subscription.setBusinessId(businessId);
            subscription.setPlanCode(PlanCode.BASIC);
            subscription.setStatus(SubscriptionStatus.TRIALING);
            subscription.setCurrentPeriodStart(now);
            subscription.setCurrentPeriodEnd(now.plus(TRIAL_DAYS, ChronoUnit.DAYS));
            return subscriptions.saveAndFlush(subscription);
        });
    }

    @Transactional(readOnly = true)
    public SubscriptionView currentForTenant() {
        return view(tenantProvider.requireBusinessId());
    }

    @Transactional(readOnly = true)
    public SubscriptionView view(UUID businessId) {
        CommercialEntitlementService.SubscriptionEntitlements snapshot = entitlements.snapshot(businessId);
        CommercialEntitlementService.EntitlementUsage voice = snapshot.requireEntitlement("VOICE_SECONDS");
        CommercialEntitlementService.EntitlementUsage capacity = snapshot.requireEntitlement("CONCURRENT_CALLS");

        if (!"USAGE".equalsIgnoreCase(voice.kind()) || !"SECONDS".equalsIgnoreCase(voice.unit())) {
            throw new IllegalStateException("VOICE_SECONDS entitlement has an incompatible shape");
        }
        if (!"CAPACITY".equalsIgnoreCase(capacity.kind())) {
            throw new IllegalStateException("CONCURRENT_CALLS entitlement has an incompatible shape");
        }

        int maxConcurrentCalls;
        try {
            maxConcurrentCalls = capacity.limit().intValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalStateException("CONCURRENT_CALLS capacity must be an integer", e);
        }

        return new SubscriptionView(
                snapshot.businessId(),
                snapshot.planCode(),
                snapshot.status(),
                snapshot.serviceAllowed(),
                maxConcurrentCalls,
                wholeMinutesFloor(voice.limit()),
                wholeMinutesCeil(voice.used()),
                wholeMinutesCeil(voice.overage()),
                snapshot.currentPeriodStart(),
                snapshot.currentPeriodEnd(),
                snapshot.graceUntil(),
                snapshot.billingProviderConnected(),
                false);
    }

    @Transactional
    public BusinessSubscription synchronize(UUID businessId,
                                            PlanCode planCode,
                                            SubscriptionStatus status,
                                            Instant periodStart,
                                            Instant periodEnd,
                                            Instant graceUntil,
                                            String externalCustomerId,
                                            String externalSubscriptionId) {
        if (businessId == null || planCode == null || status == null) {
            throw new IllegalArgumentException("Business, plan and status are required");
        }
        if (periodStart == null || periodEnd == null || !periodEnd.isAfter(periodStart)) {
            throw new IllegalArgumentException("A valid subscription period is required");
        }
        BusinessSubscription subscription = subscriptions.findByBusinessId(businessId)
                .orElseGet(BusinessSubscription::new);
        subscription.setBusinessId(businessId);
        subscription.setPlanCode(planCode);
        subscription.setStatus(status);
        subscription.setCurrentPeriodStart(periodStart);
        subscription.setCurrentPeriodEnd(periodEnd);
        subscription.setGraceUntil(graceUntil);
        subscription.setExternalCustomerId(blankToNull(externalCustomerId));
        subscription.setExternalSubscriptionId(blankToNull(externalSubscriptionId));
        return subscriptions.saveAndFlush(subscription);
    }

    @Transactional(readOnly = true)
    public List<PlanView> plans() {
        return catalog.activePlans().stream()
                .map(plan -> {
                    CommercialPlanCatalogService.EntitlementRule voice = plan.entitlement("VOICE_SECONDS");
                    CommercialPlanCatalogService.EntitlementRule capacity = plan.entitlement("CONCURRENT_CALLS");
                    if (voice == null || capacity == null) {
                        throw new IllegalStateException("Commercial plan is missing required voice entitlements: " + plan.code());
                    }
                    int maxConcurrent;
                    try {
                        maxConcurrent = capacity.limitValue().intValueExact();
                    } catch (ArithmeticException e) {
                        throw new IllegalStateException("Commercial plan capacity is not an integer: " + plan.code(), e);
                    }
                    return new PlanView(plan.code(), maxConcurrent, wholeMinutesFloor(voice.limitValue()));
                })
                .toList();
    }

    private static int wholeMinutesFloor(BigDecimal seconds) {
        if (seconds == null || seconds.signum() < 0) throw new IllegalStateException("Usage quantity must be non-negative");
        return seconds.divide(SECONDS_PER_MINUTE, 0, RoundingMode.FLOOR).intValueExact();
    }

    private static long wholeMinutesCeil(BigDecimal seconds) {
        if (seconds == null || seconds.signum() < 0) throw new IllegalStateException("Usage quantity must be non-negative");
        return seconds.divide(SECONDS_PER_MINUTE, 0, RoundingMode.CEILING).longValueExact();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record SubscriptionView(
            UUID businessId,
            String plan,
            String status,
            boolean serviceAllowed,
            int maxConcurrentCalls,
            int includedMinutes,
            long usedMinutes,
            long overageMinutes,
            Instant currentPeriodStart,
            Instant currentPeriodEnd,
            Instant graceUntil,
            boolean billingProviderConnected,
            boolean legacyFallback) {}

    public record PlanView(String code, int maxConcurrentCalls, int includedMinutes) {}
}
