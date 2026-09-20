package cl.helvoca.messaging.meta;

import cl.helvoca.jobs.PersistentJobProperties;
import cl.helvoca.messaging.outbound.MetaWhatsAppMessagingProvider;
import cl.helvoca.messaging.outbound.OutboundMessagingProperties;
import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.phone.PhoneNumberRepository;
import cl.helvoca.security.TenantProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class MetaWhatsAppCertificationReadinessService {
    private final MetaWhatsAppTenantConfigRepository configs;
    private final PhoneNumberRepository phones;
    private final TenantProvider tenantProvider;
    private final MetaWhatsAppCredentialAvailability credentialAvailability;
    private final MetaWhatsAppProperties metaProperties;
    private final OutboundMessagingProperties outboundProperties;
    private final PersistentJobProperties jobProperties;

    public MetaWhatsAppCertificationReadinessService(
            MetaWhatsAppTenantConfigRepository configs,
            PhoneNumberRepository phones,
            TenantProvider tenantProvider,
            MetaWhatsAppCredentialAvailability credentialAvailability,
            MetaWhatsAppProperties metaProperties,
            OutboundMessagingProperties outboundProperties,
            PersistentJobProperties jobProperties) {
        this.configs = configs;
        this.phones = phones;
        this.tenantProvider = tenantProvider;
        this.credentialAvailability = credentialAvailability;
        this.metaProperties = metaProperties;
        this.outboundProperties = outboundProperties;
        this.jobProperties = jobProperties;
    }

    @Transactional(readOnly = true)
    public MetaWhatsAppCertificationReadinessResponse readiness() {
        UUID businessId = tenantProvider.requireBusinessId();
        List<MetaWhatsAppCertificationReadinessResponse.Blocker> blockers = new ArrayList<>();

        MetaWhatsAppTenantConfig config = configs.findById(businessId).orElse(null);
        List<PhoneNumber> metaPhones = phones.findAllByBusinessIdOrderByCreatedAtDesc(businessId)
                .stream()
                .filter(phone -> MetaWhatsAppMessagingProvider.ID.equals(phone.getWhatsappProvider()))
                .filter(phone -> phone.getWhatsappExternalId() != null
                        && !phone.getWhatsappExternalId().isBlank())
                .toList();

        if (config == null) {
            blockers.add(blocker("TENANT_CONFIG_MISSING",
                    "Meta WhatsApp tenant configuration is missing."));
        }

        if (metaPhones.isEmpty()) {
            blockers.add(blocker("META_PHONE_MISSING",
                    "No Meta WhatsApp phone_number_id is configured."));
        } else if (metaPhones.size() > 1) {
            blockers.add(blocker("META_PHONE_AMBIGUOUS",
                    "More than one Meta WhatsApp phone is configured."));
        }

        PhoneNumber phone = metaPhones.size() == 1 ? metaPhones.getFirst() : null;
        if (phone != null) {
            if (!phone.isActive()) {
                blockers.add(blocker("PHONE_INACTIVE",
                        "The configured phone is inactive."));
            }
            if (!validPhoneNumberId(phone.getWhatsappExternalId())) {
                blockers.add(blocker("PHONE_NUMBER_ID_INVALID",
                        "The Meta phone_number_id is invalid."));
            }
        }

        String credentialRef = config == null ? null : normalizeCredentialRef(config.getCredentialRef());
        if (config != null && credentialRef == null) {
            blockers.add(blocker("CREDENTIAL_REFERENCE_INVALID",
                    "The Meta credential reference is missing or invalid."));
        } else if (credentialRef != null && !credentialAvailability.isAvailable(credentialRef)) {
            blockers.add(blocker("CREDENTIAL_UNAVAILABLE",
                    "The tenant Meta credential is not available in the deployment."));
        }

        if (!metaProperties.isWebhookValidationEnabled()
                || !metaProperties.hasAppSecret()
                || !metaProperties.hasVerifyToken()) {
            blockers.add(blocker("WEBHOOK_SECURITY_NOT_READY",
                    "Meta webhook signature and verification security are not fully configured."));
        }

        // Certification readiness is intentionally evaluated with every real-traffic
        // gate closed. This stage must never send a message or process queued jobs.
        if (config != null && config.isEnabled()) {
            blockers.add(blocker("TENANT_MUST_BE_DISABLED",
                    "Disable the tenant Meta channel before certification preflight."));
        }
        if (phone != null && phone.isWhatsappEnabled()) {
            blockers.add(blocker("PHONE_WHATSAPP_MUST_BE_DISABLED",
                    "Disable WhatsApp on the tenant phone before certification preflight."));
        }
        if (metaProperties.isEnabled()) {
            blockers.add(blocker("GLOBAL_META_MUST_BE_DISABLED",
                    "Global Meta WhatsApp delivery must remain disabled during certification preflight."));
        }
        if (outboundProperties.isDeliveryEnabled()) {
            blockers.add(blocker("OUTBOUND_DELIVERY_MUST_BE_DISABLED",
                    "Real outbound delivery must remain disabled during certification preflight."));
        }
        if (!"NONE".equalsIgnoreCase(clean(outboundProperties.getProvider()))) {
            blockers.add(blocker("OUTBOUND_PROVIDER_MUST_BE_NONE",
                    "The global outbound provider must remain NONE during certification preflight."));
        }
        if (jobProperties.isEnabled()) {
            blockers.add(blocker("JOBS_MUST_BE_DISABLED",
                    "Persistent outbound jobs must remain disabled during certification preflight."));
        }

        boolean alreadyCertified = phone != null && phone.getWhatsappCertifiedAt() != null;
        if (alreadyCertified) {
            return new MetaWhatsAppCertificationReadinessResponse(
                    blockers.isEmpty() ? "ALREADY_CERTIFIED" : "BLOCKED",
                    blockers.isEmpty(),
                    true,
                    blockers);
        }

        return new MetaWhatsAppCertificationReadinessResponse(
                blockers.isEmpty() ? "READY_FOR_PILOT_CERTIFICATION" : "BLOCKED",
                blockers.isEmpty(),
                false,
                blockers);
    }

    private static MetaWhatsAppCertificationReadinessResponse.Blocker blocker(
            String code, String message) {
        return new MetaWhatsAppCertificationReadinessResponse.Blocker(code, message);
    }

    private static String normalizeCredentialRef(String value) {
        if (value == null) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return normalized.matches("^[A-Z0-9_]{2,80}$") ? normalized : null;
    }

    private static boolean validPhoneNumberId(String value) {
        return value != null && value.trim().matches("^[0-9]{5,30}$");
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
