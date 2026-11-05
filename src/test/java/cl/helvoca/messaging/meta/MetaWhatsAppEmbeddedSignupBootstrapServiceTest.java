package cl.helvoca.messaging.meta;

import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static org.junit.jupiter.api.Assertions.*;

class MetaWhatsAppEmbeddedSignupBootstrapServiceTest {

    @Test
    void staysUnavailableAndDoesNotExposePartialIdsByDefault() {
        MetaWhatsAppProperties meta = new MetaWhatsAppProperties();
        meta.setEmbeddedSignupAppId("123456789");

        var response = new MetaWhatsAppEmbeddedSignupBootstrapService(meta).bootstrap();

        assertFalse(response.enabled());
        assertFalse(response.available());
        assertNull(response.appId());
        assertNull(response.configId());
        assertEquals("v26.0", response.graphApiVersion());
    }

    @Test
    void exposesOnlySdkBootstrapIdentifiersWhenFullyEnabled() {
        MetaWhatsAppProperties meta = new MetaWhatsAppProperties();
        meta.setEmbeddedSignupEnabled(true);
        meta.setEmbeddedSignupAppId("123456789");
        meta.setEmbeddedSignupConfigId("987654321");
        meta.setAppSecret("super-secret");
        meta.setVerifyToken("verify-secret");

        var response = new MetaWhatsAppEmbeddedSignupBootstrapService(meta).bootstrap();

        assertTrue(response.enabled());
        assertTrue(response.available());
        assertEquals("123456789", response.appId());
        assertEquals("987654321", response.configId());
        assertEquals("v26.0", response.graphApiVersion());
        assertFalse(response.toString().contains("super-secret"));
        assertFalse(response.toString().contains("verify-secret"));
    }

    @Test
    void exposesCoexistenceToEveryTenantOnlyWhenSelfServiceIsExplicitlyEnabled() {
        MetaWhatsAppProperties meta = readyProperties();
        meta.setCoexistenceEnabled(true);
        meta.setCoexistenceSelfServiceEnabled(true);
        TenantProvider tenant = mock(TenantProvider.class);
        when(tenant.requireBusinessId()).thenReturn(UUID.randomUUID());

        var response = new MetaWhatsAppEmbeddedSignupBootstrapService(meta, tenant).bootstrap();

        assertTrue(response.available());
        assertTrue(response.coexistenceAvailable());
    }

    @Test
    void pilotAllowlistExposesCoexistenceOnlyToItsTenantEvenWhenSelfServiceIsEnabled() {
        MetaWhatsAppProperties meta = readyProperties();
        meta.setCoexistenceEnabled(true);
        meta.setCoexistenceSelfServiceEnabled(true);
        UUID allowedTenant = UUID.randomUUID();
        meta.setCoexistenceAllowedBusinessId(allowedTenant.toString());
        meta.setCoexistenceAllowedPhone("+56950730447");
        TenantProvider otherTenant = mock(TenantProvider.class);
        when(otherTenant.requireBusinessId()).thenReturn(UUID.randomUUID());

        var otherResponse = new MetaWhatsAppEmbeddedSignupBootstrapService(meta, otherTenant).bootstrap();
        assertFalse(otherResponse.coexistenceAvailable());

        TenantProvider allowed = mock(TenantProvider.class);
        when(allowed.requireBusinessId()).thenReturn(allowedTenant);
        var allowedResponse = new MetaWhatsAppEmbeddedSignupBootstrapService(meta, allowed).bootstrap();
        assertTrue(allowedResponse.coexistenceAvailable());
    }

    @Test
    void doesNotExposeSelfServiceWithoutAuthenticatedTenantContext() {
        MetaWhatsAppProperties meta = readyProperties();
        meta.setCoexistenceEnabled(true);
        meta.setCoexistenceSelfServiceEnabled(true);

        var response = new MetaWhatsAppEmbeddedSignupBootstrapService(meta).bootstrap();

        assertFalse(response.coexistenceAvailable());
    }

    @Test
    void partialPilotAllowlistBlocksSelfServiceAndDoesNotExposeCoexistence() {
        MetaWhatsAppProperties meta = readyProperties();
        meta.setCoexistenceEnabled(true);
        meta.setCoexistenceSelfServiceEnabled(true);
        meta.setCoexistenceAllowedBusinessId(UUID.randomUUID().toString());
        TenantProvider tenant = mock(TenantProvider.class);
        when(tenant.requireBusinessId()).thenReturn(UUID.randomUUID());

        var response = new MetaWhatsAppEmbeddedSignupBootstrapService(meta, tenant).bootstrap();

        assertFalse(response.coexistenceAvailable());
    }

    @Test
    void doesNotExposeIdsWhenConfigurationIsIncomplete() {
        MetaWhatsAppProperties meta = new MetaWhatsAppProperties();
        meta.setEmbeddedSignupEnabled(true);
        meta.setEmbeddedSignupAppId("123456789");

        var response = new MetaWhatsAppEmbeddedSignupBootstrapService(meta).bootstrap();

        assertTrue(response.enabled());
        assertFalse(response.available());
        assertNull(response.appId());
        assertNull(response.configId());
    }

    private static MetaWhatsAppProperties readyProperties() {
        MetaWhatsAppProperties meta = new MetaWhatsAppProperties();
        meta.setEmbeddedSignupEnabled(true);
        meta.setEmbeddedSignupAppId("123456789");
        meta.setEmbeddedSignupConfigId("987654321");
        return meta;
    }
}
