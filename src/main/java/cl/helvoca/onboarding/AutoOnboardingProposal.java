package cl.helvoca.onboarding;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

public record AutoOnboardingProposal(
        String businessName,
        String sourceUrl,
        boolean sourceReadable,
        String sourceSummary,
        String timezone,
        String language,
        List<ServiceProposal> services,
        List<HourProposal> hours,
        List<KnowledgeProposal> knowledge,
        List<String> warnings
) {
    public record ServiceProposal(
            String name,
            String description,
            Integer durationMinutes,
            BigDecimal price
    ) {}

    public record HourProposal(
            int dayOfWeek,
            LocalTime openTime,
            LocalTime closeTime
    ) {}

    public record KnowledgeProposal(
            String title,
            String category,
            String content
    ) {}
}
