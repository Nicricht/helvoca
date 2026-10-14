package cl.helvoca.billing;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * Exercises the official Mercado Pago HMAC-SHA256 validator with locally
 * generated signed requests, without mocking signature validation or
 * contacting a provider. This does not certify provider webhook delivery.
 */
class MercadoPagoWebhookSignatureRoundTripTest {
    static final String SECRET = "c5-only-local-fake-webhook-key";

    private MercadoPagoWebhookController controller(BillingSubscriptionService billing) {
        MercadoPagoProperties settings = new MercadoPagoProperties();
        settings.setEnabled(true);
        settings.setAccessToken("local-test-placeholder");
        settings.setWebhookSecret(SECRET);
        return new MercadoPagoWebhookController(settings, billing);
    }

    static String sign(String dataId, String requestId) {
        try {
            String timestamp = Long.toString(Instant.now().toEpochMilli());
            String manifest = "id:" + dataId.toLowerCase(java.util.Locale.ROOT)
                    + ";request-id:" + requestId + ";ts:" + timestamp + ";";
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "ts=" + timestamp + ",v1=" + HexFormat.of().formatHex(
                    mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new AssertionError("Cannot generate local HMAC test fixture", e);
        }
    }

    @Test
    void signedPreapprovalEventCallsOnlyRemoteReconciliation() {
        BillingSubscriptionService billing = mock(BillingSubscriptionService.class);
        var response = controller(billing).webhook(
                sign("pre-c5", "req-c5"), "req-c5", "subscription_preapproval",
                null, "pre-c5", "{\"data\":{\"id\":\"pre-c5\"}}");
        assertEquals(204, response.getStatusCode().value());
        verify(billing).reconcileSubscription("pre-c5");
        verifyNoMoreInteractions(billing);
    }

    @Test
    void signedApprovedPaymentNoticeIsNotPaymentProofByItself() {
        BillingSubscriptionService billing = mock(BillingSubscriptionService.class);
        String body = "{\"type\":\"subscription_authorized_payment\","
                + "\"data\":{\"id\":\"invoice-c5\"},\"live_mode\":false}";
        var response = controller(billing).webhook(
                sign("invoice-c5", "req-invoice"), "req-invoice",
                null, null, null, body);
        assertEquals(204, response.getStatusCode().value());
        // The service retrieves the invoice from the provider and checks its
        // payment status. No change of plan is possible from JSON alone.
        verify(billing).reconcileAuthorizedPayment("invoice-c5");
        verifyNoMoreInteractions(billing);
    }

    @Test
    void changingSignedInvoiceIdFailsAuthenticationBeforeBilling() {
        BillingSubscriptionService billing = mock(BillingSubscriptionService.class);
        var response = controller(billing).webhook(
                sign("invoice-good", "req-mismatch"), "req-mismatch",
                "subscription_authorized_payment", null, "invoice-other", null);
        assertEquals(401, response.getStatusCode().value());
        verifyNoInteractions(billing);
    }

    @Test
    void changingSignedRequestIdFailsAuthenticationBeforeBilling() {
        BillingSubscriptionService billing = mock(BillingSubscriptionService.class);
        var response = controller(billing).webhook(
                sign("invoice-good", "req-good"), "req-tampered",
                "subscription_authorized_payment", null, "invoice-good", null);
        assertEquals(401, response.getStatusCode().value());
        verifyNoInteractions(billing);
    }

    @Test
    void malformedSignatureNeverReconciles() {
        BillingSubscriptionService billing = mock(BillingSubscriptionService.class);
        var response = controller(billing).webhook(
                "ts=1,v1=invalid", "req-invalid", "subscription_authorized_payment",
                null, "invoice-c5", null);
        assertEquals(401, response.getStatusCode().value());
        verifyNoInteractions(billing);
    }

    @Test
    void signedUnknownTopicIsIgnored() {
        BillingSubscriptionService billing = mock(BillingSubscriptionService.class);
        var response = controller(billing).webhook(
                sign("pay-c5", "req-topic"), "req-topic", "payment",
                null, "pay-c5", null);
        assertEquals(204, response.getStatusCode().value());
        verifyNoInteractions(billing);
    }

    @Test
    void signedReplayReachesIdempotentServiceNotDirectMutation() {
        BillingSubscriptionService billing = mock(BillingSubscriptionService.class);
        var controller = controller(billing);
        String header = sign("invoice-replay", "req-replay");
        for (int i = 0; i < 2; i++) {
            var response = controller.webhook(header, "req-replay",
                    "subscription_authorized_payment", null, "invoice-replay", null);
            assertEquals(204, response.getStatusCode().value());
        }
        // Invoice-level deduplication is implemented and covered separately
        // by BillingSubscriptionServiceTest. Controller only dispatches.
        verify(billing, times(2)).reconcileAuthorizedPayment("invoice-replay");
        verifyNoMoreInteractions(billing);
    }
}
