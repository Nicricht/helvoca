package cl.helvoca.messaging.meta;

import cl.helvoca.audit.AuditService;
import cl.helvoca.common.ConflictException;
import cl.helvoca.security.TenantProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class MetaWhatsAppEmbeddedSignupSelectedPhoneRegistrationService {
    private final MetaWhatsAppEmbeddedSignupSelectedPhoneValidationService validationService;
    private final MetaWhatsAppEmbeddedSignupRegisterPhoneClient registerPhoneClient;
    private final MetaWhatsAppProperties metaProperties;
    private final TenantProvider tenantProvider;
    private final AuditService auditService;

    @Autowired
    public MetaWhatsAppEmbeddedSignupSelectedPhoneRegistrationService(
            MetaWhatsAppEmbeddedSignupSelectedPhoneValidationService validationService,
            MetaWhatsAppEmbeddedSignupRegisterPhoneClient registerPhoneClient,
            MetaWhatsAppProperties metaProperties,
            TenantProvider tenantProvider,
            AuditService auditService) {
        this.validationService = validationService;
        this.registerPhoneClient = registerPhoneClient;
        this.metaProperties = metaProperties;
        this.tenantProvider = tenantProvider;
        this.auditService = auditService;
    }

    MetaWhatsAppEmbeddedSignupSelectedPhoneRegistrationService(
            MetaWhatsAppEmbeddedSignupSelectedPhoneValidationService validationService,
            MetaWhatsAppEmbeddedSignupRegisterPhoneClient registerPhoneClient,
            MetaWhatsAppProperties metaProperties) {
        this(validationService, registerPhoneClient, metaProperties, null, null);
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

        if (auditService != null && tenantProvider != null) {
            UUID businessId = tenantProvider.requireBusinessId();
            auditService.humanSuccess(
                    businessId,
                    "META_WHATSAPP_PHONE_REGISTERED",
                    "META_WHATSAPP_CONFIG",
                    businessId,
                    null,
                    Map.of(
                            "phoneRegistered", true,
                            "phoneValidated", true));
        }

        return new MetaWhatsAppEmbeddedSignupSelectedPhoneRegistrationResult(
                "PHONE_NUMBER_REGISTERED",
                validated.phoneNumberId(),
                validated.displayPhoneNumber(),
                validated.verifiedName(),
                true);
    }
}
