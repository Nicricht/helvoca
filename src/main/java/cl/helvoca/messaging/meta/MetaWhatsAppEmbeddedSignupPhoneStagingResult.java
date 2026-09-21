package cl.helvoca.messaging.meta;

import java.util.UUID;

public record MetaWhatsAppEmbeddedSignupPhoneStagingResult(
        String state,
        UUID phoneRecordId,
        String provider,
        String phoneNumberId,
        String wabaId,
        String credentialRef,
        boolean enabled
) {
}
