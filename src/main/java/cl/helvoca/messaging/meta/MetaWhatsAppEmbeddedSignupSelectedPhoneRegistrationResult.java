package cl.helvoca.messaging.meta;

public record MetaWhatsAppEmbeddedSignupSelectedPhoneRegistrationResult(
        String state,
        String phoneNumberId,
        String displayPhoneNumber,
        String verifiedName,
        boolean registered
) {
}
