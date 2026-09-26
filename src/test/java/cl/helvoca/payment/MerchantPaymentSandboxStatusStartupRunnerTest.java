package cl.helvoca.payment;

import cl.helvoca.security.TenantDatabaseContext;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MerchantPaymentSandboxStatusStartupRunnerTest {

    @Test
    void checksProviderStatusWithoutCreatingAnotherOrder() {
        UUID businessId = UUID.randomUUID();
        PaymentProviderRegistry providers = mock(PaymentProviderRegistry.class);
        PaymentProviderAdapter provider = mock(PaymentProviderAdapter.class);

        when(providers.byCode(businessId, "mercadopago")).thenReturn(Optional.of(provider));
        when(provider.providerCode()).thenReturn("mercadopago");
        when(provider.getStatus(any())).thenReturn(new PaymentProviderAdapter.StatusResult(
                BusinessPayment.Status.SUCCEEDED,
                java.util.Map.of("remoteStatus", "processed")));

        MerchantPaymentSandboxStatusStartupRunner runner =
                new MerchantPaymentSandboxStatusStartupRunner(
                        false,
                        "",
                        "",
                        mock(TenantDatabaseContext.class),
                        providers);

        var result = runner.check(businessId, "ORDTST123");

        assertEquals("mercadopago", result.provider());
        assertEquals("ORDTST123", result.externalId());
        assertEquals(BusinessPayment.Status.SUCCEEDED, result.status());
        verify(provider).getStatus(argThat(command ->
                businessId.equals(command.businessId())
                        && "ORDTST123".equals(command.externalId())));
        verify(provider, never()).create(any());
    }

    @Test
    void failsClosedWhenProviderIsUnavailable() {
        UUID businessId = UUID.randomUUID();
        PaymentProviderRegistry providers = mock(PaymentProviderRegistry.class);
        when(providers.byCode(businessId, "mercadopago")).thenReturn(Optional.empty());

        MerchantPaymentSandboxStatusStartupRunner runner =
                new MerchantPaymentSandboxStatusStartupRunner(
                        false,
                        "",
                        "",
                        mock(TenantDatabaseContext.class),
                        providers);

        assertThrows(
                IllegalStateException.class,
                () -> runner.check(businessId, "ORDTST123"));
    }
}
