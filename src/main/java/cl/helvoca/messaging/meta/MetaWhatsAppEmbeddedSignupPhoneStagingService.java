package cl.helvoca.messaging.meta;

import cl.helvoca.common.ConflictException;
import cl.helvoca.messaging.outbound.MetaWhatsAppMessagingProvider;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class MetaWhatsAppEmbeddedSignupPhoneStagingService {
    private final MetaWhatsAppEmbeddedSignupSelectedPhoneRegistrationService registrationService;
    private final MetaWhatsAppTenantConfigurationService configurationService;

    public MetaWhatsAppEmbeddedSignupPhoneStagingService(
            MetaWhatsAppEmbeddedSignupSelectedPhoneRegistrationService registrationService,
            MetaWhatsAppTenantConfigurationService configurationService) {
        this.registrationService = registrationService;
        this.configurationService = configurationService;
    }

    public MetaWhatsAppEmbeddedSignupPhoneStagingResult registerAndStage(
            UUID phoneRecordId,
            String wabaId,
            String phoneNumberId,
            String credentialRef,
            String pin) {
        if (phoneRecordId == null) {
            throw new IllegalArgumentException("Phone record id is required");
        }

        MetaWhatsAppEmbeddedSignupSelectedPhoneRegistrationResult registration =
                registrationService.register(
                        wabaId,
                        phoneNumberId,
                        pin);

        if (!registration.registered()) {
            throw new ConflictException("META_EMBEDDED_SIGNUP_PHONE_NOT_REGISTERED");
        }

        MetaWhatsAppTenantConfigurationResponse staged =
                configurationService.replace(
                        new MetaWhatsAppTenantConfigurationRequest(
                                phoneRecordId,
                                MetaWhatsAppMessagingProvider.ID,
                                registration.phoneNumberId(),
                                credentialRef,
                                wabaId));

        if (staged.enabled()) {
            throw new IllegalStateException(
                    "Meta Embedded Signup staged configuration must remain disabled");
        }

        return new MetaWhatsAppEmbeddedSignupPhoneStagingResult(
                "PHONE_NUMBER_REGISTERED_AND_STAGED",
                staged.phoneRecordId(),
                staged.provider(),
                staged.providerPhoneNumberId(),
                staged.wabaId(),
                staged.credentialRef(),
                false);
    }
}
