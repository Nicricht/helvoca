package cl.helvoca.messaging.meta;

import cl.helvoca.messaging.outbound.MetaWhatsAppMessagingProvider;
import cl.helvoca.messaging.outbound.OutboundMessagingProperties;
import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.phone.PhoneNumberRepository;
import cl.helvoca.security.TenantProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class MetaWhatsAppTenantHealthService {
    private final MetaWhatsAppTenantConfigRepository configs;
    private final PhoneNumberRepository phones;
    private final TenantProvider tenantProvider;
    private final MetaWhatsAppCredentialAvailability credentialAvailability;
    private final MetaWhatsAppProperties metaProperties;
    private final OutboundMessagingProperties outboundProperties;

    public MetaWhatsAppTenantHealthService(
            MetaWhatsAppTenantConfigRepository configs,
            PhoneNumberRepository phones,
            TenantProvider tenantProvider,
            MetaWhatsAppCredentialAvailability credentialAvailability,
            MetaWhatsAppProperties metaProperties,
            OutboundMessagingProperties outboundProperties) {
        this.configs = configs;
        this.phones = phones;
        this.tenantProvider = tenantProvider;
        this.credentialAvailability = credentialAvailability;
        this.metaProperties = metaProperties;
        this.outboundProperties = outboundProperties;
    }

    @Transactional(readOnly = true)
    public MetaWhatsAppTenantHealthResponse health() {
        UUID businessId = tenantProvider.requireBusinessId();
        MetaWhatsAppTenantConfig config = configs.findById(businessId).orElse(null);
        List<PhoneNumber> metaPhones = phones.findAllByBusinessIdOrderByCreatedAtDesc(businessId)
                .stream()
                .filter(phone -> MetaWhatsAppMessagingProvider.ID.equals(phone.getWhatsappProvider()))
                .filter(phone -> phone.getWhatsappExternalId() != null
                        && !phone.getWhatsappExternalId().isBlank())
                .toList();

        if (config == null && metaPhones.isEmpty()) {
            return response("NOT_CONFIGURED", false, false, false, false,
                    false, false, false, false, null);
        }

        String credentialRef = config == null ? null : cleanCredentialRef(config.getCredentialRef());
        boolean complete = metaPhones.size() == 1 && credentialRef != null;
        if (!complete) {
            PhoneNumber phone = metaPhones.size() == 1 ? metaPhones.get(0) : null;
            return response("INCOMPLETE", false, false,
                    phone != null && phone.isActive(),
                    false,
                    webhookSecurityReady(),
                    metaProperties.isEnabled(),
                    outboundProperties.isDeliveryEnabled(),
                    phone != null && phone.getWhatsappCertifiedAt() != null,
                    phone);
        }

        PhoneNumber phone = metaPhones.get(0);
        boolean tenantEnabled = config.isEnabled() && phone.isWhatsappEnabled();
        boolean phoneActive = phone.isActive();
        boolean credentialAvailable = credentialAvailability.isAvailable(credentialRef);
        boolean webhookReady = webhookSecurityReady();
        boolean integrationEnabled = metaProperties.isEnabled();
        boolean outboundEnabled = outboundProperties.isDeliveryEnabled();
        boolean certified = phone.getWhatsappCertifiedAt() != null;

        String state;
        if (!tenantEnabled) state = "TENANT_DISABLED";
        else if (!phoneActive) state = "PHONE_INACTIVE";
        else if (!credentialAvailable) state = "CREDENTIAL_UNAVAILABLE";
        else if (!webhookReady) state = "WEBHOOK_SECURITY_NOT_READY";
        else if (!certified) state = "UNCERTIFIED";
        else if (!integrationEnabled) state = "GLOBAL_INTEGRATION_DISABLED";
        else if (!outboundEnabled) state = "OUTBOUND_DELIVERY_DISABLED";
        else state = "READY";

        return new MetaWhatsAppTenantHealthResponse(
                state,
                true,
                tenantEnabled,
                phoneActive,
                credentialAvailable,
                webhookReady,
                integrationEnabled,
                outboundEnabled,
                certified,
                phone.getWhatsappProvider(),
                phone.getId(),
                phone.getWhatsappExternalId(),
                phone.getWhatsappCertifiedAt());
    }

    private boolean webhookSecurityReady() {
        return metaProperties.isWebhookValidationEnabled()
                && metaProperties.hasAppSecret()
                && metaProperties.hasVerifyToken();
    }

    private static String cleanCredentialRef(String value) {
        if (value == null) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return normalized.matches("^[A-Z0-9_]{2,80}$") ? normalized : null;
    }

    private MetaWhatsAppTenantHealthResponse response(
            String state,
            boolean configured,
            boolean tenantEnabled,
            boolean phoneActive,
            boolean credentialAvailable,
            boolean webhookSecurityReady,
            boolean integrationEnabled,
            boolean outboundDeliveryEnabled,
            boolean certified,
            PhoneNumber phone) {
        return new MetaWhatsAppTenantHealthResponse(
                state,
                configured,
                tenantEnabled,
                phoneActive,
                credentialAvailable,
                webhookSecurityReady,
                integrationEnabled,
                outboundDeliveryEnabled,
                certified,
                phone == null ? null : phone.getWhatsappProvider(),
                phone == null ? null : phone.getId(),
                phone == null ? null : phone.getWhatsappExternalId(),
                phone == null ? null : phone.getWhatsappCertifiedAt());
    }
}
