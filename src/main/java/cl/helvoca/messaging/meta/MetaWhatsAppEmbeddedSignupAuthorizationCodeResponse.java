package cl.helvoca.messaging.meta;

import java.util.List;

public record MetaWhatsAppEmbeddedSignupAuthorizationCodeResponse(
        String state,
        boolean accepted,
        boolean retained,
        boolean exchangePending,
        List<WabaCandidate> wabas,
        String wabaAfterCursor
) {
    public MetaWhatsAppEmbeddedSignupAuthorizationCodeResponse {
        wabas = wabas == null ? List.of() : List.copyOf(wabas);
    }

    public record WabaCandidate(
            String id,
            String name,
            String currency,
            String timezoneId,
            String messageTemplateNamespace,
            boolean systemUserAssigned
    ) {
    }
}
