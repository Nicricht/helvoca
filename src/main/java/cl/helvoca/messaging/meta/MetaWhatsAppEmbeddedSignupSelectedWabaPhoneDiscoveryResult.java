package cl.helvoca.messaging.meta;

import java.util.List;

public record MetaWhatsAppEmbeddedSignupSelectedWabaPhoneDiscoveryResult(
        String state,
        boolean appSubscribed,
        List<MetaWhatsAppEmbeddedSignupPhoneNumberPage.PhoneNumber> phoneNumbers,
        String afterCursor
) {
    public MetaWhatsAppEmbeddedSignupSelectedWabaPhoneDiscoveryResult {
        phoneNumbers = phoneNumbers == null ? List.of() : List.copyOf(phoneNumbers);
    }
}
