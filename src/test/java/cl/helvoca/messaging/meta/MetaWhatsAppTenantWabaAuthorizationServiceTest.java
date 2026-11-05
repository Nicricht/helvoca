package cl.helvoca.messaging.meta;

import cl.helvoca.common.ConflictException;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MetaWhatsAppTenantWabaAuthorizationServiceTest {
    private static final String WABA = "1906385232743451";
    private static final String OTHER_WABA = "1972385232742141";

    @Test
    void persistsWabaAgainstCurrentTenantDisabled() {
        UUID businessId = UUID.randomUUID();
        var configs = mock(MetaWhatsAppTenantConfigRepository.class);
        var credentials = mock(MetaWhatsAppEmbeddedSignupCredentialReferenceResolver.class);
        var tenants = mock(TenantProvider.class);
        when(tenants.requireBusinessId()).thenReturn(businessId);
        when(configs.findById(businessId)).thenReturn(Optional.empty());
        when(credentials.requireReference()).thenReturn("EMBEDDED_SIGNUP_SYSTEM_USER");
        when(configs.saveAndFlush(any(MetaWhatsAppTenantConfig.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var service = new MetaWhatsAppTenantWabaAuthorizationService(configs, credentials, tenants);
        service.authorize(WABA);

        verify(configs).saveAndFlush(argThat(config ->
                businessId.equals(config.getBusinessId())
                        && WABA.equals(config.getWabaId())
                        && "EMBEDDED_SIGNUP_SYSTEM_USER".equals(config.getCredentialRef())
                        && !config.isEnabled()));
    }

    @Test
    void sameWabaCanBeReauthorizedWithoutDisablingAnAlreadyActiveTenant() {
        UUID businessId = UUID.randomUUID();
        var configs = mock(MetaWhatsAppTenantConfigRepository.class);
        var credentials = mock(MetaWhatsAppEmbeddedSignupCredentialReferenceResolver.class);
        var tenants = mock(TenantProvider.class);
        var existing = new MetaWhatsAppTenantConfig();
        existing.setBusinessId(businessId);
        existing.setWabaId(WABA);
        existing.setCredentialRef("EMBEDDED_SIGNUP_SYSTEM_USER");
        existing.setEnabled(true);
        when(tenants.requireBusinessId()).thenReturn(businessId);
        when(configs.findById(businessId)).thenReturn(Optional.of(existing));
        when(credentials.requireReference()).thenReturn("EMBEDDED_SIGNUP_SYSTEM_USER");

        var service = new MetaWhatsAppTenantWabaAuthorizationService(configs, credentials, tenants);
        service.authorize(WABA);

        assertTrue(existing.isEnabled());
        verify(configs, never()).saveAndFlush(any());
    }

    @Test
    void refusesToReplaceAnExistingTenantWabaWithoutExplicitDisconnect() {
        UUID businessId = UUID.randomUUID();
        var configs = mock(MetaWhatsAppTenantConfigRepository.class);
        var credentials = mock(MetaWhatsAppEmbeddedSignupCredentialReferenceResolver.class);
        var tenants = mock(TenantProvider.class);
        var existing = new MetaWhatsAppTenantConfig();
        existing.setBusinessId(businessId);
        existing.setWabaId(WABA);
        existing.setCredentialRef("EMBEDDED_SIGNUP_SYSTEM_USER");
        when(tenants.requireBusinessId()).thenReturn(businessId);
        when(configs.findById(businessId)).thenReturn(Optional.of(existing));

        var service = new MetaWhatsAppTenantWabaAuthorizationService(configs, credentials, tenants);
        ConflictException error = assertThrows(
                ConflictException.class,
                () -> service.authorize(OTHER_WABA));

        assertEquals("META_WHATSAPP_TENANT_ALREADY_BOUND_TO_ANOTHER_WABA", error.getMessage());
        verifyNoInteractions(credentials);
        verify(configs, never()).saveAndFlush(any());
    }

    @Test
    void refusesToChangeAnActiveLegacyConfigWithoutWabaBinding() {
        UUID businessId = UUID.randomUUID();
        var configs = mock(MetaWhatsAppTenantConfigRepository.class);
        var credentials = mock(MetaWhatsAppEmbeddedSignupCredentialReferenceResolver.class);
        var tenants = mock(TenantProvider.class);
        var existing = new MetaWhatsAppTenantConfig();
        existing.setBusinessId(businessId);
        existing.setCredentialRef("EMBEDDED_SIGNUP_SYSTEM_USER");
        existing.setEnabled(true);
        when(tenants.requireBusinessId()).thenReturn(businessId);
        when(configs.findById(businessId)).thenReturn(Optional.of(existing));

        var service = new MetaWhatsAppTenantWabaAuthorizationService(configs, credentials, tenants);
        ConflictException error = assertThrows(
                ConflictException.class,
                () -> service.authorize(WABA));

        assertEquals("META_WHATSAPP_ACTIVE_CONFIG_REQUIRES_DEACTIVATION", error.getMessage());
        verifyNoInteractions(credentials);
        verify(configs, never()).saveAndFlush(any());
    }

    @Test
    void requiresExactPersistedWabaForCurrentTenant() {
        UUID businessId = UUID.randomUUID();
        var configs = mock(MetaWhatsAppTenantConfigRepository.class);
        var credentials = mock(MetaWhatsAppEmbeddedSignupCredentialReferenceResolver.class);
        var tenants = mock(TenantProvider.class);
        var existing = new MetaWhatsAppTenantConfig();
        existing.setBusinessId(businessId);
        existing.setWabaId(WABA);
        existing.setCredentialRef("EMBEDDED_SIGNUP_SYSTEM_USER");
        when(tenants.requireBusinessId()).thenReturn(businessId);
        when(configs.findById(businessId)).thenReturn(Optional.of(existing));

        var service = new MetaWhatsAppTenantWabaAuthorizationService(configs, credentials, tenants);
        assertDoesNotThrow(() -> service.requireAuthorized(WABA));

        ConflictException error = assertThrows(
                ConflictException.class,
                () -> service.requireAuthorized(OTHER_WABA));
        assertEquals("META_WHATSAPP_WABA_NOT_AUTHORIZED_FOR_TENANT", error.getMessage());
    }
    @Test
    void rejectsMissingOrMalformedWabaIdsBeforeReadingTenantConfiguration() {
        UUID businessId = UUID.randomUUID();
        var configs = mock(MetaWhatsAppTenantConfigRepository.class);
        var credentials = mock(MetaWhatsAppEmbeddedSignupCredentialReferenceResolver.class);
        var tenants = mock(TenantProvider.class);
        when(tenants.requireBusinessId()).thenReturn(businessId);
        var service = new MetaWhatsAppTenantWabaAuthorizationService(configs, credentials, tenants);

        assertThrows(IllegalArgumentException.class, () -> service.authorize(null));
        assertThrows(IllegalArgumentException.class, () -> service.authorize("not-numeric"));
        verifyNoInteractions(configs, credentials);
    }

    @Test
    void requireAuthorizedFailsClosedWhenTenantHasNoStoredConfiguration() {
        UUID businessId = UUID.randomUUID();
        var configs = mock(MetaWhatsAppTenantConfigRepository.class);
        var credentials = mock(MetaWhatsAppEmbeddedSignupCredentialReferenceResolver.class);
        var tenants = mock(TenantProvider.class);
        when(tenants.requireBusinessId()).thenReturn(businessId);
        when(configs.findById(businessId)).thenReturn(Optional.empty());
        var service = new MetaWhatsAppTenantWabaAuthorizationService(configs, credentials, tenants);

        ConflictException error = assertThrows(
                ConflictException.class, () -> service.requireAuthorized(WABA));
        assertEquals("META_WHATSAPP_WABA_NOT_AUTHORIZED_FOR_TENANT", error.getMessage());
        verifyNoInteractions(credentials);
    }


}
