package cl.helvoca.billing;

import cl.helvoca.call.CallSessionRepository;
import cl.helvoca.security.TenantProvider;
import cl.helvoca.telephony.CallCommercialProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class BusinessSubscriptionService {
    private static final String SIMULATOR_PROVIDER = "simulator";
    private static final int TRIAL_DAYS = 14;

    private final BusinessSubscriptionRepository subscriptions;
    private final CallSessionRepository calls;
    private final TenantProvider tenantProvider;
    private final CallCommercialProperties commercial;

    public BusinessSubscriptionService(BusinessSubscriptionRepository subscriptions,
                                       CallSessionRepository calls,
                                       TenantProvider tenantProvider,
                                       CallCommercialProperties commercial) {
        this.subscriptions = subscriptions;
        this.calls = calls;
        this.tenantProvider = tenantProvider;
        this.commercial = commercial;
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
        Instant now = Instant.now();
        BusinessSubscription stored = subscriptions.findByBusinessId(businessId).orElse(null);
        if (stored == null) {
            Instant start = now.truncatedTo(ChronoUnit.DAYS).minus(30, ChronoUnit.DAYS);
            Instant end = now.plus(1, ChronoUnit.DAYS);
            return buildView(businessId, PlanCode.PRO, SubscriptionStatus.ACTIVE,
                    start, end, null, false, true, now,
                    commercial.getMaxConcurrentPerBusiness());
        }
        return buildView(businessId, stored.getPlanCode(), stored.getStatus(),
                stored.getCurrentPeriodStart(), stored.getCurrentPeriodEnd(), stored.getGraceUntil(),
                stored.getExternalSubscriptionId() != null && !stored.getExternalSubscriptionId().isBlank(),
                false, now, stored.getPlanCode().getMaxConcurrentCalls());
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

    public List<PlanView> plans() {
        return Arrays.stream(PlanCode.values())
                .map(plan -> new PlanView(plan.name(), plan.getMaxConcurrentCalls(), plan.getIncludedMinutesPerPeriod()))
                .toList();
    }

    private SubscriptionView buildView(UUID businessId,
                                       PlanCode plan,
                                       SubscriptionStatus status,
                                       Instant periodStart,
                                       Instant periodEnd,
                                       Instant graceUntil,
                                       boolean billingProviderConnected,
                                       boolean legacyFallback,
                                       Instant now,
                                       int maxConcurrentCalls) {
        Long secondsValue = calls.sumDurationSecondsByBusinessAndPeriod(
                businessId, periodStart, periodEnd, SIMULATOR_PROVIDER);
        long seconds = secondsValue == null ? 0L : Math.max(0L, secondsValue);
        long usedMinutes = (seconds + 59L) / 60L;
        long overageMinutes = Math.max(0L, usedMinutes - plan.getIncludedMinutesPerPeriod());
        boolean serviceAllowed = status.allowsService(now, graceUntil)
                && (status != SubscriptionStatus.TRIALING || now.isBefore(periodEnd));
        return new SubscriptionView(
                businessId,
                plan.name(),
                status.name(),
                serviceAllowed,
                maxConcurrentCalls,
                plan.getIncludedMinutesPerPeriod(),
                usedMinutes,
                overageMinutes,
                periodStart,
                periodEnd,
                graceUntil,
                billingProviderConnected,
                legacyFallback);
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
