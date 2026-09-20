package cl.helvoca.messaging.meta;

public record MetaWhatsAppEmbeddedSignupSelectedPhoneValidationResult(
        String state,
        String phoneNumberId,
        String displayPhoneNumber,
        String verifiedName,
        String qualityRating,
        String codeVerificationStatus
) {
}
