package cl.helvoca.messaging.meta;

import cl.helvoca.jobs.PersistentJobProperties;
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

class MetaWhatsAppCertificationReadinessServiceTest {

    @Test
    void readyForPilotCertificationOnlyWithCompleteConfigAndAllTrafficGatesClosed() {
        Fixture fixture = fixture();

        MetaWhatsAppCertificationReadinessResponse response = fixture.service().readiness();

        assertEquals("READY_FOR_PILOT_CERTIFICATION", response.state());
        assertTrue(response.ready());
        assertFalse(response.alreadyCertified());
        assertTrue(response.blockers().isEmpty());
        verify(fixture.phone, never()).setWhatsappCertifiedAt(any());
        verify(fixture.phones, never()).save(any());
    }

    @Test
    void blocksWhenAnyRealTrafficGateIsOpen() {
        Fixture fixture = fixture();
        fixture.meta.setEnabled(true);
        fixture.outbound.setDeliveryEnabled(true);
        fixture.outbound.setProvider(MetaWhatsAppMessagingProvider.ID);
        fixture.jobs.setEnabled(true);

        MetaWhatsAppCertificationReadinessResponse response = fixture.service().readiness();

        assertEquals("BLOCKED", response.state());
        assertFalse(response.ready());
        assertTrue(response.blockers().stream()
                .anyMatch(item -> "GLOBAL_META_MUST_BE_DISABLED".equals(item.code())));
        assertTrue(response.blockers().stream()
                .anyMatch(item -> "OUTBOUND_DELIVERY_MUST_BE_DISABLED".equals(item.code())));
        assertTrue(response.blockers().stream()
                .anyMatch(item -> "OUTBOUND_PROVIDER_MUST_BE_NONE".equals(item.code())));
        assertTrue(response.blockers().stream()
                .anyMatch(item -> "JOBS_MUST_BE_DISABLED".equals(item.code())));
    }

    @Test
    void blocksWithoutCredentialOrWebhookSecurity() {
        Fixture fixture = fixture();
        when(fixture.credentials.isAvailable("ACME_01")).thenReturn(false);
        fixture.meta.setAppSecret("");

        MetaWhatsAppCertificationReadinessResponse response = fixture.service().readiness();

        assertEquals("BLOCKED", response.state());
        assertFalse(response.ready());
        assertTrue(response.blockers().stream()
                .anyMatch(item -> "CREDENTIAL_UNAVAILABLE".equals(item.code())));
        assertTrue(response.blockers().stream()
                .anyMatch(item -> "WEBHOOK_SECURITY_NOT_READY".equals(item.code())));
    }

    @Test
    void reportsAlreadyCertifiedWithoutRewritingCertificationTimestamp() {
        Fixture fixture = fixture();
        Instant certifiedAt = Instant.parse("2026-09-20T04:00:00Z");
        when(fixture.phone.getWhatsappCertifiedAt()).thenReturn(certifiedAt);

        MetaWhatsAppCertificationReadinessResponse response = fixture.service().readiness();

        assertEquals("ALREADY_CERTIFIED", response.state());
        assertTrue(response.ready());
        assertTrue(response.alreadyCertified());
        verify(fixture.phone, never()).setWhatsappCertifiedAt(any());
        verify(fixture.phones, never()).save(any());
    }

    private static Fixture fixture() {
        UUID businessId = UUID.randomUUID();
        MetaWhatsAppTenantConfigRepository configs = mock(MetaWhatsAppTenantConfigRepository.class);
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        MetaWhatsAppCredentialAvailability credentials = mock(MetaWhatsAppCredentialAvailability.class);
        MetaWhatsAppProperties meta = new MetaWhatsAppProperties();
        OutboundMessagingProperties outbound = new OutboundMessagingProperties();
        PersistentJobProperties jobs = new PersistentJobProperties();
        PhoneNumber phone = mock(PhoneNumber.class);

        MetaWhatsAppTenantConfig config = new MetaWhatsAppTenantConfig();
        config.setBusinessId(businessId);
        config.setCredentialRef("ACME_01");
        config.setEnabled(false);

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(configs.findById(businessId)).thenReturn(Optional.of(config));
        when(phones.findAllByBusinessIdOrderByCreatedAtDesc(businessId)).thenReturn(List.of(phone));
        when(phone.getWhatsappProvider()).thenReturn(MetaWhatsAppMessagingProvider.ID);
        when(phone.getWhatsappExternalId()).thenReturn("123456789012345");
        when(phone.isActive()).thenReturn(true);
        when(phone.isWhatsappEnabled()).thenReturn(false);
        when(phone.getWhatsappCertifiedAt()).thenReturn(null);
        when(credentials.isAvailable("ACME_01")).thenReturn(true);

        meta.setEnabled(false);
        meta.setWebhookValidationEnabled(true);
        meta.setAppSecret("configured-app-secret");
        meta.setVerifyToken("configured-verify-token");
        outbound.setDeliveryEnabled(false);
        outbound.setProvider("NONE");
        jobs.setEnabled(false);

        return new Fixture(
                configs, phones, tenantProvider, credentials,
                meta, outbound, jobs, phone);
    }

    private record Fixture(
            MetaWhatsAppTenantConfigRepository configs,
            PhoneNumberRepository phones,
            TenantProvider tenantProvider,
            MetaWhatsAppCredentialAvailability credentials,
            MetaWhatsAppProperties meta,
            OutboundMessagingProperties outbound,
            PersistentJobProperties jobs,
            PhoneNumber phone) {

        MetaWhatsAppCertificationReadinessService service() {
            return new MetaWhatsAppCertificationReadinessService(
                    configs,
                    phones,
                    tenantProvider,
                    credentials,
                    meta,
                    outbound,
                    jobs);
        }
    }
}
