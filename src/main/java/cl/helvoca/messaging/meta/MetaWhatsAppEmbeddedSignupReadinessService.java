package cl.helvoca.messaging.meta;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class MetaWhatsAppEmbeddedSignupReadinessService {
    private final MetaWhatsAppProperties metaProperties;

    public MetaWhatsAppEmbeddedSignupReadinessService(MetaWhatsAppProperties metaProperties) {
        this.metaProperties = metaProperties;
    }

    public MetaWhatsAppEmbeddedSignupReadinessResponse readiness() {
        List<MetaWhatsAppEmbeddedSignupReadinessResponse.Blocker> blockers = new ArrayList<>();

        boolean enabled = metaProperties.isEmbeddedSignupEnabled();
        boolean appIdConfigured = metaProperties.hasEmbeddedSignupAppId();
        boolean configIdConfigured = metaProperties.hasEmbeddedSignupConfigId();
        boolean businessIdConfigured = metaProperties.hasEmbeddedSignupBusinessId();
        boolean systemUserIdConfigured = metaProperties.hasEmbeddedSignupSystemUserId();
        boolean systemUserAccessTokenConfigured = metaProperties.hasEmbeddedSignupSystemUserAccessToken();
        boolean adminSystemUserAccessTokenConfigured = metaProperties.hasEmbeddedSignupAdminSystemUserAccessToken();
        boolean appSecretConfigured = metaProperties.hasAppSecret();
        boolean verifyTokenConfigured = metaProperties.hasVerifyToken();
        boolean webhookValidationEnabled = metaProperties.isWebhookValidationEnabled();

        if (!enabled) {
            blockers.add(blocker(
                    "EMBEDDED_SIGNUP_DISABLED",
                    "Meta Embedded Signup is disabled."));
        }
        if (!appIdConfigured) {
            blockers.add(blocker(
                    "APP_ID_MISSING",
                    "Meta Embedded Signup app_id is not configured."));
        }
        if (!configIdConfigured) {
            blockers.add(blocker(
                    "CONFIG_ID_MISSING",
                    "Meta Embedded Signup configuration_id is not configured."));
        }
        if (!businessIdConfigured) {
            blockers.add(blocker(
                    "BUSINESS_ID_MISSING",
                    "Meta provider business_id is not configured."));
        }
        if (!systemUserIdConfigured) {
            blockers.add(blocker(
                    "SYSTEM_USER_ID_MISSING",
                    "Meta Embedded Signup system user id is not configured."));
        }
        if (!systemUserAccessTokenConfigured) {
            blockers.add(blocker(
                    "SYSTEM_USER_ACCESS_TOKEN_MISSING",
                    "Meta Embedded Signup system user access token is not configured."));
        }
        if (!adminSystemUserAccessTokenConfigured) {
            blockers.add(blocker(
                    "ADMIN_SYSTEM_USER_ACCESS_TOKEN_MISSING",
                    "Meta Embedded Signup admin system user access token is not configured."));
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
        if (!webhookValidationEnabled) {
            blockers.add(blocker(
                    "WEBHOOK_VALIDATION_DISABLED",
                    "Meta webhook validation must remain enabled."));
        }

        boolean ready = blockers.isEmpty();
        return new MetaWhatsAppEmbeddedSignupReadinessResponse(
                ready ? "READY_FOR_EMBEDDED_SIGNUP" : "BLOCKED",
                ready,
                enabled,
                appIdConfigured,
                configIdConfigured,
                businessIdConfigured,
                systemUserIdConfigured,
                adminSystemUserAccessTokenConfigured,
                appSecretConfigured,
                verifyTokenConfigured,
                webhookValidationEnabled,
                blockers);
    }

    private static MetaWhatsAppEmbeddedSignupReadinessResponse.Blocker blocker(
            String code,
            String message) {
        return new MetaWhatsAppEmbeddedSignupReadinessResponse.Blocker(code, message);
    }
}
