package cl.helvoca.messaging.meta;

import cl.helvoca.audit.AuditService;
import cl.helvoca.common.ConflictException;
import cl.helvoca.security.TenantProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class MetaWhatsAppEmbeddedSignupSelectedPhoneValidationService {
    private static final int MAX_PHONE_PAGES = 100;

    private final MetaWhatsAppEmbeddedSignupSelectedWabaSubscriptionService subscriptionService;
    private final MetaWhatsAppEmbeddedSignupPhoneNumberClient phoneNumberClient;
    private final MetaWhatsAppProperties metaProperties;
    private final TenantProvider tenantProvider;
    private final AuditService auditService;

    @Autowired
    public MetaWhatsAppEmbeddedSignupSelectedPhoneValidationService(
            MetaWhatsAppEmbeddedSignupSelectedWabaSubscriptionService subscriptionService,
            MetaWhatsAppEmbeddedSignupPhoneNumberClient phoneNumberClient,
            MetaWhatsAppProperties metaProperties,
            TenantProvider tenantProvider,
            AuditService auditService) {
        this.subscriptionService = subscriptionService;
        this.phoneNumberClient = phoneNumberClient;
        this.metaProperties = metaProperties;
        this.tenantProvider = tenantProvider;
        this.auditService = auditService;
    }

    MetaWhatsAppEmbeddedSignupSelectedPhoneValidationService(
            MetaWhatsAppEmbeddedSignupSelectedWabaSubscriptionService subscriptionService,
            MetaWhatsAppEmbeddedSignupPhoneNumberClient phoneNumberClient,
            MetaWhatsAppProperties metaProperties) {
        this(subscriptionService, phoneNumberClient, metaProperties, null, null);
    }

    public MetaWhatsAppEmbeddedSignupSelectedPhoneValidationResult validate(
            String wabaId,
            String phoneNumberId) {
        String cleanWabaId = requireNumericId(
                wabaId,
                "Meta WABA id is required",
                "Meta WABA id is invalid");
        String cleanPhoneNumberId = requireNumericId(
                phoneNumberId,
                "Meta phone number id is required",
                "Meta phone number id is invalid");

        MetaWhatsAppEmbeddedSignupSelectedWabaSubscriptionResult subscription =
                subscriptionService.ensureSubscribed(cleanWabaId);

        if (!subscription.appSubscribed()) {
            throw new ConflictException("META_EMBEDDED_SIGNUP_APP_NOT_SUBSCRIBED");
        }

        Set<String> seenCursors = new HashSet<>();
        String cursor = null;

        for (int pageNumber = 0; pageNumber < MAX_PHONE_PAGES; pageNumber++) {
            MetaWhatsAppEmbeddedSignupPhoneNumberPage page =
                    cursor == null
                            ? phoneNumberClient.list(
                                    cleanWabaId,
                                    metaProperties.getEmbeddedSignupSystemUserAccessToken())
                            : phoneNumberClient.list(
                                    cleanWabaId,
                                    metaProperties.getEmbeddedSignupSystemUserAccessToken(),
                                    cursor);

            for (MetaWhatsAppEmbeddedSignupPhoneNumberPage.PhoneNumber phone : page.phoneNumbers()) {
                if (cleanPhoneNumberId.equals(phone.id())) {
                    if (auditService != null && tenantProvider != null) {
                        UUID businessId = tenantProvider.requireBusinessId();
                        auditService.humanSuccess(
                                businessId,
                                "META_WHATSAPP_PHONE_VALIDATED",
                                "META_WHATSAPP_CONFIG",
                                businessId,
                                null,
                                Map.of(
                                        "phoneValidated", true,
                                        "appSubscribed", true));
                    }

                    return new MetaWhatsAppEmbeddedSignupSelectedPhoneValidationResult(
                            "PHONE_NUMBER_VALIDATED",
                            phone.id(),
                            phone.displayPhoneNumber(),
                            phone.verifiedName(),
                            phone.qualityRating(),
                            phone.codeVerificationStatus());
                }
            }

            String nextCursor = clean(page.afterCursor());
            if (nextCursor == null) {
                throw new ConflictException("META_EMBEDDED_SIGNUP_PHONE_NOT_IN_WABA");
            }
            if (!seenCursors.add(nextCursor)) {
                throw new IllegalStateException(
                        "Meta Embedded Signup phone number pagination repeated a cursor");
            }
            cursor = nextCursor;
        }

        throw new IllegalStateException(
                "Meta Embedded Signup phone number pagination exceeded safe limit");
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
