package cl.helvoca.payment;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MercadoPagoSandboxPaymentProviderAdapterTest {
    @Test
    void createsSandboxOrderAndMapsCreatedToRequiresAction() {
        UUID businessId = UUID.randomUUID();
        UUID paymentOperationId = UUID.randomUUID();
        PaymentProviderConfigService configs = mock(PaymentProviderConfigService.class);
        PaymentProviderCredentialResolver credentials = mock(PaymentProviderCredentialResolver.class);
        MercadoPagoOrderClient client = mock(MercadoPagoOrderClient.class);

        PaymentProviderConfig config = config(businessId);
        when(configs.requireSandboxMercadoPago(businessId)).thenReturn(config);
        when(credentials.resolve("TENANT_TEST")).thenReturn(Optional.of(
                new PaymentProviderCredentialResolver.Credentials("token", "secret", true)));
        when(client.create("token", "payment-operation:" + paymentOperationId,
                paymentOperationId, new BigDecimal("1500")))
                .thenReturn(new MercadoPagoOrderClient.RemoteOrder(
                        "ORDTST123",
                        "https://www.mercadopago.cl/checkout/test",
                        "created",
                        "created",
                        paymentOperationId.toString(),
                        "CLP",
                        null));

        MercadoPagoSandboxPaymentProviderAdapter adapter =
                new MercadoPagoSandboxPaymentProviderAdapter(configs, credentials, client);
        PaymentProviderAdapter.CreateResult result = adapter.create(new PaymentProviderAdapter.CreateCommand(
                businessId,
                paymentOperationId,
                UUID.randomUUID(),
                new BigDecimal("1500"),
                "CLP",
                "payment-operation:" + paymentOperationId,
                null,
                Map.of()));

        assertEquals("ORDTST123", result.externalId());
        assertEquals(BusinessPayment.Status.REQUIRES_ACTION, result.status());
        assertNotNull(result.checkoutUrl());
        assertEquals(true, result.metadata().get("sandbox"));
    }

    @Test
    void rejectsUnsupportedCurrencyBeforeCallingProvider() {
        UUID businessId = UUID.randomUUID();
        PaymentProviderConfigService configs = mock(PaymentProviderConfigService.class);
        PaymentProviderCredentialResolver credentials = mock(PaymentProviderCredentialResolver.class);
        MercadoPagoOrderClient client = mock(MercadoPagoOrderClient.class);
        PaymentProviderConfig config = config(businessId);
        when(configs.requireSandboxMercadoPago(businessId)).thenReturn(config);
        when(credentials.resolve("TENANT_TEST")).thenReturn(Optional.of(
                new PaymentProviderCredentialResolver.Credentials("token", "secret", true)));

        MercadoPagoSandboxPaymentProviderAdapter adapter =
                new MercadoPagoSandboxPaymentProviderAdapter(configs, credentials, client);

        assertThrows(IllegalArgumentException.class, () -> adapter.create(
                new PaymentProviderAdapter.CreateCommand(
                        businessId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        new BigDecimal("10"),
                        "USD",
                        "idempotency",
                        null,
                        Map.of())));
        verifyNoInteractions(client);
    }

    @Test
    void statusMappingFailsClosedForPartialRefund() {
        assertEquals(BusinessPayment.Status.SUCCEEDED,
                MercadoPagoSandboxPaymentProviderAdapter.mapStatus("processed", "accredited"));
        assertEquals(BusinessPayment.Status.REFUNDED,
                MercadoPagoSandboxPaymentProviderAdapter.mapStatus("processed", "refunded"));
        assertEquals(BusinessPayment.Status.FAILED,
                MercadoPagoSandboxPaymentProviderAdapter.mapStatus("processed", "partially_refunded"));
        assertEquals(BusinessPayment.Status.PENDING,
                MercadoPagoSandboxPaymentProviderAdapter.mapStatus("processing", "in_process"));
    }

    private static PaymentProviderConfig config(UUID businessId) {
        PaymentProviderConfig config = new PaymentProviderConfig();
        config.setBusinessId(businessId);
        config.setProvider("mercadopago");
        config.setMode(PaymentProviderConfig.Mode.SANDBOX);
        config.setCredentialRef("TENANT_TEST");
        config.setEnabled(true);
        return config;
    }
}
