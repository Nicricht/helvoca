package cl.helvoca.billing;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import com.mercadopago.exceptions.MPApiException;

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
        } catch (IllegalStateException error) {
            Throwable cause = error.getCause();
            if (cause instanceof MPApiException apiError && apiError.getApiResponse() != null) {
                System.err.println("MERCADOPAGO_API_ERROR_STATUS=" + apiError.getStatusCode());
                System.err.println("MERCADOPAGO_API_ERROR_BODY=" + apiError.getApiResponse().getContent());
            }
            throw error;
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
