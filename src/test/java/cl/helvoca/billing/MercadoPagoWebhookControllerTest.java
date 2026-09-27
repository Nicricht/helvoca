package cl.helvoca.billing;

import com.mercadopago.webhook.WebhookSignatureValidator;
import org.junit.jupiter.api.Test;

import java.time.Duration;

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
    void validAuthorizedPaymentWebhookIsVerifiedAndRouted() {
        MercadoPagoProperties properties = new MercadoPagoProperties();
        properties.setEnabled(true);
        properties.setAccessToken("test-token");
        properties.setWebhookSecret("test-secret");
        BillingSubscriptionService billing = mock(BillingSubscriptionService.class);
        MercadoPagoWebhookController controller = new MercadoPagoWebhookController(properties, billing);

        try (var validator = mockStatic(WebhookSignatureValidator.class)) {
            validator.when(() -> WebhookSignatureValidator.validate(
                            eq("signed"), eq("req-2"), eq("invoice-1"), eq("test-secret"), any(Duration.class)))
                    .thenAnswer(invocation -> null);

            var response = controller.webhook(
                    "signed",
                    "req-2",
                    "subscription_authorized_payment",
                    null,
                    "invoice-1",
                    null);

            assertEquals(204, response.getStatusCode().value());
            verify(billing).reconcileAuthorizedPayment("invoice-1");
            validator.verify(() -> WebhookSignatureValidator.validate(
                    eq("signed"), eq("req-2"), eq("invoice-1"), eq("test-secret"), any(Duration.class)));
        }
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
