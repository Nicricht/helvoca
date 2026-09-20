package cl.helvoca.messaging.meta;

import java.util.List;

public record MetaWhatsAppEmbeddedSignupPhoneNumberPage(
        List<PhoneNumber> phoneNumbers,
        String afterCursor
) {
    public MetaWhatsAppEmbeddedSignupPhoneNumberPage {
        phoneNumbers = phoneNumbers == null ? List.of() : List.copyOf(phoneNumbers);
    }

    public record PhoneNumber(
            String id,
            String displayPhoneNumber,
            String verifiedName,
            String qualityRating,
            String codeVerificationStatus
    ) {
    }
}
