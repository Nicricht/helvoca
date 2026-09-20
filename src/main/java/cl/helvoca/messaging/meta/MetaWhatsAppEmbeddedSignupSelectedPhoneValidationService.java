package cl.helvoca.messaging.meta;

import cl.helvoca.common.ConflictException;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Service
public class MetaWhatsAppEmbeddedSignupSelectedPhoneValidationService {
    private static final int MAX_PHONE_PAGES = 100;

    private final MetaWhatsAppEmbeddedSignupSelectedWabaPhoneDiscoveryService discoveryService;
    private final MetaWhatsAppEmbeddedSignupPhoneNumberClient phoneNumberClient;
    private final MetaWhatsAppProperties metaProperties;

    public MetaWhatsAppEmbeddedSignupSelectedPhoneValidationService(
            MetaWhatsAppEmbeddedSignupSelectedWabaPhoneDiscoveryService discoveryService,
            MetaWhatsAppEmbeddedSignupPhoneNumberClient phoneNumberClient,
            MetaWhatsAppProperties metaProperties) {
        this.discoveryService = discoveryService;
        this.phoneNumberClient = phoneNumberClient;
        this.metaProperties = metaProperties;
    }

    public MetaWhatsAppEmbeddedSignupSelectedPhoneValidationResult validate(
            String wabaId,
            String phoneNumberId) {
        String cleanPhoneNumberId = requireNumericId(
                phoneNumberId,
                "Meta phone number id is required",
                "Meta phone number id is invalid");

        MetaWhatsAppEmbeddedSignupSelectedWabaPhoneDiscoveryResult discovery =
                discoveryService.discover(wabaId);

        MetaWhatsAppEmbeddedSignupPhoneNumberPage.PhoneNumber selected =
                find(discovery.phoneNumbers(), cleanPhoneNumberId);
        if (selected != null) {
            return result(selected);
        }

        Set<String> seenCursors = new HashSet<>();
        String cursor = clean(discovery.afterCursor());

        for (int pageNumber = 1; cursor != null && pageNumber < MAX_PHONE_PAGES; pageNumber++) {
            if (!seenCursors.add(cursor)) {
                throw new IllegalStateException(
                        "Meta Embedded Signup phone number pagination repeated a cursor");
            }

            MetaWhatsAppEmbeddedSignupPhoneNumberPage page =
                    phoneNumberClient.list(
                            wabaId,
                            metaProperties.getEmbeddedSignupSystemUserAccessToken(),
                            cursor);

            selected = find(page.phoneNumbers(), cleanPhoneNumberId);
            if (selected != null) {
                return result(selected);
            }

            cursor = clean(page.afterCursor());
        }

        if (cursor != null) {
            throw new IllegalStateException(
                    "Meta Embedded Signup phone number pagination exceeded safe limit");
        }

        throw new ConflictException("META_EMBEDDED_SIGNUP_PHONE_NOT_IN_SELECTED_WABA");
    }

    private static MetaWhatsAppEmbeddedSignupPhoneNumberPage.PhoneNumber find(
            java.util.List<MetaWhatsAppEmbeddedSignupPhoneNumberPage.PhoneNumber> phoneNumbers,
            String selectedPhoneNumberId) {
        return phoneNumbers.stream()
                .filter(phone -> selectedPhoneNumberId.equals(phone.id()))
                .findFirst()
                .orElse(null);
    }

    private static MetaWhatsAppEmbeddedSignupSelectedPhoneValidationResult result(
            MetaWhatsAppEmbeddedSignupPhoneNumberPage.PhoneNumber phone) {
        return new MetaWhatsAppEmbeddedSignupSelectedPhoneValidationResult(
                "PHONE_NUMBER_VALIDATED",
                phone.id(),
                phone.displayPhoneNumber(),
                phone.verifiedName(),
                phone.qualityRating(),
                phone.codeVerificationStatus());
    }

    private static String requireNumericId(
            String value,
            String missingMessage,
            String invalidMessage) {
        String clean = clean(value);
        if (clean == null) {
            throw new IllegalArgumentException(missingMessage);
        }
        if (!clean.matches("[0-9]{1,80}")) {
            throw new IllegalArgumentException(invalidMessage);
        }
        return clean;
    }

    private static String clean(String value) {
        String clean = value == null ? "" : value.trim();
        return clean.isBlank() ? null : clean;
    }
}
