package cl.helvoca.messaging.meta;

import java.util.List;

public record MetaWhatsAppEmbeddedSignupAuthorizationCodeResponse(
        String state,
        boolean accepted,
        boolean retained,
        boolean exchangePending,
        List<MetaWhatsAppEmbeddedSignupSharedWabaPage.Waba> wabas,
        String wabaAfterCursor
) {
    public MetaWhatsAppEmbeddedSignupAuthorizationCodeResponse {
        wabas = wabas == null ? List.of() : List.copyOf(wabas);
    }
}
