package cl.helvoca.platform;

import cl.helvoca.billing.UsageCommercialStatusService;
import cl.helvoca.telephony.CallCommercialProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class PlatformCommercialEconomicsService {
    private static final BigDecimal SIXTY = BigDecimal.valueOf(60);
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final PlatformCommercialEconomicsRepository repository;
    private final CallCommercialProperties commercial;

    public PlatformCommercialEconomicsService(PlatformCommercialEconomicsRepository repository,
                                              CallCommercialProperties commercial) {
        this.repository = repository;
        this.commercial = commercial;
    }

    @Transactional(readOnly = true)
    public PortfolioEconomics portfolio() {
        List<BusinessEconomics> businesses = repository.currentBusinesses().stream()
                .map(this::business)
                .toList();
        List<ProviderEconomics> providers = repository.providerBreakdown().stream()
                .map(this::provider)
                .toList();

        long basePlanValue = businesses.stream().mapToLong(BusinessEconomics::basePlanValueClp).sum();
        long overageValue = businesses.stream().mapToLong(BusinessEconomics::estimatedOverageValueClp).sum();
        long commercialValue = Math.addExact(basePlanValue, overageValue);
        BigDecimal costUsd = businesses.stream()
                .map(BusinessEconomics::estimatedPlatformCostUsd)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal costClp = toClp(costUsd);
        BigDecimal marginClp = costClp == null
                ? null
                : BigDecimal.valueOf(commercialValue).subtract(costClp);
        BigDecimal marginPercent = marginPercent(marginClp, commercialValue);

        long revenueUnknown = businesses.stream()
                .filter(BusinessEconomics::commercialValueUnknown)
                .count();

        return new PortfolioEconomics(
                businesses.size(),
                basePlanValue,
                overageValue,
                commercialValue,
                costUsd,
                costClp,
                marginClp,
                marginPercent,
                commercial.getUsdToClpRate(),
                revenueUnknown,
                businesses,
                providers);
    }

    private BusinessEconomics business(PlatformCommercialEconomicsRepository.BusinessEconomicsSource source) {
        BigDecimal included = nonNegative(source.includedSeconds());
        BigDecimal used = nonNegative(source.usedSeconds());
        BigDecimal overage = used.subtract(included).max(BigDecimal.ZERO);

        boolean commercialValueUnknown = source.monthlyPriceClp() == null && revenueEligible(source.status());
        long baseValue = source.monthlyPriceClp() == null || !revenueEligible(source.status())
                ? 0L : source.monthlyPriceClp().longValue();
        long overageValue = overageValue(overage, source.overageUnitSize(), source.overagePriceClp());
        long commercialValue = Math.addExact(baseValue, overageValue);

        BigDecimal costUsd = nonNegative(source.estimatedCostUsd());
        BigDecimal costClp = toClp(costUsd);
        BigDecimal marginClp = costClp == null || commercialValueUnknown
                ? null : BigDecimal.valueOf(commercialValue).subtract(costClp);

        return new BusinessEconomics(
                source.businessId(),
                source.businessName(),
                source.planCode(),
                source.planName(),
                source.status(),
                wholeMinutesFloor(included),
                wholeMinutesCeil(used),
                UsageCommercialStatusService.alertLevel(used, included),
                baseValue,
                overageValue,
                commercialValueUnknown ? null : commercialValue,
                costUsd,
                costClp,
                marginClp,
                marginPercent(marginClp, commercialValue),
                commercialValueUnknown);
    }

    private ProviderEconomics provider(PlatformCommercialEconomicsRepository.ProviderCostSource source) {
        return new ProviderEconomics(
                source.aiProvider(),
                source.aiModel(),
                source.callCount(),
                wholeMinutesCeil(source.durationSeconds()),
                nonNegative(source.estimatedTelephonyCostUsd()),
                nonNegative(source.estimatedAiCostUsd()),
                nonNegative(source.estimatedTotalCostUsd()),
                toClp(nonNegative(source.estimatedTotalCostUsd())));
    }

    private BigDecimal toClp(BigDecimal usd) {
        BigDecimal rate = nonNegative(commercial.getUsdToClpRate());
        if (rate.signum() <= 0) return null;
        return nonNegative(usd).multiply(rate).setScale(0, RoundingMode.HALF_UP);
    }

    private static BigDecimal marginPercent(BigDecimal marginClp, long commercialValueClp) {
        if (marginClp == null || commercialValueClp <= 0) return null;
        return marginClp.multiply(ONE_HUNDRED)
                .divide(BigDecimal.valueOf(commercialValueClp), 1, RoundingMode.HALF_UP);
    }

    private static long overageValue(BigDecimal overage,
                                     BigDecimal unitSize,
                                     Integer unitPriceClp) {
        if (overage == null || overage.signum() <= 0 || unitSize == null || unitSize.signum() <= 0
                || unitPriceClp == null || unitPriceClp < 0) {
            return 0;
        }
        long units = overage.divide(unitSize, 0, RoundingMode.CEILING).longValueExact();
        return Math.multiplyExact(units, unitPriceClp.longValue());
    }

    private static boolean revenueEligible(String status) {
        if (status == null) return false;
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        return "ACTIVE".equals(normalized) || "PAST_DUE".equals(normalized);
    }

    private static long wholeMinutesFloor(BigDecimal seconds) {
        return nonNegative(seconds).divide(SIXTY, 0, RoundingMode.FLOOR).longValueExact();
    }

    private static long wholeMinutesCeil(BigDecimal seconds) {
        return nonNegative(seconds).divide(SIXTY, 0, RoundingMode.CEILING).longValueExact();
    }

    private static BigDecimal nonNegative(BigDecimal value) {
        return value == null || value.signum() < 0 ? BigDecimal.ZERO : value;
    }

    public record PortfolioEconomics(
            int businessCount,
            long basePlanValueClp,
            long estimatedOverageValueClp,
            long estimatedCommercialValueClp,
            BigDecimal estimatedPlatformCostUsd,
            BigDecimal estimatedPlatformCostClp,
            BigDecimal estimatedGrossMarginClp,
            BigDecimal estimatedGrossMarginPercent,
            BigDecimal usdToClpRate,
            long businessesWithUnknownCommercialValue,
            List<BusinessEconomics> businesses,
            List<ProviderEconomics> providers) {
    }

    public record BusinessEconomics(
            UUID businessId,
            String businessName,
            String planCode,
            String planName,
            String status,
            long includedMinutes,
            long usedMinutes,
            String usageAlertLevel,
            long basePlanValueClp,
            long estimatedOverageValueClp,
            Long estimatedCommercialValueClp,
            BigDecimal estimatedPlatformCostUsd,
            BigDecimal estimatedPlatformCostClp,
            BigDecimal estimatedGrossMarginClp,
            BigDecimal estimatedGrossMarginPercent,
            boolean commercialValueUnknown) {
    }

    public record ProviderEconomics(
            String aiProvider,
            String aiModel,
            long callCount,
            long minutes,
            BigDecimal estimatedTelephonyCostUsd,
            BigDecimal estimatedAiCostUsd,
            BigDecimal estimatedTotalCostUsd,
            BigDecimal estimatedTotalCostClp) {
    }
}
