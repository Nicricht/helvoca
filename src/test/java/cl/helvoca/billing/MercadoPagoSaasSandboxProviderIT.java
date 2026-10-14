package cl.helvoca.billing;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import com.mercadopago.exceptions.MPApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


import java.net.URI;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MercadoPagoSaasSandboxProviderIT {

    // Safe diagnostics contract: never publish provider response text, buyer,
    // tokens, IDs, signatures or a checkout URL. All classifications are fixed labels.
    @Test
    void rejectsUnstructuredOrOversizedProviderError() {
        assertEquals("UNCLASSIFIED", safeProviderErrorCategory(null));
        assertEquals("UNCLASSIFIED", safeProviderErrorCategory("not a JSON object"));
        assertEquals("UNCLASSIFIED", safeProviderErrorCategory("x".repeat(16_385)));
        assertEquals("UNCLASSIFIED", safeProviderErrorCategory("{\"unexpected\":\"secret@example.com\"}"));
    }

    @Test
    void classifiesOnlySupportedErrorHintFieldsWithoutLeakingProviderData() {
        String privateValue = "sensitive-buyer@example.com";
        String response = "{\"message\":\"Invalid payer_email for " + privateValue + "\"}";
        String category = safeProviderErrorCategory(response);
        assertEquals("PAYER_EMAIL_HINT", category);
        assertFalse(category.contains(privateValue));
        assertEquals("CURRENCY_HINT", safeProviderErrorCategory(
                "{\"cause\":[{\"code\":\"invalid_currency_id\"}]}"));
        assertEquals("RECURRENCE_HINT", safeProviderErrorCategory(
                "{\"message\":\"auto_recurring is unsupported\"}"));
        assertEquals("BACK_URL_HINT", safeProviderErrorCategory(
                "{\"message\":\"invalid back_url\"}"));
    }

    // Never print the raw API response: it can contain email, IDs and other PII.
    // These fixed labels are hints, not proof that a specific field caused HTTP 400.
    static String safeProviderErrorCategory(String response) {
        if (response == null || response.isBlank() || response.length() > 16_384) {
            return "UNCLASSIFIED";
        }
        try {
            JsonNode payload = new ObjectMapper().readTree(response);
            if (payload == null || !payload.isObject()) return "UNCLASSIFIED";
            StringBuilder hints = new StringBuilder();
            for (String key : new String[]{"message", "error"}) {
                JsonNode field = payload.path(key);
                if (field.isTextual()) hints.append(' ').append(field.asText());
            }
            JsonNode causes = payload.path("cause");
            if (causes.isArray()) {
                for (int i = 0; i < Math.min(8, causes.size()); i++) {
                    JsonNode cause = causes.path(i);
                    for (String key : new String[]{"code", "description"}) {
                        JsonNode field = cause.path(key);
                        if (field.isTextual()) hints.append(' ').append(field.asText());
                    }
                }
            }
            String details = hints.toString().toLowerCase(Locale.ROOT);
            if (details.contains("payer_email") || details.contains("payer email")) return "PAYER_EMAIL_HINT";
            if (details.contains("currency_id") || details.contains("currency id")) return "CURRENCY_HINT";
            if (details.contains("auto_recurring") || details.contains("frequency_type")) return "RECURRENCE_HINT";
            if (details.contains("back_url") || details.contains("back url")) return "BACK_URL_HINT";
            return "UNCLASSIFIED";
        } catch (Exception ignored) {
            return "UNCLASSIFIED";
        }
    }

    @Test
    void createsAndReadsRealSandboxSubscriptionWithTestCredentialsOnly() {
        Assumptions.assumeTrue(
                Boolean.parseBoolean(System.getenv("HELVOCA_SAAS_BILLING_SANDBOX_LIVE_TEST")),
                "External Mercado Pago sandbox certification is disabled");

        String token = requiredEnv("MERCADOPAGO_TEST_ACCESS_TOKEN");
        assertTrue(token.startsWith("APP_USR"),
                "Mercado Pago TEST access token must start with APP_USR");

        String payerEmail = requiredEnv("MERCADOPAGO_TEST_PAYER_EMAIL")
                .trim()
                .toLowerCase(Locale.ROOT);
        assertTrue(payerEmail.endsWith("@testuser.com"),
                "Sandbox payer must be a Mercado Pago testuser.com account");

        UUID businessId = UUID.randomUUID();
        PaymentPlan plan = new PaymentPlan("BASIC", "Emprende", 24_990, false);

        MercadoPagoProperties properties = new MercadoPagoProperties();
        properties.setEnabled(true);
        properties.setAccessToken(token);
        properties.setBackUrl("https://recepvoz.cl/pricing.html");

        MercadoPagoSubscriptionGateway gateway = new MercadoPagoSubscriptionGateway(properties);
        SubscriptionPaymentGateway.Checkout checkout;
        try {
            checkout = gateway.createCheckout(businessId, payerEmail, plan);
        } catch (IllegalStateException failedCheckout) {
            if (failedCheckout.getCause() instanceof MPApiException apiError
                    && apiError.getApiResponse() != null) {
                // Public GitHub logs: status and fixed category ONLY, never raw
                // provider content, IDs, buyer email, signature or access token.
                if (apiError.getApiResponse().getStatusCode() == 400) {
                    System.out.println("C5_SANDBOX_CREATE_REJECTED_HTTP_400");
                    System.out.println("C5_SANDBOX_CREATE_REJECTION_HINT="
                            + safeProviderErrorCategory(apiError.getApiResponse().getContent()));
                } else {
                    System.out.println("C5_SANDBOX_CREATE_REJECTED_OTHER_HTTP_STATUS");
                }
            }
            throw failedCheckout;
        }

        assertNotNull(checkout);
        assertNotNull(checkout.subscriptionId());
        assertFalse(checkout.subscriptionId().isBlank());
        assertEquals("helvoca:" + businessId + ":BASIC", checkout.externalReference());

        URI checkoutUri = URI.create(checkout.checkoutUrl());
        assertEquals("https", checkoutUri.getScheme());
        assertNotNull(checkoutUri.getHost());

        SubscriptionPaymentGateway.RemoteSubscription remote =
                gateway.getSubscription(checkout.subscriptionId());

        assertEquals(checkout.subscriptionId(), remote.id());
        assertEquals(checkout.externalReference(), remote.externalReference());
        assertNotNull(remote.status());
        assertFalse(remote.status().isBlank());

        System.out.println("MERCADOPAGO_SANDBOX_CERTIFIED subscriptionId=" + remote.id());
        System.out.println("MERCADOPAGO_SANDBOX_STATUS=" + remote.status());
        System.out.println("MERCADOPAGO_SANDBOX_CHECKOUT_URL=" + checkout.checkoutUrl());
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            fail(name + " is required for external sandbox certification");
        }
        return value.trim();
    }
}
