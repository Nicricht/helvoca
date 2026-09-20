package cl.helvoca.messaging.meta;

import cl.helvoca.common.NotFoundException;
import cl.helvoca.messaging.outbound.MetaWhatsAppMessagingProvider;
import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.phone.PhoneNumberRepository;
import cl.helvoca.security.TenantProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
public class MetaWhatsAppTenantConfigurationService {
    private final MetaWhatsAppTenantConfigRepository configs;
    private final PhoneNumberRepository phones;
    private final TenantProvider tenantProvider;

    public MetaWhatsAppTenantConfigurationService(
            MetaWhatsAppTenantConfigRepository configs,
            PhoneNumberRepository phones,
            TenantProvider tenantProvider) {
        this.configs = configs;
        this.phones = phones;
        this.tenantProvider = tenantProvider;
    }

    @Transactional
    public MetaWhatsAppTenantConfigurationResponse replace(
            MetaWhatsAppTenantConfigurationRequest request) {
        UUID businessId = tenantProvider.requireBusinessId();

        if (request == null) {
            throw new IllegalArgumentException("Meta WhatsApp configuration is required");
        }

        String provider = normalizeProvider(request.provider());
        if (!MetaWhatsAppMessagingProvider.ID.equals(provider)) {
            throw new IllegalArgumentException("Only META_WHATSAPP_CLOUD is supported");
        }

        String providerPhoneNumberId = normalizeProviderPhoneNumberId(request.providerPhoneNumberId());
        String credentialRef = normalizeCredentialRef(request.credentialRef());

        PhoneNumber phone = phones.findByIdAndBusinessId(request.phoneRecordId(), businessId)
                .orElseThrow(() -> new NotFoundException("Phone number not found"));

        phone.setWhatsappProvider(MetaWhatsAppMessagingProvider.ID);
        phone.setWhatsappExternalId(providerPhoneNumberId);

        // Configuration never activates delivery. Activation remains a separate,
        // explicit step after credentials and Meta onboarding are certified.
        phone.setWhatsappEnabled(false);
        phone.setWhatsappCertifiedAt(null);
        phones.save(phone);

        MetaWhatsAppTenantConfig config = configs.findById(businessId)
                .orElseGet(MetaWhatsAppTenantConfig::new);
        config.setBusinessId(businessId);
        config.setCredentialRef(credentialRef);
        config.setEnabled(false);
        configs.save(config);

        return new MetaWhatsAppTenantConfigurationResponse(
                request.phoneRecordId(),
                MetaWhatsAppMessagingProvider.ID,
                providerPhoneNumberId,
                credentialRef,
                false);
    }

    private static String normalizeProvider(String value) {
        if (value == null) return "";
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeProviderPhoneNumberId(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!normalized.matches("^[0-9]{5,30}$")) {
            throw new IllegalArgumentException("Invalid Meta phone_number_id");
        }
        return normalized;
    }

    private static String normalizeCredentialRef(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("^[A-Z0-9_]{2,80}$")) {
            throw new IllegalArgumentException("Invalid Meta credential_ref");
        }
        return normalized;
    }
}
