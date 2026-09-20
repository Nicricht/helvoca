package cl.helvoca.messaging.meta;

import cl.helvoca.messaging.outbound.MetaWhatsAppMessagingProvider;
import cl.helvoca.messaging.outbound.OutboundMessagingProperties;
import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.phone.PhoneNumberRepository;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MetaWhatsAppTenantHealthServiceTest {

    @Test
    void reportsGlobalIntegrationGateWithoutExposingCredentialReference() {
        Fixture fixture = configuredFixture();
        fixture.config.setEnabled(true);
        when(fixture.phone.isWhatsappEnabled()).thenReturn(true);
        when(fixture.phone.isActive()).thenReturn(true);
        when(fixture.phone.getWhatsappCertifiedAt()).thenReturn(Instant.parse("2026-09-20T03:00:00Z"));
        when(fixture.credentials.isAvailable("ACME_01")).thenReturn(true);

        fixture.meta.setEnabled(false);
        fixture.meta.setWebhookValidationEnabled(true);
        fixture.meta.setAppSecret("secret-value");
        fixture.meta.setVerifyToken("verify-value");
        fixture.outbound.setDeliveryEnabled(true);

        MetaWhatsAppTenantHealthResponse health = fixture.service().health();

        assertEquals("GLOBAL_INTEGRATION_DISABLED", health.state());
        assertTrue(health.configured());
        assertTrue(health.tenantEnabled());
        assertTrue(health.credentialAvailable());
        assertTrue(health.webhookSecurityReady());
        assertTrue(health.certified());
        assertFalse(health.integrationEnabled());
        assertEquals("123456789012345", health.providerPhoneNumberId());
        assertFalse(health.toString().contains("ACME_01"));
        assertFalse(health.toString().contains("secret-value"));
        assertFalse(health.toString().contains("verify-value"));
    }

    @Test
    void reportsReadyOnlyWhenAllLocalSafetyChecksPass() {
        Fixture fixture = configuredFixture();
        fixture.config.setEnabled(true);
        when(fixture.phone.isWhatsappEnabled()).thenReturn(true);
        when(fixture.phone.isActive()).thenReturn(true);
        when(fixture.phone.getWhatsappCertifiedAt()).thenReturn(Instant.parse("2026-09-20T03:00:00Z"));
        when(fixture.credentials.isAvailable("ACME_01")).thenReturn(true);

        fixture.meta.setEnabled(true);
        fixture.meta.setWebhookValidationEnabled(true);
        fixture.meta.setAppSecret("app-secret");
        fixture.meta.setVerifyToken("verify-token");
        fixture.outbound.setDeliveryEnabled(true);

        MetaWhatsAppTenantHealthResponse health = fixture.service().health();

        assertEquals("READY", health.state());
        assertTrue(health.configured());
        assertTrue(health.tenantEnabled());
        assertTrue(health.phoneActive());
        assertTrue(health.credentialAvailable());
        assertTrue(health.webhookSecurityReady());
        assertTrue(health.integrationEnabled());
        assertTrue(health.outboundDeliveryEnabled());
        assertTrue(health.certified());
    }

    @Test
    void failsClosedWhenWebhookSecurityIsIncomplete() {
        Fixture fixture = configuredFixture();
        fixture.config.setEnabled(true);
        when(fixture.phone.isWhatsappEnabled()).thenReturn(true);
        when(fixture.phone.isActive()).thenReturn(true);
        when(fixture.phone.getWhatsappCertifiedAt()).thenReturn(Instant.parse("2026-09-20T03:00:00Z"));
        when(fixture.credentials.isAvailable("ACME_01")).thenReturn(true);

        fixture.meta.setEnabled(true);
        fixture.meta.setWebhookValidationEnabled(true);
        fixture.meta.setAppSecret("");
        fixture.meta.setVerifyToken("verify-token");
        fixture.outbound.setDeliveryEnabled(true);

        MetaWhatsAppTenantHealthResponse health = fixture.service().health();

        assertEquals("WEBHOOK_SECURITY_NOT_READY", health.state());
        assertFalse(health.webhookSecurityReady());
    }

    @Test
    void reportsIncompleteForAmbiguousMetaPhoneConfiguration() {
        UUID businessId = UUID.randomUUID();
        MetaWhatsAppTenantConfigRepository configs = mock(MetaWhatsAppTenantConfigRepository.class);
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        MetaWhatsAppCredentialAvailability credentials = mock(MetaWhatsAppCredentialAvailability.class);
        MetaWhatsAppProperties meta = new MetaWhatsAppProperties();
        OutboundMessagingProperties outbound = new OutboundMessagingProperties();

        MetaWhatsAppTenantConfig config = new MetaWhatsAppTenantConfig();
        config.setBusinessId(businessId);
        config.setCredentialRef("ACME_01");

        PhoneNumber first = mock(PhoneNumber.class);
        PhoneNumber second = mock(PhoneNumber.class);
        when(first.getWhatsappProvider()).thenReturn(MetaWhatsAppMessagingProvider.ID);
        when(first.getWhatsappExternalId()).thenReturn("1111111111");
        when(second.getWhatsappProvider()).thenReturn(MetaWhatsAppMessagingProvider.ID);
        when(second.getWhatsappExternalId()).thenReturn("2222222222");
        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(configs.findById(businessId)).thenReturn(Optional.of(config));
        when(phones.findAllByBusinessIdOrderByCreatedAtDesc(businessId))
                .thenReturn(List.of(first, second));

        var service = new MetaWhatsAppTenantHealthService(
                configs, phones, tenantProvider, credentials, meta, outbound);

        MetaWhatsAppTenantHealthResponse health = service.health();

        assertEquals("INCOMPLETE", health.state());
        assertFalse(health.configured());
        verifyNoInteractions(credentials);
    }

    private static Fixture configuredFixture() {
        UUID businessId = UUID.randomUUID();
        MetaWhatsAppTenantConfigRepository configs = mock(MetaWhatsAppTenantConfigRepository.class);
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        MetaWhatsAppCredentialAvailability credentials = mock(MetaWhatsAppCredentialAvailability.class);
        MetaWhatsAppProperties meta = new MetaWhatsAppProperties();
        OutboundMessagingProperties outbound = new OutboundMessagingProperties();
        PhoneNumber phone = mock(PhoneNumber.class);

        MetaWhatsAppTenantConfig config = new MetaWhatsAppTenantConfig();
        config.setBusinessId(businessId);
        config.setCredentialRef("ACME_01");

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(configs.findById(businessId)).thenReturn(Optional.of(config));
        when(phones.findAllByBusinessIdOrderByCreatedAtDesc(businessId)).thenReturn(List.of(phone));
        when(phone.getId()).thenReturn(UUID.randomUUID());
        when(phone.getWhatsappProvider()).thenReturn(MetaWhatsAppMessagingProvider.ID);
        when(phone.getWhatsappExternalId()).thenReturn("123456789012345");

        return new Fixture(
                configs, phones, tenantProvider, credentials, meta, outbound, phone, config);
    }

    private record Fixture(
            MetaWhatsAppTenantConfigRepository configs,
            PhoneNumberRepository phones,
            TenantProvider tenantProvider,
            MetaWhatsAppCredentialAvailability credentials,
            MetaWhatsAppProperties meta,
            OutboundMessagingProperties outbound,
            PhoneNumber phone,
            MetaWhatsAppTenantConfig config) {

        MetaWhatsAppTenantHealthService service() {
            return new MetaWhatsAppTenantHealthService(
                    configs, phones, tenantProvider, credentials, meta, outbound);
        }
    }
}
