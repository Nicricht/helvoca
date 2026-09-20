package cl.helvoca.messaging.meta;

import cl.helvoca.common.ConflictException;
import org.springframework.stereotype.Service;

@Service
public class MetaWhatsAppEmbeddedSignupSelectedWabaPhoneDiscoveryService {
    private final MetaWhatsAppEmbeddedSignupSelectedWabaSubscriptionService subscriptionService;
    private final MetaWhatsAppEmbeddedSignupPhoneNumberClient phoneNumberClient;
    private final MetaWhatsAppProperties metaProperties;

    public MetaWhatsAppEmbeddedSignupSelectedWabaPhoneDiscoveryService(
            MetaWhatsAppEmbeddedSignupSelectedWabaSubscriptionService subscriptionService,
            MetaWhatsAppEmbeddedSignupPhoneNumberClient phoneNumberClient,
            MetaWhatsAppProperties metaProperties) {
        this.subscriptionService = subscriptionService;
        this.phoneNumberClient = phoneNumberClient;
        this.metaProperties = metaProperties;
    }

    public MetaWhatsAppEmbeddedSignupSelectedWabaPhoneDiscoveryResult discover(
            String wabaId) {
        MetaWhatsAppEmbeddedSignupSelectedWabaSubscriptionResult subscription =
                subscriptionService.ensureSubscribed(wabaId);

        if (!subscription.appSubscribed()) {
            throw new ConflictException("META_EMBEDDED_SIGNUP_APP_NOT_SUBSCRIBED");
        }

        MetaWhatsAppEmbeddedSignupPhoneNumberPage phonePage =
                phoneNumberClient.list(
                        wabaId,
                        metaProperties.getEmbeddedSignupSystemUserAccessToken());

        return new MetaWhatsAppEmbeddedSignupSelectedWabaPhoneDiscoveryResult(
                "PHONE_NUMBERS_DISCOVERED",
                true,
                phonePage.phoneNumbers(),
                phonePage.afterCursor());
    }
}
