package cl.helvoca.messaging.meta;

import cl.helvoca.common.ConflictException;
import cl.helvoca.messaging.outbound.MetaWhatsAppMessagingProvider;
import cl.helvoca.security.TenantProvider;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Coexistence finalization does NOT call /register or require a PIN.
 * Accept only verified Meta Business App + Cloud API numbers and stage
 * tenant-scoped configuration with delivery disabled.
 */
@Service
public class MetaWhatsAppCoexistenceStagingService {
    private final MetaWhatsAppProperties properties;
    private final MetaWhatsAppEmbeddedSignupReadinessService readiness;
    private final MetaWhatsAppCoexistencePhoneStatusClient statusClient;
    private final MetaWhatsAppEmbeddedSignupSelectedPhoneValidationService validationService;
    private final MetaWhatsAppEmbeddedSignupPhoneRecordResolverService phoneRecordResolver;
    private final MetaWhatsAppEmbeddedSignupCredentialReferenceResolver credentials;
    private final MetaWhatsAppTenantConfigurationService tenantConfig;
    private final TenantProvider tenantProvider;

    public MetaWhatsAppCoexistenceStagingService(
            MetaWhatsAppProperties properties,
            MetaWhatsAppEmbeddedSignupReadinessService readiness,
            MetaWhatsAppCoexistencePhoneStatusClient statusClient,
            MetaWhatsAppEmbeddedSignupSelectedPhoneValidationService validationService,
            MetaWhatsAppEmbeddedSignupPhoneRecordResolverService phoneRecordResolver,
            MetaWhatsAppEmbeddedSignupCredentialReferenceResolver credentials,
            MetaWhatsAppTenantConfigurationService tenantConfig,
            TenantProvider tenantProvider) {
        this.properties = properties;
        this.readiness = readiness;
        this.statusClient = statusClient;
        this.validationService = validationService;
        this.phoneRecordResolver = phoneRecordResolver;
        this.credentials = credentials;
        this.tenantConfig = tenantConfig;
        this.tenantProvider = tenantProvider;
    }

    public MetaWhatsAppEmbeddedSignupPhoneStagingResult stage(
            String wabaId, String phoneNumberId) {
        UUID businessId = tenantProvider.requireBusinessId();
        // Require either the exact controlled-pilot tenant/phone pair or the explicit
        // tenant self-service gate. Both modes remain disabled unless an operator opts in.
        if (!properties.isCoexistenceEnabled() || !coexistenceTenantAllowed(businessId)) {
            throw new ConflictException("META_COEXISTENCE_NOT_AUTHORIZED_FOR_TENANT");
        }
        if (!readiness.readiness().readyForEmbeddedSignup()) {
            throw new ConflictException("META_COEXISTENCE_PROVIDER_NOT_READY");
        }
        if (phoneNumberId == null || !phoneNumberId.matches("[0-9]{5,30}")
                || wabaId == null || !wabaId.matches("[0-9]{5,30}")) {
            throw new IllegalArgumentException("Invalid Meta identifiers");
        }

        // Read-only carrier proof FIRST. Never call ordinary /register here.
        MetaWhatsAppCoexistencePhoneStatusClient.Status carrier =
                statusClient.read(phoneNumberId,
                        properties.getEmbeddedSignupSystemUserAccessToken());
        if (!carrier.verified()) {
            throw new ConflictException("META_COEXISTENCE_NOT_CONFIRMED_BY_PROVIDER");
        }
        String providerPhone = MetaWhatsAppEmbeddedSignupPhoneRecordResolverService
                .normalizeE164(carrier.displayPhoneNumber());
        if (!coexistencePhoneAllowed(providerPhone)) {
            throw new ConflictException("META_COEXISTENCE_WRONG_PHONE");
        }

        // Confirm membership of the selected WABA and verify number consistency.
        MetaWhatsAppEmbeddedSignupSelectedPhoneValidationResult validated =
                validationService.validate(wabaId, phoneNumberId);
        if (!phoneNumberId.equals(validated.phoneNumberId())
                || !providerPhone.equals(
                        MetaWhatsAppEmbeddedSignupPhoneRecordResolverService
                                .normalizeE164(validated.displayPhoneNumber()))) {
            throw new ConflictException("META_COEXISTENCE_WABA_PHONE_MISMATCH");
        }

        String credentialRef = credentials.requireReference();
        UUID phoneRecordId = phoneRecordResolver.resolve(providerPhone);
        MetaWhatsAppTenantConfigurationResponse staged =
                tenantConfig.replace(new MetaWhatsAppTenantConfigurationRequest(
                        phoneRecordId,
                        MetaWhatsAppMessagingProvider.ID,
                        phoneNumberId,
                        credentialRef,
                        wabaId));
        if (staged.enabled()) {
            throw new IllegalStateException("Meta coexistence staging must remain disabled");
        }
        return new MetaWhatsAppEmbeddedSignupPhoneStagingResult(
                "COEXISTENCE_VERIFIED_AND_STAGED",
                staged.phoneRecordId(), staged.provider(), staged.providerPhoneNumberId(),
                staged.wabaId(), staged.credentialRef(), false);
    }

    private boolean coexistenceTenantAllowed(UUID businessId) {
        String allowedBusinessId = properties.getCoexistenceAllowedBusinessId();
        String allowedPhone = properties.getCoexistenceAllowedPhone();
        boolean hasBusinessAllowlist = !allowedBusinessId.isBlank();
        boolean hasPhoneAllowlist = !allowedPhone.isBlank();

        // A partial pilot allowlist is a configuration error, never a reason
        // to fall back to unrestricted tenant self-service.
        if (hasBusinessAllowlist != hasPhoneAllowlist) return false;
        if (hasBusinessAllowlist) return businessId.toString().equals(allowedBusinessId);
        return properties.isCoexistenceSelfServiceEnabled();
    }

    private boolean coexistencePhoneAllowed(String providerPhone) {
        String allowedBusinessId = properties.getCoexistenceAllowedBusinessId();
        String allowedPhone = properties.getCoexistenceAllowedPhone();
        // stage() already rejects partial allowlists before contacting Meta.
        if (!allowedBusinessId.isBlank()) return allowedPhone.equals(providerPhone);
        return properties.isCoexistenceSelfServiceEnabled();
    }
}
