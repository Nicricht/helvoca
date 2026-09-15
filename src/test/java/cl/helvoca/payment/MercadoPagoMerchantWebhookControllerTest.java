package cl.helvoca.payment;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class MercadoPagoMerchantWebhookControllerTest {
    @Test
    void rejectsLiveModeBeforeAnyReconciliation() {
        UUID webhookKey = UUID.randomUUID();
        PaymentProviderConfigService configs = mock(PaymentProviderConfigService.class);
        PaymentProviderCredentialResolver credentials = mock(PaymentProviderCredentialResolver.class);
        PaymentWebhookService webhooks = mock(PaymentWebhookService.class);
        PaymentProviderConfig config = sandboxConfig(UUID.randomUUID(), webhookKey);

        when(configs.byWebhookKey(webhookKey)).thenReturn(config);
        when(credentials.resolve("TENANT_TEST")).thenReturn(Optional.of(
                new PaymentProviderCredentialResolver.Credentials("token", "secret", true)));

        MercadoPagoMerchantWebhookController controller =
                new MercadoPagoMerchantWebhookController(configs, credentials, webhooks);
        String body = """
                {"id":"evt-live","live_mode":true,"type":"order", "data":{"id":"ORDTST123"}}
                """;
        ResponseEntity<Void> response = controller.webhook(
                webhookKey, null, "req-1", "order", "ORDTST123", body);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verifyNoInteractions(webhooks);
    }

    @Test
    void ignoresNonOrderTopicsWithoutReconciliation() {
        UUID webhookKey = UUID.randomUUID();
        PaymentProviderConfigService configs = mock(PaymentProviderConfigService.class);
        PaymentProviderCredentialResolver credentials = mock(PaymentProviderCredentialResolver.class);
        PaymentWebhookService webhooks = mock(PaymentWebhookService.class);
        PaymentProviderConfig config = sandboxConfig(UUID.randomUUID(), webhookKey);

        when(configs.byWebhookKey(webhookKey)).thenReturn(config);
        when(credentials.resolve("TENANT_TEST")).thenReturn(Optional.of(
                new PaymentProviderCredentialResolver.Credentials("token", "secret", true)));

        MercadoPagoMerchantWebhookController controller =
                new MercadoPagoMerchantWebhookController(configs, credentials, webhooks);
        ResponseEntity<Void> response = controller.webhook(
                webhookKey, null, "req-2", "payment", "123", "{}");

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verifyNoInteractions(webhooks);
    }

    @Test
    void unknownWebhookKeyIsNotFound() {
        UUID webhookKey = UUID.randomUUID();
        PaymentProviderConfigService configs = mock(PaymentProviderConfigService.class);
        PaymentProviderCredentialResolver credentials = mock(PaymentProviderCredentialResolver.class);
        PaymentWebhookService webhooks = mock(PaymentWebhookService.class);
        when(configs.byWebhookKey(webhookKey)).thenReturn(null);

        MercadoPagoMerchantWebhookController controller =
                new MercadoPagoMerchantWebhookController(configs, credentials, webhooks);
        ResponseEntity<Void> response = controller.webhook(
                webhookKey, null, null, "order", "ORDTST123", "{}");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verifyNoInteractions(credentials, webhooks);
    }

    private static PaymentProviderConfig sandboxConfig(UUID businessId, UUID webhookKey) {
        PaymentProviderConfig config = new PaymentProviderConfig();
        config.setBusinessId(businessId);
        config.setProvider("mercadopago");
        config.setMode(PaymentProviderConfig.Mode.SANDBOX);
        config.setEnabled(true);
        config.setCredentialRef("TENANT_TEST");
        config.setWebhookKey(webhookKey);
        return config;
    }
}
