package cl.helvoca.billing;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/v1/public/pricing")
public class PublicPricingController {

    @GetMapping
    public List<PlanOffer> plans() {
        return Arrays.stream(PlanCode.values()).map(PlanOffer::from).toList();
    }

    public record PlanOffer(
            String code,
            String name,
            int monthlyPriceClp,
            int includedMinutes,
            int maxConcurrentCalls,
            Integer overagePerMinuteClp,
            boolean customPricing,
            boolean recommended) {
        static PlanOffer from(PlanCode plan) {
            return new PlanOffer(
                    plan.getPublicCode(),
                    plan.getDisplayName(),
                    plan.getMonthlyPriceClp(),
                    plan.getIncludedMinutesPerPeriod(),
                    plan.getMaxConcurrentCalls(),
                    plan.getOveragePerMinuteClp(),
                    plan.isCustomPricing(),
                    plan.isRecommended());
        }
    }
}
