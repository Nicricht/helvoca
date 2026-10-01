package cl.helvoca.billing;

import cl.helvoca.security.TenantProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
public class UsageCommercialStatusService {
    private static final BigDecimal SIXTY = BigDecimal.valueOf(60);
    private static final BigDecimal SEVENTY = BigDecimal.valueOf(70);
    private static final BigDecimal NINETY = BigDecimal.valueOf(90);
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final BusinessSubscriptionService subscriptions;
    private final TenantProvider tenantProvider;

    public UsageCommercialStatusService(BusinessSubscriptionService subscriptions,
                                        TenantProvider tenantProvider) {
        this.subscriptions = subscriptions;
        this.tenantProvider = tenantProvider;
    }

    @Transactional(readOnly = true)
    public UsageCommercialStatus currentForTenant() {
        UUID businessId = tenantProvider.requireBusinessId();
        BusinessSubscriptionService.SubscriptionView subscription = subscriptions.view(businessId);
        CommercialEntitlementService.EntitlementUsage voice = require(subscription, "VOICE_SECONDS");
        CommercialEntitlementService.EntitlementUsage safety = require(subscription, "VOICE_SAFETY_SECONDS");

        long overageCharge = estimatedOverageCharge(
                voice.overage(), voice.overageUnitSize(), voice.overagePriceClp());

        return new UsageCommercialStatus(
                wholeMinutesFloor(voice.limit()),
                wholeMinutesCeil(voice.used()),
                wholeMinutesCeil(voice.overage()),
                usagePercent(voice.used(), voice.limit()),
                alertLevel(voice.used(), voice.limit()),
                voice.overagePriceClp(),
                overageCharge,
                wholeMinutesFloor(safety.limit()),
                wholeMinutesFloor(safety.remaining()),
                safety.hardExceeded());
    }

    public static String alertLevel(BigDecimal used, BigDecimal limit) {
        BigDecimal safeUsed = nonNegative(used);
        BigDecimal safeLimit = nonNegative(limit);
        if (safeLimit.signum() == 0) return safeUsed.signum() > 0 ? "OVERAGE" : "NORMAL";
        if (safeUsed.compareTo(safeLimit) > 0) return "OVERAGE";

        BigDecimal percent = safeUsed
                .multiply(ONE_HUNDRED)
                .divide(safeLimit, 4, RoundingMode.HALF_UP);
        if (percent.compareTo(ONE_HUNDRED) >= 0) return "LIMIT";
        if (percent.compareTo(NINETY) >= 0) return "WARNING";
        if (percent.compareTo(SEVENTY) >= 0) return "NOTICE";
        return "NORMAL";
    }

    static BigDecimal usagePercent(BigDecimal used, BigDecimal limit) {
        BigDecimal safeUsed = nonNegative(used);
        BigDecimal safeLimit = nonNegative(limit);
        if (safeLimit.signum() == 0) {
            return safeUsed.signum() == 0 ? BigDecimal.ZERO.setScale(1) : ONE_HUNDRED.setScale(1);
        }
        return safeUsed.multiply(ONE_HUNDRED)
                .divide(safeLimit, 1, RoundingMode.HALF_UP);
    }

    private static CommercialEntitlementService.EntitlementUsage require(
            BusinessSubscriptionService.SubscriptionView subscription,
            String key) {
        return subscription.entitlements().stream()
                .filter(value -> value.key().equalsIgnoreCase(key))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Required entitlement is missing: " + key));
    }

    private static long estimatedOverageCharge(BigDecimal overage,
                                                BigDecimal unitSize,
                                                Integer priceClp) {
        if (overage == null || overage.signum() <= 0 || unitSize == null
                || unitSize.signum() <= 0 || priceClp == null || priceClp < 0) {
            return 0;
        }
        long units = overage.divide(unitSize, 0, RoundingMode.CEILING).longValueExact();
        return Math.multiplyExact(units, priceClp.longValue());
    }

    private static int wholeMinutesFloor(BigDecimal seconds) {
        return nonNegative(seconds).divide(SIXTY, 0, RoundingMode.FLOOR).intValueExact();
    }

    private static long wholeMinutesCeil(BigDecimal seconds) {
        return nonNegative(seconds).divide(SIXTY, 0, RoundingMode.CEILING).longValueExact();
    }

    private static BigDecimal nonNegative(BigDecimal value) {
        return value == null || value.signum() < 0 ? BigDecimal.ZERO : value;
    }

    public record UsageCommercialStatus(
            int includedMinutes,
            long usedMinutes,
            long overageMinutes,
            BigDecimal usagePercent,
            String alertLevel,
            Integer overagePricePerMinuteClp,
            long estimatedOverageChargeClp,
            int safetyLimitMinutes,
            int safetyRemainingMinutes,
            boolean safetyExceeded) {
    }
}
