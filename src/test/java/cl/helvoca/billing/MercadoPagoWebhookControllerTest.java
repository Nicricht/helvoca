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

            assertEquals(200, response.getStatusCode().value());
            verify(billing).reconcileAuthorizedPayment("invoice-1");
            validator.verify(() -> WebhookSignatureValidator.validate(
                    eq("signed"), eq("req-2"), eq("invoice-1"), eq("test-secret"), any(Duration.class)));
        }
    }

    @Test
    void validPreapprovalWebhookAcknowledges200AndReconcilesOnce() {
        MercadoPagoProperties properties = new MercadoPagoProperties();
        properties.setEnabled(true);
        properties.setAccessToken("test-token");
        properties.setWebhookSecret("test-secret");
        BillingSubscriptionService billing = mock(BillingSubscriptionService.class);
        MercadoPagoWebhookController controller = new MercadoPagoWebhookController(properties, billing);

        try (var validator = mockStatic(WebhookSignatureValidator.class)) {
            validator.when(() -> WebhookSignatureValidator.validate(
                    eq("signed"), eq("req-3"), eq("pre-3"), eq("test-secret"), any(Duration.class)))
                    .thenAnswer(invocation -> null);

            var response = controller.webhook(
                    "signed", "req-3", "subscription_preapproval", null, "pre-3", null);

            assertEquals(200, response.getStatusCode().value());
            verify(billing, times(1)).reconcileSubscription("pre-3");
            verifyNoMoreInteractions(billing);
        }
    }

    @Test
    void authenticatedUnrecognizedTopicAcknowledges200WithoutFinancialEffects() {
        MercadoPagoProperties properties = new MercadoPagoProperties();
        properties.setEnabled(true);
        properties.setAccessToken("test-token");
        properties.setWebhookSecret("test-secret");
        BillingSubscriptionService billing = mock(BillingSubscriptionService.class);
        MercadoPagoWebhookController controller = new MercadoPagoWebhookController(properties, billing);

        try (var validator = mockStatic(WebhookSignatureValidator.class)) {
            validator.when(() -> WebhookSignatureValidator.validate(
                    eq("signed"), eq("req-4"), eq("payment-4"), eq("test-secret"), any(Duration.class)))
                    .thenAnswer(invocation -> null);

            var response = controller.webhook("signed", "req-4", "payment",
                    null, "payment-4", null);

            assertEquals(200, response.getStatusCode().value());
            verifyNoInteractions(billing);
            validator.verify(() -> WebhookSignatureValidator.validate(
                    eq("signed"), eq("req-4"), eq("payment-4"), eq("test-secret"), any(Duration.class)));
        }
    }

    @Test
    void reconciliationFailureStillReturns500ToPreserveProviderRetry() {
        MercadoPagoProperties properties = new MercadoPagoProperties();
        properties.setEnabled(true);
        properties.setAccessToken("test-token");
        properties.setWebhookSecret("test-secret");
        BillingSubscriptionService billing = mock(BillingSubscriptionService.class);
        doThrow(new IllegalStateException("synthetic DB failure"))
                .when(billing).reconcileAuthorizedPayment("invoice-fail");
        MercadoPagoWebhookController controller = new MercadoPagoWebhookController(properties, billing);

        try (var validator = mockStatic(WebhookSignatureValidator.class)) {
            validator.when(() -> WebhookSignatureValidator.validate(
                    eq("signed"), eq("req-fail"), eq("invoice-fail"), eq("test-secret"), any(Duration.class)))
                    .thenAnswer(invocation -> null);

            var response = controller.webhook(
                    "signed", "req-fail", "subscription_authorized_payment", null, "invoice-fail", null);

            assertEquals(500, response.getStatusCode().value());
            verify(billing).reconcileAuthorizedPayment("invoice-fail");
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
