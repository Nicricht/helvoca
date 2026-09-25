package cl.helvoca.payment;

import cl.helvoca.security.TenantDatabaseContext;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MerchantPaymentSandboxBootstrapStartupRunnerTest {

    @Test
    void provisionsDisabledWebhookConfigWhenSecretsAreMissing() {
        UUID businessId = UUID.randomUUID();
        UUID webhookKey = UUID.randomUUID();
        PaymentProviderConfigRepository configs = mock(PaymentProviderConfigRepository.class);
        PaymentProviderCredentialResolver credentials = mock(PaymentProviderCredentialResolver.class);

        when(credentials.resolve("RECEPVOZ_MP_SANDBOX")).thenReturn(Optional.empty());
        when(configs.findById(businessId)).thenReturn(Optional.empty());
        when(configs.saveAndFlush(any())).thenAnswer(invocation -> {
            PaymentProviderConfig value = invocation.getArgument(0);
            value.setWebhookKey(webhookKey);
            return value;
        });

        MerchantPaymentSandboxBootstrapStartupRunner runner =
                new MerchantPaymentSandboxBootstrapStartupRunner(
                        false,
                        "",
                        "RECEPVOZ_MP_SANDBOX",
                        mock(TenantDatabaseContext.class),
                        mock(PlatformTransactionManager.class),
                        configs,
                        credentials);

        var result = runner.bootstrap(businessId);

        assertTrue(result.changed());
        assertFalse(result.enabled());
        assertFalse(result.credentialsConfigured());
        assertEquals(
                "/webhooks/v1/payments/mercadopago/" + webhookKey,
                result.webhookPath());
        verify(configs).saveAndFlush(argThat(value ->
                businessId.equals(value.getBusinessId())
                        && !value.isEnabled()
                        && value.getMode() == PaymentProviderConfig.Mode.SANDBOX
                        && "mercadopago".equals(value.getProvider())
                        && "RECEPVOZ_MP_SANDBOX".equals(value.getCredentialRef())));
    }

    @Test
    void createsSandboxProviderOnlyAfterSecretsResolve() {
        UUID businessId = UUID.randomUUID();
        PaymentProviderConfigRepository configs = mock(PaymentProviderConfigRepository.class);
        PaymentProviderCredentialResolver credentials = mock(PaymentProviderCredentialResolver.class);

        when(credentials.resolve("RECEPVOZ_MP_SANDBOX"))
                .thenReturn(Optional.of(new PaymentProviderCredentialResolver.Credentials(
                        "sandbox-token",
                        "sandbox-webhook-secret",
                        true)));
        when(configs.findById(businessId)).thenReturn(Optional.empty());
        when(configs.saveAndFlush(any())).thenAnswer(invocation -> {
            PaymentProviderConfig value = invocation.getArgument(0);
            if (value.getWebhookKey() == null) {
                value.setWebhookKey(UUID.randomUUID());
            }
            return value;
        });

        MerchantPaymentSandboxBootstrapStartupRunner runner =
                new MerchantPaymentSandboxBootstrapStartupRunner(
                        false,
                        "",
                        "RECEPVOZ_MP_SANDBOX",
                        mock(TenantDatabaseContext.class),
                        mock(PlatformTransactionManager.class),
                        configs,
                        credentials);

        var result = runner.bootstrap(businessId);

        assertTrue(result.changed());
        assertEquals("mercadopago", result.provider());
        assertEquals(PaymentProviderConfig.Mode.SANDBOX, result.mode());
        assertTrue(result.enabled());
        assertTrue(result.credentialsConfigured());
        assertNotNull(result.webhookPath());
        verify(configs).saveAndFlush(argThat(value ->
                businessId.equals(value.getBusinessId())
                        && value.isEnabled()
                        && value.getMode() == PaymentProviderConfig.Mode.SANDBOX
                        && "mercadopago".equals(value.getProvider())
                        && "RECEPVOZ_MP_SANDBOX".equals(value.getCredentialRef())));
    }

    @Test
    void bootstrapIsIdempotentForAlreadyReadyTenant() {
        UUID businessId = UUID.randomUUID();
        UUID webhookKey = UUID.randomUUID();
        PaymentProviderConfigRepository configs = mock(PaymentProviderConfigRepository.class);
        PaymentProviderCredentialResolver credentials = mock(PaymentProviderCredentialResolver.class);

        PaymentProviderConfig config = new PaymentProviderConfig();
        config.setBusinessId(businessId);
        config.setProvider("mercadopago");
        config.setMode(PaymentProviderConfig.Mode.SANDBOX);
        config.setCredentialRef("RECEPVOZ_MP_SANDBOX");
        config.setEnabled(true);
        config.setWebhookKey(webhookKey);

        when(credentials.resolve("RECEPVOZ_MP_SANDBOX"))
                .thenReturn(Optional.of(new PaymentProviderCredentialResolver.Credentials(
                        "sandbox-token",
                        "sandbox-webhook-secret",
                        true)));
        when(configs.findById(businessId)).thenReturn(Optional.of(config));

        MerchantPaymentSandboxBootstrapStartupRunner runner =
                new MerchantPaymentSandboxBootstrapStartupRunner(
                        false,
                        "",
                        "RECEPVOZ_MP_SANDBOX",
                        mock(TenantDatabaseContext.class),
                        mock(PlatformTransactionManager.class),
                        configs,
                        credentials);

        var result = runner.bootstrap(businessId);

        assertFalse(result.changed());
        assertTrue(result.enabled());
        assertTrue(result.credentialsConfigured());
        assertEquals(
                "/webhooks/v1/payments/mercadopago/" + webhookKey,
                result.webhookPath());
        verify(configs, never()).saveAndFlush(any());
    }
}
