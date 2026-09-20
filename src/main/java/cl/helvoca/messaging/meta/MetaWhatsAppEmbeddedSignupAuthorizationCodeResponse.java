package cl.helvoca.messaging.meta;

public record MetaWhatsAppEmbeddedSignupAuthorizationCodeResponse(
        String state,
        boolean accepted,
        boolean retained,
        boolean exchangePending
) {
}
