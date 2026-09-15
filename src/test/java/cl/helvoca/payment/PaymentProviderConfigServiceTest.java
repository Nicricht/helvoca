package cl.helvoca.payment;

import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentProviderConfigServiceTest {
    @Test
    void liveModeIsRejected() {
        UUID businessId = UUID.randomUUID();
        PaymentProviderConfigRepository repo = mock(PaymentProviderConfigRepository.class);
        PaymentProviderCredentialResolver credentials = mock(PaymentProviderCredentialResolver.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        when(tenantProvider.requireBusinessId()).thenReturn(businessId);

        PaymentProviderConfigService service = new PaymentProviderConfigService(repo, credentials, tenantProvider);
        assertThrows(IllegalArgumentException.class, () -> service.replace(
                new PaymentProviderConfigService.Update(
                        "mercadopago", PaymentProviderConfig.Mode.LIVE, false, "TENANT_TEST")));
        verify(repo, never()).saveAndFlush(any());
    }

    @Test
    void enablingRequiresExplicitSandboxCredentials() {
        UUID businessId = UUID.randomUUID();
        PaymentProviderConfigRepository repo = mock(PaymentProviderConfigRepository.class);
        PaymentProviderCredentialResolver credentials = mock(PaymentProviderCredentialResolver.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(credentials.resolve("TENANT_TEST")).thenReturn(Optional.empty());

        PaymentProviderConfigService service = new PaymentProviderConfigService(repo, credentials, tenantProvider);
        assertThrows(IllegalArgumentException.class, () -> service.replace(
                new PaymentProviderConfigService.Update(
                        "mercadopago", PaymentProviderConfig.Mode.SANDBOX, true, "TENANT_TEST")));
        verify(repo, never()).saveAndFlush(any());
    }

    @Test
    void responseExposesWebhookPathButNeverCredentialValues() {
        UUID businessId = UUID.randomUUID();
        UUID webhookKey = UUID.randomUUID();
        PaymentProviderConfigRepository repo = mock(PaymentProviderConfigRepository.class);
        PaymentProviderCredentialResolver credentials = mock(PaymentProviderCredentialResolver.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(credentials.resolve("TENANT_TEST")).thenReturn(Optional.of(
                new PaymentProviderCredentialResolver.Credentials("top-secret", "webhook-secret", true)));
        when(repo.findById(businessId)).thenReturn(Optional.empty());
        when(repo.saveAndFlush(any())).thenAnswer(invocation -> {
            PaymentProviderConfig config = invocation.getArgument(0);
            config.setWebhookKey(webhookKey);
            return config;
        });

        PaymentProviderConfigService service = new PaymentProviderConfigService(repo, credentials, tenantProvider);
        PaymentProviderConfigService.View view = service.replace(
                new PaymentProviderConfigService.Update(
                        "mercadopago", PaymentProviderConfig.Mode.SANDBOX, true, "TENANT_TEST"));

        assertTrue(view.enabled());
        assertTrue(view.credentialsConfigured());
        assertEquals("/webhooks/v1/payments/mercadopago/" + webhookKey, view.webhookPath());
        assertFalse(view.toString().contains("top-secret"));
        assertFalse(view.toString().contains("webhook-secret"));
    }
}
