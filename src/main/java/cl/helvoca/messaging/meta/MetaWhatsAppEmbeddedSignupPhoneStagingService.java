package cl.helvoca.messaging.meta;

import cl.helvoca.audit.AuditService;
import cl.helvoca.common.ConflictException;
import cl.helvoca.messaging.outbound.MetaWhatsAppMessagingProvider;
import cl.helvoca.security.TenantProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class MetaWhatsAppEmbeddedSignupPhoneStagingService {
    private final MetaWhatsAppEmbeddedSignupSelectedPhoneRegistrationService registrationService;
    private final MetaWhatsAppTenantConfigurationService configurationService;
    private final MetaWhatsAppEmbeddedSignupPhoneRecordResolverService phoneRecordResolver;
    private final MetaWhatsAppEmbeddedSignupCredentialReferenceResolver credentialReferenceResolver;
    private final TenantProvider tenantProvider;
    private final AuditService auditService;

    @Autowired
    public MetaWhatsAppEmbeddedSignupPhoneStagingService(
            MetaWhatsAppEmbeddedSignupSelectedPhoneRegistrationService registrationService,
            MetaWhatsAppTenantConfigurationService configurationService,
            MetaWhatsAppEmbeddedSignupPhoneRecordResolverService phoneRecordResolver,
            MetaWhatsAppEmbeddedSignupCredentialReferenceResolver credentialReferenceResolver,
            TenantProvider tenantProvider,
            AuditService auditService) {
        this.registrationService = registrationService;
        this.configurationService = configurationService;
        this.phoneRecordResolver = phoneRecordResolver;
        this.credentialReferenceResolver = credentialReferenceResolver;
        this.tenantProvider = tenantProvider;
        this.auditService = auditService;
    }

    MetaWhatsAppEmbeddedSignupPhoneStagingService(
            MetaWhatsAppEmbeddedSignupSelectedPhoneRegistrationService registrationService,
            MetaWhatsAppTenantConfigurationService configurationService,
            MetaWhatsAppEmbeddedSignupPhoneRecordResolverService phoneRecordResolver,
            MetaWhatsAppEmbeddedSignupCredentialReferenceResolver credentialReferenceResolver) {
        this(
                registrationService,
                configurationService,
                phoneRecordResolver,
                credentialReferenceResolver,
                null,
                null);
    }

    public MetaWhatsAppEmbeddedSignupPhoneStagingResult registerAndStage(
            String wabaId,
            String phoneNumberId,
            String pin) {
        MetaWhatsAppEmbeddedSignupSelectedPhoneRegistrationResult registration =
                registrationService.register(
                        wabaId,
                        phoneNumberId,
                        pin);

        if (!registration.registered()) {
            throw new ConflictException("META_EMBEDDED_SIGNUP_PHONE_NOT_REGISTERED");
        }

        String credentialRef = credentialReferenceResolver.requireReference();
        UUID phoneRecordId = phoneRecordResolver.resolve(
                registration.displayPhoneNumber());

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

        if (auditService != null && tenantProvider != null) {
            UUID businessId = tenantProvider.requireBusinessId();
            auditService.humanSuccess(
                    businessId,
                    "META_WHATSAPP_PHONE_STAGED",
                    "META_WHATSAPP_CONFIG",
                    businessId,
                    null,
                    Map.of(
                            "phoneStaged", true,
                            "enabled", false));
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
