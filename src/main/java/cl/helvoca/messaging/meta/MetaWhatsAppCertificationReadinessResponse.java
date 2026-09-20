package cl.helvoca.messaging.meta;

import java.util.List;

public record MetaWhatsAppCertificationReadinessResponse(
        String state,
        boolean ready,
        boolean alreadyCertified,
        List<Blocker> blockers
) {
    public MetaWhatsAppCertificationReadinessResponse {
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
    }

    public record Blocker(String code, String message) { }
}
