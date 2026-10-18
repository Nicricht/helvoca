package cl.helvoca.billing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MercadoPagoPropertiesTest {

    private static MercadoPagoProperties configured() {
        MercadoPagoProperties properties = new MercadoPagoProperties();
        properties.setEnabled(true);
        properties.setAccessToken("synthetic-test-token");
        properties.setBackUrl("https://example.test/billing/return");
        properties.setWebhookSecret("synthetic-test-webhook-signing-secret");
        return properties;
    }

    @Test
    void completeSaasCheckoutRequiresBothWebhookVerificationAndSecureReturnUrl() {
        MercadoPagoProperties properties = configured();
        assertTrue(properties.webhookConfigured());
        assertTrue(properties.checkoutConfigured());
    }

    @Test
    void checkoutRejectsMissingOrWhitespaceWebhookSigningSecret() {
        MercadoPagoProperties properties = configured();
        properties.setWebhookSecret("");
        assertFalse(properties.webhookConfigured());
        assertFalse(properties.checkoutConfigured());

        properties.setWebhookSecret("   ");
        assertFalse(properties.webhookConfigured());
        assertFalse(properties.checkoutConfigured());
    }

    @Test
    void checkoutRejectsHttpOrMissingReturnUrlEvenWithWebhookReady() {
        MercadoPagoProperties properties = configured();
        properties.setBackUrl("http://example.test/return");
        assertTrue(properties.webhookConfigured());
        assertFalse(properties.checkoutConfigured());

        properties.setBackUrl("");
        assertFalse(properties.checkoutConfigured());
    }

    @Test
    void checkoutRejectsMissingProviderTokenEvenWithWebhookSecret() {
        MercadoPagoProperties properties = configured();
        properties.setAccessToken("");
        assertFalse(properties.webhookConfigured());
        assertFalse(properties.checkoutConfigured());
    }

    @Test
    void disabledCheckoutAndWebhookRemainDisabledEvenWhenCredentialsPresent() {
        MercadoPagoProperties properties = configured();
        properties.setEnabled(false);
        assertFalse(properties.webhookConfigured());
        assertFalse(properties.checkoutConfigured());
    }

    @Test
    void webhookCanBeConfiguredWithoutCheckoutReturnUrl() {
        MercadoPagoProperties properties = configured();
        properties.setBackUrl("");
        assertTrue(properties.webhookConfigured());
        assertFalse(properties.checkoutConfigured());
    }
}
