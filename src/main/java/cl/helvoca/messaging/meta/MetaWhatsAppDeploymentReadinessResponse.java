package cl.helvoca.messaging.meta;

import java.util.List;

public record MetaWhatsAppDeploymentReadinessResponse(
        String state,
        boolean readyForTenantStaging,
        boolean webhookValidationEnabled,
        boolean appSecretConfigured,
        boolean verifyTokenConfigured,
        boolean globalMetaEnabled,
        boolean outboundDeliveryEnabled,
        String outboundProvider,
        boolean jobsEnabled,
        List<Blocker> blockers
) {
    public MetaWhatsAppDeploymentReadinessResponse {
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
    }

    public record Blocker(String code, String message) {
    }
}
