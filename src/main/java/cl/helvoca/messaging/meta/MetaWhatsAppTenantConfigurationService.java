package cl.helvoca.messaging.meta;

import cl.helvoca.audit.AuditService;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.messaging.outbound.MetaWhatsAppMessagingProvider;
import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.phone.PhoneNumberRepository;
import cl.helvoca.security.TenantProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class MetaWhatsAppTenantConfigurationService {
    private final MetaWhatsAppTenantConfigRepository configs;
    private final PhoneNumberRepository phones;
    private final TenantProvider tenantProvider;
    private final MetaWhatsAppCredentialAvailability credentialAvailability;
    private final MetaWhatsAppCertificationReadinessService certificationReadinessService;
    private final MetaWhatsAppDeploymentReadinessService deploymentReadinessService;
    private final AuditService auditService;

    @Autowired
    public MetaWhatsAppTenantConfigurationService(
            MetaWhatsAppTenantConfigRepository configs,
            PhoneNumberRepository phones,
            TenantProvider tenantProvider,
            MetaWhatsAppCredentialAvailability credentialAvailability,
            MetaWhatsAppCertificationReadinessService certificationReadinessService,
            MetaWhatsAppDeploymentReadinessService deploymentReadinessService,
            AuditService auditService) {
        this.configs = configs;
        this.phones = phones;
        this.tenantProvider = tenantProvider;
        this.credentialAvailability = credentialAvailability;
        this.certificationReadinessService = certificationReadinessService;
        this.deploymentReadinessService = deploymentReadinessService;
        this.auditService = auditService;
    }

    MetaWhatsAppTenantConfigurationService(
            MetaWhatsAppTenantConfigRepository configs,
            PhoneNumberRepository phones,
            TenantProvider tenantProvider,
            MetaWhatsAppCredentialAvailability credentialAvailability,
            MetaWhatsAppCertificationReadinessService certificationReadinessService,
            MetaWhatsAppDeploymentReadinessService deploymentReadinessService) {
        this(
                configs,
                phones,
                tenantProvider,
                credentialAvailability,
                certificationReadinessService,
                deploymentReadinessService,
                null);
    }

    MetaWhatsAppTenantConfigurationService(
            MetaWhatsAppTenantConfigRepository configs,
            PhoneNumberRepository phones,
            TenantProvider tenantProvider) {
        this(configs, phones, tenantProvider, credentialRef -> false, null, null);
    }

    MetaWhatsAppTenantConfigurationService(
            MetaWhatsAppTenantConfigRepository configs,
            PhoneNumberRepository phones,
            TenantProvider tenantProvider,
            MetaWhatsAppCredentialAvailability credentialAvailability) {
        this(configs, phones, tenantProvider, credentialAvailability, null, null);
    }

    @Transactional(readOnly = true)
    public MetaWhatsAppTenantStatusResponse status() {
        UUID businessId = tenantProvider.requireBusinessId();
        MetaWhatsAppTenantConfig config = configs.findById(businessId).orElse(null);

        List<PhoneNumber> metaPhones = phones.findAllByBusinessIdOrderByCreatedAtDesc(businessId)
                .stream()
                .filter(phone -> MetaWhatsAppMessagingProvider.ID.equals(phone.getWhatsappProvider()))
                .filter(phone -> phone.getWhatsappExternalId() != null
                        && !phone.getWhatsappExternalId().isBlank())
                .toList();

        if (config == null && metaPhones.isEmpty()) {
            return MetaWhatsAppTenantStatusResponse.notConfigured();
        }

        boolean credentialReferenceConfigured = config != null
                && config.getCredentialRef() != null
                && !config.getCredentialRef().isBlank();

        if (metaPhones.size() != 1 || !credentialReferenceConfigured) {
            MetaWhatsAppTenantStatusResponse.PhoneView phoneView = metaPhones.size() == 1
                    ? toPhoneView(metaPhones.get(0))
                    : null;
            return MetaWhatsAppTenantStatusResponse.incomplete(
                    phoneView,
                    config == null ? null : config.getWabaId(),
                    credentialReferenceConfigured);
        }

        PhoneNumber phone = metaPhones.get(0);
        boolean enabled = config.isEnabled() && phone.isWhatsappEnabled();
        return MetaWhatsAppTenantStatusResponse.configured(
                toPhoneView(phone),
                config.getWabaId(),
                enabled);
    }

    @Transactional
    public MetaWhatsAppTenantStatusResponse deactivate() {
        UUID businessId = tenantProvider.requireBusinessId();
        MetaWhatsAppTenantConfig config = configs.findById(businessId).orElse(null);

        List<PhoneNumber> metaPhones = phones.findAllByBusinessIdOrderByCreatedAtDesc(businessId)
                .stream()
                .filter(phone -> MetaWhatsAppMessagingProvider.ID.equals(phone.getWhatsappProvider()))
                .toList();

        if (config == null && metaPhones.isEmpty()) {
            return MetaWhatsAppTenantStatusResponse.notConfigured();
        }

        boolean wasEnabled = (config != null && config.isEnabled())
                || metaPhones.stream().anyMatch(PhoneNumber::isWhatsappEnabled);

        if (config != null && config.isEnabled()) {
            config.setEnabled(false);
            configs.save(config);
        }

        for (PhoneNumber phone : metaPhones) {
            if (phone.isWhatsappEnabled()) {
                phone.setWhatsappEnabled(false);
                phones.save(phone);
            }
        }

        if (wasEnabled && auditService != null) {
            auditService.humanSuccess(
                    businessId,
                    "META_WHATSAPP_DEACTIVATE",
                    "META_WHATSAPP_CONFIG",
                    businessId,
                    auditSnapshot(true),
                    auditSnapshot(false));
        }

        boolean credentialReferenceConfigured = config != null
                && config.getCredentialRef() != null
                && !config.getCredentialRef().isBlank();

        List<PhoneNumber> configuredMetaPhones = metaPhones.stream()
                .filter(phone -> phone.getWhatsappExternalId() != null
                        && !phone.getWhatsappExternalId().isBlank())
                .toList();

        if (configuredMetaPhones.size() != 1 || !credentialReferenceConfigured) {
            MetaWhatsAppTenantStatusResponse.PhoneView phoneView = configuredMetaPhones.size() == 1
                    ? toPhoneView(configuredMetaPhones.get(0))
                    : null;
            return MetaWhatsAppTenantStatusResponse.incomplete(
                    phoneView,
                    config == null ? null : config.getWabaId(),
                    credentialReferenceConfigured);
        }

        return MetaWhatsAppTenantStatusResponse.configured(
                toPhoneView(configuredMetaPhones.get(0)),
                config == null ? null : config.getWabaId(),
                false);
    }

    @Transactional
    public MetaWhatsAppTenantStatusResponse activate() {
        UUID businessId = tenantProvider.requireBusinessId();
        MetaWhatsAppTenantConfig config = configs.findById(businessId)
                .orElseThrow(() -> new IllegalStateException("Meta WhatsApp is not configured"));

        String credentialRef = normalizeCredentialRef(config.getCredentialRef());
        if (!credentialAvailability.isAvailable(credentialRef)) {
            throw new IllegalStateException("Meta WhatsApp credential is unavailable");
        }

        List<PhoneNumber> metaPhones = phones.findAllByBusinessIdOrderByCreatedAtDesc(businessId)
                .stream()
                .filter(phone -> MetaWhatsAppMessagingProvider.ID.equals(phone.getWhatsappProvider()))
                .filter(phone -> phone.getWhatsappExternalId() != null
                        && !phone.getWhatsappExternalId().isBlank())
                .toList();

        if (metaPhones.size() != 1) {
            throw new IllegalStateException("Meta WhatsApp requires exactly one configured phone");
        }

        PhoneNumber phone = metaPhones.get(0);
        if (!phone.isActive()) {
            throw new IllegalStateException("Meta WhatsApp phone is inactive");
        }

        normalizeProviderPhoneNumberId(phone.getWhatsappExternalId());

        if (certificationReadinessService == null || deploymentReadinessService == null) {
            throw new IllegalStateException("Meta WhatsApp activation readiness is unavailable");
        }

        MetaWhatsAppCertificationReadinessResponse certification = certificationReadinessService.readiness();
        if (!certification.ready() || !certification.alreadyCertified()) {
            throw new IllegalStateException("Meta WhatsApp certification is incomplete");
        }

        MetaWhatsAppDeploymentReadinessResponse deployment = deploymentReadinessService.readiness();
        if (!"READY_FOR_TENANT_STAGING".equals(deployment.state())
                || !deployment.readyForTenantStaging()) {
            throw new IllegalStateException("Meta WhatsApp deployment staging is not ready");
        }

        config.setEnabled(true);
        phone.setWhatsappEnabled(true);
        configs.save(config);
        phones.save(phone);
        if (auditService != null) {
            auditService.humanSuccess(
                    businessId,
                    "META_WHATSAPP_ACTIVATE",
                    "META_WHATSAPP_CONFIG",
                    businessId,
                    auditSnapshot(false),
                    auditSnapshot(true));
        }

        // This only arms the tenant. Global Meta delivery remains controlled by
        // app.meta.whatsapp.enabled and the outbound delivery gate.
        return MetaWhatsAppTenantStatusResponse.configured(
                toPhoneView(phone),
                config.getWabaId(),
                true);
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
        String wabaId = normalizeOptionalWabaId(request.wabaId());

        PhoneNumber phone = phones.findByIdAndBusinessId(request.phoneRecordId(), businessId)
                .orElseThrow(() -> new NotFoundException("Phone number not found"));

        MetaWhatsAppTenantConfig existingConfig = configs.findById(businessId).orElse(null);
        boolean wasConfigured = existingConfig != null
                || MetaWhatsAppMessagingProvider.ID.equals(phone.getWhatsappProvider());
        boolean wasPhoneWhatsappEnabled = phone.isWhatsappEnabled();
        boolean wasEnabled = (existingConfig != null && existingConfig.isEnabled())
                || (MetaWhatsAppMessagingProvider.ID.equals(phone.getWhatsappProvider())
                        && wasPhoneWhatsappEnabled);
        boolean wasCertified = phone.getWhatsappCertifiedAt() != null;

        phone.setWhatsappProvider(MetaWhatsAppMessagingProvider.ID);
        phone.setWhatsappExternalId(providerPhoneNumberId);

        // Configuration never activates delivery. Activation remains a separate,
        // explicit step after credentials and Meta onboarding are certified.
        phone.setWhatsappEnabled(false);
        phone.setWhatsappCertifiedAt(null);
        phones.save(phone);

        MetaWhatsAppTenantConfig config = existingConfig == null
                ? new MetaWhatsAppTenantConfig()
                : existingConfig;
        config.setBusinessId(businessId);
        config.setCredentialRef(credentialRef);
        config.setWabaId(wabaId);
        config.setEnabled(false);
        configs.save(config);

        if (auditService != null) {
            auditService.humanSuccess(
                    businessId,
                    "META_WHATSAPP_CONFIG_REPLACE",
                    "META_WHATSAPP_CONFIG",
                    businessId,
                    auditConfigurationSnapshot(wasConfigured, wasEnabled),
                    auditConfigurationSnapshot(true, false));

            if (wasPhoneWhatsappEnabled) {
                auditService.humanSuccess(
                        businessId,
                        "WHATSAPP_SENDER_DISABLED",
                        "WHATSAPP_SENDER",
                        businessId,
                        Map.of(
                                "whatsappEnabled", true,
                                "certified", wasCertified),
                        Map.of(
                                "whatsappEnabled", false,
                                "certified", false));
            }

            if (wasCertified) {
                auditService.humanSuccess(
                        businessId,
                        "WHATSAPP_CERTIFICATION_CLEARED",
                        "WHATSAPP_SENDER",
                        businessId,
                        Map.of(
                                "whatsappEnabled", wasPhoneWhatsappEnabled,
                                "certified", true),
                        Map.of(
                                "whatsappEnabled", false,
                                "certified", false));
            }
        }

        return new MetaWhatsAppTenantConfigurationResponse(
                request.phoneRecordId(),
                MetaWhatsAppMessagingProvider.ID,
                providerPhoneNumberId,
                wabaId,
                credentialRef,
                false);
    }

    private static Map<String, Object> auditSnapshot(boolean enabled) {
        return Map.of(
                "provider", MetaWhatsAppMessagingProvider.ID,
                "enabled", enabled);
    }

    private static Map<String, Object> auditConfigurationSnapshot(boolean configured, boolean enabled) {
        return Map.of(
                "provider", MetaWhatsAppMessagingProvider.ID,
                "configured", configured,
                "enabled", enabled);
    }

    private static MetaWhatsAppTenantStatusResponse.PhoneView toPhoneView(PhoneNumber phone) {
        return new MetaWhatsAppTenantStatusResponse.PhoneView(
                phone.getId(),
                phone.getWhatsappProvider(),
                phone.getWhatsappExternalId(),
                phone.getPhoneNumber(),
                phone.getWhatsappCertifiedAt());
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

    private static String normalizeOptionalWabaId(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (!normalized.matches("^[0-9]{5,30}$")) {
            throw new IllegalArgumentException("Invalid Meta waba_id");
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
