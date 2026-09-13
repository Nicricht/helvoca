package cl.helvoca.billing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class MercadoPagoWebhookControllerTest {

    @Test
    void invalidSignatureIsRejectedBeforeReconciliation() {
        MercadoPagoProperties properties = new MercadoPagoProperties();
        properties.setEnabled(true);
        properties.setAccessToken("test-token");
        properties.setWebhookSecret("test-secret");
        BillingSubscriptionService billing = mock(BillingSubscriptionService.class);

        var response = new MercadoPagoWebhookController(properties, billing)
                .webhook(
                        "ts=1,v1=invalid",
                        "req-1",
                        "subscription_preapproval",
                        null,
                        "pre-1",
                        null);

        assertEquals(401, response.getStatusCode().value());
        verifyNoInteractions(billing);
    }

    @Test
    void disabledWebhookReturnsNotFoundWithoutReconciliation() {
        MercadoPagoProperties properties = new MercadoPagoProperties();
        properties.setEnabled(false);
        BillingSubscriptionService billing = mock(BillingSubscriptionService.class);

        var response = new MercadoPagoWebhookController(properties, billing)
                .webhook(null, null, null, null, null, null);

        assertEquals(404, response.getStatusCode().value());
        verifyNoInteractions(billing);
    }
}
