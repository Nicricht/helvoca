package cl.helvoca.onboarding;

import java.util.List;

public record CommercialReadinessResponse(
        int progressPercent,
        boolean readyForProduction,
        boolean readyForCalls,
        boolean readyForWhatsApp,
        String planCode,
        String planName,
        String subscriptionStatus,
        boolean serviceAllowed,
        int includedMinutes,
        long usedMinutes,
        long overageMinutes,
        boolean billingConnected,
        boolean billingCheckoutConfigured,
        boolean whatsappEnabled,
        List<String> blockers,
        List<String> warnings
) {}
