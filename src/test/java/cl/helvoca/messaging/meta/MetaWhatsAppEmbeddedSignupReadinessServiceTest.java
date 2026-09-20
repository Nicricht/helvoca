package cl.helvoca.messaging.meta;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MetaWhatsAppEmbeddedSignupReadinessServiceTest {

    @Test
    void blocksByDefaultWithoutEmbeddedSignupConfiguration() {
        MetaWhatsAppProperties meta = new MetaWhatsAppProperties();

        var response = new MetaWhatsAppEmbeddedSignupReadinessService(meta).readiness();

        assertEquals("BLOCKED", response.state());
        assertFalse(response.readyForEmbeddedSignup());
        assertFalse(response.embeddedSignupEnabled());
        assertFalse(response.appIdConfigured());
        assertFalse(response.configIdConfigured());
        assertFalse(response.businessIdConfigured());
        assertFalse(response.systemUserIdConfigured());
        assertFalse(response.adminSystemUserAccessTokenConfigured());
        assertFalse(response.appSecretConfigured());
        assertFalse(response.verifyTokenConfigured());
        assertTrue(response.webhookValidationEnabled());
        assertTrue(response.blockers().stream()
                .anyMatch(item -> "EMBEDDED_SIGNUP_DISABLED".equals(item.code())));
        assertTrue(response.blockers().stream()
                .anyMatch(item -> "APP_ID_MISSING".equals(item.code())));
        assertTrue(response.blockers().stream()
                .anyMatch(item -> "CONFIG_ID_MISSING".equals(item.code())));
        assertTrue(response.blockers().stream()
                .anyMatch(item -> "BUSINESS_ID_MISSING".equals(item.code())));
        assertTrue(response.blockers().stream()
                .anyMatch(item -> "SYSTEM_USER_ID_MISSING".equals(item.code())));
        assertTrue(response.blockers().stream()
                .anyMatch(item -> "SYSTEM_USER_ACCESS_TOKEN_MISSING".equals(item.code())));
        assertTrue(response.blockers().stream()
                .anyMatch(item -> "ADMIN_SYSTEM_USER_ACCESS_TOKEN_MISSING".equals(item.code())));
    }

    @Test
    void reportsReadyWithoutExposingConfiguredValues() {
        MetaWhatsAppProperties meta = new MetaWhatsAppProperties();
        meta.setEmbeddedSignupEnabled(true);
        meta.setEmbeddedSignupAppId("123456789");
        meta.setEmbeddedSignupConfigId("987654321");
        meta.setEmbeddedSignupBusinessId("112233445566778");
        meta.setEmbeddedSignupSystemUserId("998877665544332");
        meta.setEmbeddedSignupSystemUserAccessToken("system-user-secret");
        meta.setEmbeddedSignupAdminSystemUserAccessToken("admin-system-user-secret");
        meta.setAppSecret("super-secret");
        meta.setVerifyToken("verify-secret");
        meta.setWebhookValidationEnabled(true);

        var response = new MetaWhatsAppEmbeddedSignupReadinessService(meta).readiness();

        assertEquals("READY_FOR_EMBEDDED_SIGNUP", response.state());
        assertTrue(response.readyForEmbeddedSignup());
        assertTrue(response.embeddedSignupEnabled());
        assertTrue(response.appIdConfigured());
        assertTrue(response.configIdConfigured());
        assertTrue(response.businessIdConfigured());
        assertTrue(response.systemUserIdConfigured());
        assertTrue(response.adminSystemUserAccessTokenConfigured());
        assertTrue(response.appSecretConfigured());
        assertTrue(response.verifyTokenConfigured());
        assertTrue(response.webhookValidationEnabled());
        assertTrue(response.blockers().isEmpty());
        assertFalse(response.toString().contains("123456789"));
        assertFalse(response.toString().contains("987654321"));
        assertFalse(response.toString().contains("112233445566778"));
        assertFalse(response.toString().contains("998877665544332"));
        assertFalse(response.toString().contains("super-secret"));
        assertFalse(response.toString().contains("system-user-secret"));
        assertFalse(response.toString().contains("admin-system-user-secret"));
        assertFalse(response.toString().contains("verify-secret"));
    }

    @Test
    void blocksWhenWebhookValidationIsDisabled() {
        MetaWhatsAppProperties meta = new MetaWhatsAppProperties();
        meta.setEmbeddedSignupEnabled(true);
        meta.setEmbeddedSignupAppId("123");
        meta.setEmbeddedSignupConfigId("456");
        meta.setEmbeddedSignupBusinessId("789");
        meta.setEmbeddedSignupSystemUserId("101112");
        meta.setEmbeddedSignupSystemUserAccessToken("system-user-secret");
        meta.setEmbeddedSignupAdminSystemUserAccessToken("admin-system-user-secret");
        meta.setAppSecret("secret");
        meta.setVerifyToken("verify");
        meta.setWebhookValidationEnabled(false);

        var response = new MetaWhatsAppEmbeddedSignupReadinessService(meta).readiness();

        assertFalse(response.readyForEmbeddedSignup());
        assertTrue(response.blockers().stream()
                .anyMatch(item -> "WEBHOOK_VALIDATION_DISABLED".equals(item.code())));
    }
}
