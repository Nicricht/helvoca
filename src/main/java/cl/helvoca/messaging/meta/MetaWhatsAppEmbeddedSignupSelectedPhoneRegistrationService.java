package cl.helvoca.messaging.meta;

import cl.helvoca.common.ConflictException;
import org.springframework.stereotype.Service;

@Service
public class MetaWhatsAppEmbeddedSignupSelectedPhoneRegistrationService {
    private final MetaWhatsAppEmbeddedSignupSelectedPhoneValidationService validationService;
    private final MetaWhatsAppEmbeddedSignupRegisterPhoneClient registerPhoneClient;
    private final MetaWhatsAppProperties metaProperties;

    public MetaWhatsAppEmbeddedSignupSelectedPhoneRegistrationService(
            MetaWhatsAppEmbeddedSignupSelectedPhoneValidationService validationService,
            MetaWhatsAppEmbeddedSignupRegisterPhoneClient registerPhoneClient,
            MetaWhatsAppProperties metaProperties) {
        this.validationService = validationService;
        this.registerPhoneClient = registerPhoneClient;
        this.metaProperties = metaProperties;
    }

    public MetaWhatsAppEmbeddedSignupSelectedPhoneRegistrationResult register(
            String wabaId,
            String phoneNumberId,
            String pin) {
        MetaWhatsAppEmbeddedSignupSelectedPhoneValidationResult validated =
                validationService.validate(wabaId, phoneNumberId);

        MetaWhatsAppEmbeddedSignupRegisterPhoneResult registration =
                registerPhoneClient.register(
                        validated.phoneNumberId(),
                        metaProperties.getEmbeddedSignupSystemUserAccessToken(),
                        pin);

        if (!registration.success()) {
            throw new ConflictException("META_EMBEDDED_SIGNUP_PHONE_REGISTRATION_FAILED");
        }

        return new MetaWhatsAppEmbeddedSignupSelectedPhoneRegistrationResult(
                "PHONE_NUMBER_REGISTERED",
                validated.phoneNumberId(),
                validated.displayPhoneNumber(),
                validated.verifiedName(),
                true);
    }
}
