package cl.helvoca.messaging.meta;

import java.util.List;

public record MetaWhatsAppEmbeddedSignupReadinessResponse(
        String state,
        boolean readyForEmbeddedSignup,
        boolean embeddedSignupEnabled,
        boolean appIdConfigured,
        boolean configIdConfigured,
        boolean appSecretConfigured,
        boolean verifyTokenConfigured,
        boolean webhookValidationEnabled,
        List<Blocker> blockers
) {
    public MetaWhatsAppEmbeddedSignupReadinessResponse {
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
    }

    public record Blocker(String code, String message) {
    }
}
