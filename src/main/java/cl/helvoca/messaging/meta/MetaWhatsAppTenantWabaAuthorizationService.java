package cl.helvoca.messaging.meta;

import cl.helvoca.common.ConflictException;
import cl.helvoca.security.TenantProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Binds the WABA proven by the customer's one-time Meta authorization code
 * to the currently authenticated RecepVoz tenant. Later WABA operations must
 * match this persisted tenant binding.
 */
@Service
public class MetaWhatsAppTenantWabaAuthorizationService {
    private final MetaWhatsAppTenantConfigRepository configs;
    private final MetaWhatsAppEmbeddedSignupCredentialReferenceResolver credentials;
    private final TenantProvider tenantProvider;

    public MetaWhatsAppTenantWabaAuthorizationService(
            MetaWhatsAppTenantConfigRepository configs,
            MetaWhatsAppEmbeddedSignupCredentialReferenceResolver credentials,
            TenantProvider tenantProvider) {
        this.configs = configs;
        this.credentials = credentials;
        this.tenantProvider = tenantProvider;
    }

    @Transactional
    public void authorize(String rawWabaId) {
        UUID businessId = tenantProvider.requireBusinessId();
        String wabaId = requireWabaId(rawWabaId);
        MetaWhatsAppTenantConfig config = configs.findById(businessId)
                .orElseGet(MetaWhatsAppTenantConfig::new);

        String existingWabaId = clean(config.getWabaId());
        if (config.isEnabled()) {
            // Never silently disable or replace a live sender during onboarding.
            if (wabaId.equals(existingWabaId)) return;
            throw new ConflictException("META_WHATSAPP_ACTIVE_CONFIG_REQUIRES_DEACTIVATION");
        }
        if (existingWabaId != null && !existingWabaId.equals(wabaId)) {
            throw new ConflictException("META_WHATSAPP_TENANT_ALREADY_BOUND_TO_ANOTHER_WABA");
        }

        String credentialRef = credentials.requireReference();
        config.setBusinessId(businessId);
        config.setWabaId(wabaId);
        config.setCredentialRef(credentialRef);
        config.setEnabled(false);
        configs.saveAndFlush(config);
    }

    @Transactional(readOnly = true)
    public void requireAuthorized(String rawWabaId) {
        UUID businessId = tenantProvider.requireBusinessId();
        String wabaId = requireWabaId(rawWabaId);
        MetaWhatsAppTenantConfig config = configs.findById(businessId)
                .orElseThrow(() -> new ConflictException("META_WHATSAPP_WABA_NOT_AUTHORIZED_FOR_TENANT"));
        if (!wabaId.equals(clean(config.getWabaId()))) {
            throw new ConflictException("META_WHATSAPP_WABA_NOT_AUTHORIZED_FOR_TENANT");
        }
    }

    private static String requireWabaId(String value) {
        String clean = clean(value);
        if (clean == null || !clean.matches("[0-9]{5,30}")) {
            throw new IllegalArgumentException("Invalid Meta WABA id");
        }
        return clean;
    }

    private static String clean(String value) {
        String clean = value == null ? "" : value.trim();
        return clean.isBlank() ? null : clean;
    }
}
