package cl.helvoca.onboarding;

public record OnboardingStatusResponse(
        boolean businessProfileConfigured,
        boolean servicesConfigured,
        boolean scheduleConfigured,
        boolean knowledgeConfigured,
        boolean humanTransferConfigured,
        boolean phoneConfigured,
        boolean readyForCalls,
        String nextStep,
        SectorResourceReadiness resourceBookingReadiness
) {
    /**
     * Legacy callers describe basic phone/configuration readiness only.
     * They must never imply that per-resource bookings are certified.
     */
    public OnboardingStatusResponse(
            boolean businessProfileConfigured,
            boolean servicesConfigured,
            boolean scheduleConfigured,
            boolean knowledgeConfigured,
            boolean humanTransferConfigured,
            boolean phoneConfigured,
            boolean readyForCalls,
            String nextStep) {
        this(businessProfileConfigured, servicesConfigured, scheduleConfigured,
                knowledgeConfigured, humanTransferConfigured, phoneConfigured,
                readyForCalls, nextStep, SectorResourceReadiness.forPreset(null));
    }
}
