package cl.helvoca.billing;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@RestController
@RequestMapping("/api/v1/public/pricing")
public class PublicPricingController {
    private static final BigDecimal SECONDS_PER_MINUTE = BigDecimal.valueOf(60);

    private final CommercialPlanCatalogService catalog;

    public PublicPricingController(CommercialPlanCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public List<PlanOffer> plans() {
        return catalog.activePlans().stream().map(PublicPricingController::from).toList();
    }

    private static PlanOffer from(CommercialPlanCatalogService.Plan plan) {
        CommercialPlanCatalogService.EntitlementRule voice = plan.entitlement("VOICE_SECONDS");
        CommercialPlanCatalogService.EntitlementRule capacity = plan.entitlement("CONCURRENT_CALLS");

        int includedMinutes = 0;
        Integer overagePerMinuteClp = null;
        if (voice != null && "USAGE".equalsIgnoreCase(voice.kind()) && "SECONDS".equalsIgnoreCase(voice.unit())) {
            includedMinutes = voice.limitValue()
                    .divide(SECONDS_PER_MINUTE, 0, RoundingMode.FLOOR)
                    .intValueExact();
            if (voice.overageUnitSize() != null
                    && voice.overageUnitSize().compareTo(SECONDS_PER_MINUTE) == 0) {
                overagePerMinuteClp = voice.overagePriceClp();
            }
        }

        int maxConcurrentCalls = 0;
        if (capacity != null && "CAPACITY".equalsIgnoreCase(capacity.kind())) {
            try {
                maxConcurrentCalls = capacity.limitValue().intValueExact();
            } catch (ArithmeticException e) {
                throw new IllegalStateException("Commercial plan capacity is not an integer: " + plan.code(), e);
            }
        }

        return new PlanOffer(
                plan.publicCode(),
                plan.displayName(),
                plan.monthlyPriceClp() == null ? 0 : plan.monthlyPriceClp(),
                includedMinutes,
                maxConcurrentCalls,
                overagePerMinuteClp,
                plan.customPricing(),
                plan.recommended());
    }

    public record PlanOffer(
            String code,
            String name,
            int monthlyPriceClp,
            int includedMinutes,
            int maxConcurrentCalls,
            Integer overagePerMinuteClp,
            boolean customPricing,
            boolean recommended) {}
}
