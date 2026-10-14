package cl.helvoca.billing;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MercadoPagoSaasSandboxProviderIT {

    @Test
    void createsAndReadsRealSandboxSubscriptionWithTestCredentialsOnly() {
        Assumptions.assumeTrue(
                Boolean.parseBoolean(System.getenv("HELVOCA_SAAS_BILLING_SANDBOX_LIVE_TEST")),
                "External Mercado Pago sandbox certification is disabled");

        assertEquals("true", System.getenv("HELVOCA_C5_PROVENANCE_GATE_PASSED"),
                "C5 read-only authenticated TEST seller identity preflight must pass before provider writes");
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
        SubscriptionPaymentGateway.Checkout checkout =
                gateway.createCheckout(businessId, payerEmail, plan);

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

        // Avoid putting remotely actionable checkout links or identifiers into CI logs.
        System.out.println("C5_SANDBOX_SUBSCRIPTION_CREATED_AND_READBACK_VERIFIED");
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            fail(name + " is required for external sandbox certification");
        }
        return value.trim();
    }
}
