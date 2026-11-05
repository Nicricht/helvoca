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
    private final MetaWhatsAppCoexistencePhoneStatusClient coexistenceStatus;
    private final TenantProvider tenantProvider;
    private final AuditService auditService;

    @Autowired
    public MetaWhatsAppEmbeddedSignupSelectedPhoneRegistrationService(
            MetaWhatsAppEmbeddedSignupSelectedPhoneValidationService validationService,
            MetaWhatsAppEmbeddedSignupRegisterPhoneClient registerPhoneClient,
            MetaWhatsAppProperties metaProperties,
            MetaWhatsAppCoexistencePhoneStatusClient coexistenceStatus,
            TenantProvider tenantProvider,
            AuditService auditService) {
        this.validationService = validationService;
        this.registerPhoneClient = registerPhoneClient;
        this.metaProperties = metaProperties;
        this.coexistenceStatus = coexistenceStatus;
        this.tenantProvider = tenantProvider;
        this.auditService = auditService;
    }

    MetaWhatsAppEmbeddedSignupSelectedPhoneRegistrationService(
            MetaWhatsAppEmbeddedSignupSelectedPhoneValidationService validationService,
            MetaWhatsAppEmbeddedSignupRegisterPhoneClient registerPhoneClient,
            MetaWhatsAppProperties metaProperties) {
        this(validationService, registerPhoneClient, metaProperties, null, null, null);
    }

    public MetaWhatsAppEmbeddedSignupSelectedPhoneRegistrationResult register(
            String wabaId,
            String phoneNumberId,
            String pin) {
        // An existing WhatsApp Business App number must use the coexistence path,
        // never the ordinary Cloud API registration flow.
        if (!metaProperties.isStandardRegistrationEnabled()) {
            throw new ConflictException("META_STANDARD_REGISTRATION_DISABLED");
        }
        MetaWhatsAppEmbeddedSignupSelectedPhoneValidationResult validated =
                validationService.validate(wabaId, phoneNumberId);
        if (coexistenceStatus == null) {
            throw new ConflictException("META_PHONE_BUSINESS_APP_STATUS_UNAVAILABLE");
        }
        if (coexistenceStatus.isOnBusinessApp(
                validated.phoneNumberId(),
                metaProperties.getEmbeddedSignupSystemUserAccessToken())) {
            throw new ConflictException("META_EXISTING_BUSINESS_APP_REQUIRES_COEXISTENCE");
        }

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
