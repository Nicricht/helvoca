package cl.helvoca.messaging.meta;

import cl.helvoca.jobs.PersistentJobProperties;
import cl.helvoca.messaging.outbound.OutboundMessagingProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class MetaWhatsAppDeploymentReadinessService {
    private final MetaWhatsAppProperties metaProperties;
    private final OutboundMessagingProperties outboundProperties;
    private final PersistentJobProperties jobProperties;

    public MetaWhatsAppDeploymentReadinessService(
            MetaWhatsAppProperties metaProperties,
            OutboundMessagingProperties outboundProperties,
            PersistentJobProperties jobProperties) {
        this.metaProperties = metaProperties;
        this.outboundProperties = outboundProperties;
        this.jobProperties = jobProperties;
    }

    public MetaWhatsAppDeploymentReadinessResponse readiness() {
        List<MetaWhatsAppDeploymentReadinessResponse.Blocker> blockers = new ArrayList<>();

        boolean webhookValidationEnabled = metaProperties.isWebhookValidationEnabled();
        boolean appSecretConfigured = metaProperties.hasAppSecret();
        boolean verifyTokenConfigured = metaProperties.hasVerifyToken();
        boolean globalMetaEnabled = metaProperties.isEnabled();
        boolean outboundDeliveryEnabled = outboundProperties.isDeliveryEnabled();
        String outboundProvider = normalizeProvider(outboundProperties.getProvider());
        boolean jobsEnabled = jobProperties.isEnabled();

        if (!webhookValidationEnabled) {
            blockers.add(blocker(
                    "WEBHOOK_VALIDATION_DISABLED",
                    "Meta webhook validation must remain enabled."));
        }
        if (!appSecretConfigured) {
            blockers.add(blocker(
                    "APP_SECRET_MISSING",
                    "Meta app secret is not configured."));
        }
        if (!verifyTokenConfigured) {
            blockers.add(blocker(
                    "VERIFY_TOKEN_MISSING",
                    "Meta webhook verify token is not configured."));
        }

        // Tenant staging must happen before any real traffic gate is opened.
        if (globalMetaEnabled) {
            blockers.add(blocker(
                    "GLOBAL_META_MUST_BE_DISABLED",
                    "Global Meta WhatsApp delivery must remain disabled during tenant staging."));
        }
        if (outboundDeliveryEnabled) {
            blockers.add(blocker(
                    "OUTBOUND_DELIVERY_MUST_BE_DISABLED",
                    "Real outbound delivery must remain disabled during tenant staging."));
        }
        if (!"NONE".equals(outboundProvider)) {
            blockers.add(blocker(
                    "OUTBOUND_PROVIDER_MUST_BE_NONE",
                    "The global outbound provider must remain NONE during tenant staging."));
        }
        if (jobsEnabled) {
            blockers.add(blocker(
                    "JOBS_MUST_BE_DISABLED",
                    "Persistent outbound jobs must remain disabled during tenant staging."));
        }

        boolean ready = blockers.isEmpty();
        return new MetaWhatsAppDeploymentReadinessResponse(
                ready ? "READY_FOR_TENANT_STAGING" : "BLOCKED",
                ready,
                webhookValidationEnabled,
                appSecretConfigured,
                verifyTokenConfigured,
                globalMetaEnabled,
                outboundDeliveryEnabled,
                outboundProvider,
                jobsEnabled,
                blockers);
    }

    private static MetaWhatsAppDeploymentReadinessResponse.Blocker blocker(
            String code,
            String message) {
        return new MetaWhatsAppDeploymentReadinessResponse.Blocker(code, message);
    }

    private static String normalizeProvider(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return normalized.isBlank() ? "NONE" : normalized;
    }
}
