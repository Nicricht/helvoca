package cl.helvoca.messaging.meta;

import java.util.List;

public record MetaWhatsAppEmbeddedSignupTokenDebugResult(
        boolean valid,
        String appId,
        String type,
        Long expiresAt,
        Long dataAccessExpiresAt,
        List<String> scopes,
        List<String> granularTargetIds
) {
    public MetaWhatsAppEmbeddedSignupTokenDebugResult {
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
        granularTargetIds = granularTargetIds == null ? List.of() : List.copyOf(granularTargetIds);
    }
}
