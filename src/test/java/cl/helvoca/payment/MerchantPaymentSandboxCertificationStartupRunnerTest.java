package cl.helvoca.payment;

import cl.helvoca.security.TenantDatabaseContext;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MerchantPaymentSandboxCertificationStartupRunnerTest {

    @Test
    void createsIsolatedSandboxCheckoutWithoutBusinessOrderMutation() {
        UUID businessId = UUID.randomUUID();
        PaymentProviderRegistry providers = mock(PaymentProviderRegistry.class);
        PaymentProviderAdapter provider = mock(PaymentProviderAdapter.class);

        when(providers.byCode(businessId, "mercadopago")).thenReturn(Optional.of(provider));
        when(provider.providerCode()).thenReturn("mercadopago");
        when(provider.create(any())).thenReturn(new PaymentProviderAdapter.CreateResult(
                "ORDTST123",
                "https://www.mercadopago.cl/checkout/v1/redirect?pref_id=test",
                BusinessPayment.Status.REQUIRES_ACTION,
                Map.of("sandbox", true)));

        MerchantPaymentSandboxCertificationStartupRunner runner =
                new MerchantPaymentSandboxCertificationStartupRunner(
                        false,
                        "",
                        "1000",
                        mock(TenantDatabaseContext.class),
                        providers);

        var result = runner.certify(businessId, new BigDecimal("1000"));

        assertEquals("mercadopago", result.provider());
        assertEquals("ORDTST123", result.externalId());
        assertEquals(BusinessPayment.Status.REQUIRES_ACTION, result.status());
        assertTrue(result.checkoutUrl().startsWith("https://"));

        verify(provider).create(argThat(command ->
                businessId.equals(command.businessId())
                        && command.amount().compareTo(new BigDecimal("1000")) == 0
                        && "CLP".equals(command.currency())
                        && command.metadata().containsKey("certification")
                        && command.idempotencyKey().startsWith("helvoca-mp-sandbox-cert-")));
    }

    @Test
    void rejectsMissingProvider() {
        UUID businessId = UUID.randomUUID();
        PaymentProviderRegistry providers = mock(PaymentProviderRegistry.class);
        when(providers.byCode(businessId, "mercadopago")).thenReturn(Optional.empty());

        MerchantPaymentSandboxCertificationStartupRunner runner =
                new MerchantPaymentSandboxCertificationStartupRunner(
                        false,
                        "",
                        "1000",
                        mock(TenantDatabaseContext.class),
                        providers);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> runner.certify(businessId, new BigDecimal("1000")));

        assertTrue(error.getMessage().contains("not available"));
    }

    @Test
    void rejectsProviderResponseWithoutHttpsCheckout() {
        UUID businessId = UUID.randomUUID();
        PaymentProviderRegistry providers = mock(PaymentProviderRegistry.class);
        PaymentProviderAdapter provider = mock(PaymentProviderAdapter.class);

        when(providers.byCode(businessId, "mercadopago")).thenReturn(Optional.of(provider));
        when(provider.create(any())).thenReturn(new PaymentProviderAdapter.CreateResult(
                "ORDTST123",
                "http://unsafe.example.test/checkout",
                BusinessPayment.Status.REQUIRES_ACTION,
                Map.of()));

        MerchantPaymentSandboxCertificationStartupRunner runner =
                new MerchantPaymentSandboxCertificationStartupRunner(
                        false,
                        "",
                        "1000",
                        mock(TenantDatabaseContext.class),
                        providers);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> runner.certify(businessId, new BigDecimal("1000")));

        assertTrue(error.getMessage().contains("invalid checkout URL"));
    }
}
