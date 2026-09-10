package cl.helvoca.onboarding;

public record OnboardingStatusResponse(
        boolean businessProfileConfigured,
        boolean servicesConfigured,
        boolean scheduleConfigured,
        boolean knowledgeConfigured,
        boolean humanTransferConfigured,
        boolean phoneConfigured,
        boolean readyForCalls,
        String nextStep
) {}
