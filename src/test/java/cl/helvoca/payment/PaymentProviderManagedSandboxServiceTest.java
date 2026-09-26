package cl.helvoca.payment;

import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentProviderManagedSandboxServiceTest {

    @Test
    void enablesPlatformManagedSandboxWithoutClientCredentialReference() {
        UUID businessId = UUID.randomUUID();
        UUID webhookKey = UUID.randomUUID();
        PaymentProviderConfigRepository repo = mock(PaymentProviderConfigRepository.class);
        PaymentProviderCredentialResolver credentials = mock(PaymentProviderCredentialResolver.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(credentials.resolve("RECEPVOZ_MP_SANDBOX")).thenReturn(Optional.of(
                new PaymentProviderCredentialResolver.Credentials("secret-token", "secret-webhook", true)));
        when(repo.findById(businessId)).thenReturn(Optional.empty());
        when(repo.saveAndFlush(any(PaymentProviderConfig.class))).thenAnswer(invocation -> {
            PaymentProviderConfig config = invocation.getArgument(0);
            config.setWebhookKey(webhookKey);
            return config;
        });

        PaymentProviderConfigService service =
                new PaymentProviderConfigService(repo, credentials, tenantProvider);

        PaymentProviderConfigService.ManagedSandboxView result = service.enableManagedSandbox();

        assertTrue(result.available());
        assertTrue(result.configured());
        assertTrue(result.enabled());
        assertEquals("mercadopago", result.provider());
        assertEquals(PaymentProviderConfig.Mode.SANDBOX, result.mode());
        assertEquals("/webhooks/v1/payments/mercadopago/" + webhookKey, result.webhookPath());
        assertFalse(result.toString().contains("RECEPVOZ_MP_SANDBOX"));
        assertFalse(result.toString().contains("secret-token"));
        assertFalse(result.toString().contains("secret-webhook"));

        verify(repo).saveAndFlush(argThat(config ->
                businessId.equals(config.getBusinessId())
                        && "mercadopago".equals(config.getProvider())
                        && config.getMode() == PaymentProviderConfig.Mode.SANDBOX
                        && config.isEnabled()
                        && "RECEPVOZ_MP_SANDBOX".equals(config.getCredentialRef())));
    }

    @Test
    void reportsManagedSandboxUnavailableWithoutMutatingTenantConfig() {
        UUID businessId = UUID.randomUUID();
        PaymentProviderConfigRepository repo = mock(PaymentProviderConfigRepository.class);
        PaymentProviderCredentialResolver credentials = mock(PaymentProviderCredentialResolver.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(credentials.resolve("RECEPVOZ_MP_SANDBOX")).thenReturn(Optional.empty());
        when(repo.findById(businessId)).thenReturn(Optional.empty());

        PaymentProviderConfigService service =
                new PaymentProviderConfigService(repo, credentials, tenantProvider);

        PaymentProviderConfigService.ManagedSandboxView status = service.managedSandbox();

        assertFalse(status.available());
        assertFalse(status.configured());
        assertFalse(status.enabled());
        assertThrows(ResponseStatusException.class, service::enableManagedSandbox);
        verify(repo, never()).saveAndFlush(any());
    }

    @Test
    void managedDisableNeverOverwritesDifferentTenantCredentialConfiguration() {
        UUID businessId = UUID.randomUUID();
        PaymentProviderConfigRepository repo = mock(PaymentProviderConfigRepository.class);
        PaymentProviderCredentialResolver credentials = mock(PaymentProviderCredentialResolver.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);

        PaymentProviderConfig custom = new PaymentProviderConfig();
        custom.setBusinessId(businessId);
        custom.setProvider("mercadopago");
        custom.setMode(PaymentProviderConfig.Mode.SANDBOX);
        custom.setEnabled(true);
        custom.setCredentialRef("TENANT_CUSTOM");
        custom.setWebhookKey(UUID.randomUUID());

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(credentials.resolve("RECEPVOZ_MP_SANDBOX")).thenReturn(Optional.of(
                new PaymentProviderCredentialResolver.Credentials("secret-token", "secret-webhook", true)));
        when(repo.findById(businessId)).thenReturn(Optional.of(custom));

        PaymentProviderConfigService service =
                new PaymentProviderConfigService(repo, credentials, tenantProvider);

        ResponseStatusException error =
                assertThrows(ResponseStatusException.class, service::disableManagedSandbox);

        assertEquals(409, error.getStatusCode().value());
        assertTrue(custom.isEnabled());
        verify(repo, never()).saveAndFlush(any());
    }
}
