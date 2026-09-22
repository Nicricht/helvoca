package cl.helvoca.messaging.meta;

import cl.helvoca.audit.AuditService;
import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.phone.PhoneNumberRepository;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MetaWhatsAppTenantConfigurationServiceTest {

    @Test
    void storesOnlyNonSecretTenantConfigurationAndKeepsDeliveryDisabled() {
        UUID businessId = UUID.randomUUID();
        UUID phoneRecordId = UUID.randomUUID();

        MetaWhatsAppTenantConfigRepository configs = mock(MetaWhatsAppTenantConfigRepository.class);
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        AuditService audit = mock(AuditService.class);

        PhoneNumber phone = new PhoneNumber();
        phone.setBusinessId(businessId);
        phone.setPhoneNumber("+56922222222");
        phone.setActive(true);
        phone.setWhatsappEnabled(true);
        phone.setWhatsappProvider("TWILIO_WHATSAPP");
        phone.setWhatsappCertifiedAt(java.time.Instant.now());

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(phones.findByIdAndBusinessId(phoneRecordId, businessId)).thenReturn(Optional.of(phone));
        when(configs.findById(businessId)).thenReturn(Optional.empty());

        var service = new MetaWhatsAppTenantConfigurationService(
                configs, phones, tenantProvider, credentialRef -> false, null, null, audit);
        var response = service.replace(new MetaWhatsAppTenantConfigurationRequest(
                phoneRecordId,
                "meta_whatsapp_cloud",
                "123456789012345",
                " acme_01 ",
                "987654321098765"));

        assertEquals("META_WHATSAPP_CLOUD", response.provider());
        assertEquals("123456789012345", response.providerPhoneNumberId());
        assertEquals("987654321098765", response.wabaId());
        assertEquals("ACME_01", response.credentialRef());
        assertFalse(response.enabled());

        assertEquals("META_WHATSAPP_CLOUD", phone.getWhatsappProvider());
        assertEquals("123456789012345", phone.getWhatsappExternalId());
        assertFalse(phone.isWhatsappEnabled());
        assertNull(phone.getWhatsappCertifiedAt());

        verify(phones).save(phone);
        verify(configs).save(argThat(config ->
                businessId.equals(config.getBusinessId())
                        && !config.isEnabled()
                        && "ACME_01".equals(config.getCredentialRef())
                        && "987654321098765".equals(config.getWabaId())));
        verify(audit).humanSuccess(
                eq(businessId),
                eq("META_WHATSAPP_CONFIG_REPLACE"),
                eq("META_WHATSAPP_CONFIG"),
                eq(businessId),
                eq(Map.of(
                        "provider", "META_WHATSAPP_CLOUD",
                        "configured", false,
                        "enabled", false)),
                eq(Map.of(
                        "provider", "META_WHATSAPP_CLOUD",
                        "configured", true,
                        "enabled", false)));
        verify(audit).humanSuccess(
                eq(businessId),
                eq("WHATSAPP_CERTIFICATION_CLEARED"),
                eq("WHATSAPP_SENDER"),
                eq(businessId),
                eq(Map.of(
                        "whatsappEnabled", true,
                        "certified", true)),
                eq(Map.of(
                        "whatsappEnabled", false,
                        "certified", false)));
    }

    @Test
    void replacingEnabledUncertifiedSenderAuditsSenderDisable() {
        UUID businessId = UUID.randomUUID();
        UUID phoneRecordId = UUID.randomUUID();

        MetaWhatsAppTenantConfigRepository configs = mock(MetaWhatsAppTenantConfigRepository.class);
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        AuditService audit = mock(AuditService.class);

        PhoneNumber phone = new PhoneNumber();
        phone.setBusinessId(businessId);
        phone.setPhoneNumber("+56922222222");
        phone.setActive(true);
        phone.setWhatsappEnabled(true);
        phone.setWhatsappProvider("META_WHATSAPP_CLOUD");

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(phones.findByIdAndBusinessId(phoneRecordId, businessId)).thenReturn(Optional.of(phone));
        when(configs.findById(businessId)).thenReturn(Optional.empty());

        var service = new MetaWhatsAppTenantConfigurationService(
                configs, phones, tenantProvider, credentialRef -> false, null, null, audit);
        service.replace(new MetaWhatsAppTenantConfigurationRequest(
                phoneRecordId,
                "META_WHATSAPP_CLOUD",
                "123456789012345",
                "ACME_01",
                null));

        verify(audit).humanSuccess(
                eq(businessId),
                eq("WHATSAPP_SENDER_DISABLED"),
                eq("WHATSAPP_SENDER"),
                eq(businessId),
                eq(Map.of(
                        "whatsappEnabled", true,
                        "certified", false)),
                eq(Map.of(
                        "whatsappEnabled", false,
                        "certified", false)));
    }

    @Test
    void refusesPhoneRecordFromAnotherTenant() {
        UUID businessId = UUID.randomUUID();
        UUID phoneRecordId = UUID.randomUUID();

        MetaWhatsAppTenantConfigRepository configs = mock(MetaWhatsAppTenantConfigRepository.class);
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(phones.findByIdAndBusinessId(phoneRecordId, businessId)).thenReturn(Optional.empty());

        var service = new MetaWhatsAppTenantConfigurationService(configs, phones, tenantProvider);

        assertThrows(RuntimeException.class, () -> service.replace(
                new MetaWhatsAppTenantConfigurationRequest(
                        phoneRecordId,
                        "META_WHATSAPP_CLOUD",
                        "1234567890",
                        "ACME_01",
                        null)));

        verify(configs, never()).save(any());
        verify(phones, never()).save(any());
    }

    @Test
    void rejectsUnsupportedProviderBeforePersistingAnything() {
        MetaWhatsAppTenantConfigRepository configs = mock(MetaWhatsAppTenantConfigRepository.class);
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        when(tenantProvider.requireBusinessId()).thenReturn(UUID.randomUUID());

        var service = new MetaWhatsAppTenantConfigurationService(configs, phones, tenantProvider);

        var error = assertThrows(IllegalArgumentException.class, () -> service.replace(
                new MetaWhatsAppTenantConfigurationRequest(
                        UUID.randomUUID(),
                        "TWILIO_WHATSAPP",
                        "1234567890",
                        "ACME_01",
                        null)));

        assertEquals("Only META_WHATSAPP_CLOUD is supported", error.getMessage());
        verifyNoInteractions(configs, phones);
    }

    @Test
    void rejectsMalformedMetaPhoneNumberIdAndCredentialRef() {
        MetaWhatsAppTenantConfigRepository configs = mock(MetaWhatsAppTenantConfigRepository.class);
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        when(tenantProvider.requireBusinessId()).thenReturn(UUID.randomUUID());

        var service = new MetaWhatsAppTenantConfigurationService(configs, phones, tenantProvider);

        assertThrows(IllegalArgumentException.class, () -> service.replace(
                new MetaWhatsAppTenantConfigurationRequest(
                        UUID.randomUUID(),
                        "META_WHATSAPP_CLOUD",
                        "../bad",
                        "ACME_01",
                        null)));

        assertThrows(IllegalArgumentException.class, () -> service.replace(
                new MetaWhatsAppTenantConfigurationRequest(
                        UUID.randomUUID(),
                        "META_WHATSAPP_CLOUD",
                        "1234567890",
                        "../BAD",
                        null)));

        verifyNoInteractions(configs, phones);
    }
    @Test
    void statusReturnsNotConfiguredWithoutTenantConfigOrMetaPhone() {
        UUID businessId = UUID.randomUUID();
        MetaWhatsAppTenantConfigRepository configs = mock(MetaWhatsAppTenantConfigRepository.class);
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(configs.findById(businessId)).thenReturn(Optional.empty());
        when(phones.findAllByBusinessIdOrderByCreatedAtDesc(businessId)).thenReturn(List.of());

        var service = new MetaWhatsAppTenantConfigurationService(configs, phones, tenantProvider);
        var response = service.status();

        assertEquals("NOT_CONFIGURED", response.status());
        assertFalse(response.configured());
        assertFalse(response.enabled());
        assertNull(response.provider());
        assertNull(response.phoneRecordId());
        assertFalse(response.credentialReferenceConfigured());
    }

    @Test
    void statusReportsConfiguredDisabledWithoutReadingOrReturningSecret() {
        UUID businessId = UUID.randomUUID();
        UUID phoneRecordId = UUID.randomUUID();
        Instant certifiedAt = Instant.parse("2026-09-20T00:00:00Z");

        MetaWhatsAppTenantConfigRepository configs = mock(MetaWhatsAppTenantConfigRepository.class);
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        PhoneNumber phone = mock(PhoneNumber.class);

        MetaWhatsAppTenantConfig config = new MetaWhatsAppTenantConfig();
        config.setBusinessId(businessId);
        config.setCredentialRef("ACME_01");
        config.setWabaId("987654321098765");
        config.setEnabled(false);

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(configs.findById(businessId)).thenReturn(Optional.of(config));
        when(phones.findAllByBusinessIdOrderByCreatedAtDesc(businessId)).thenReturn(List.of(phone));
        when(phone.getId()).thenReturn(phoneRecordId);
        when(phone.getWhatsappProvider()).thenReturn("META_WHATSAPP_CLOUD");
        when(phone.getWhatsappExternalId()).thenReturn("123456789012345");
        when(phone.getPhoneNumber()).thenReturn("+56922222222");
        when(phone.getWhatsappCertifiedAt()).thenReturn(certifiedAt);
        when(phone.isWhatsappEnabled()).thenReturn(false);

        var service = new MetaWhatsAppTenantConfigurationService(configs, phones, tenantProvider);
        var response = service.status();

        assertEquals("CONFIGURED_DISABLED", response.status());
        assertTrue(response.configured());
        assertFalse(response.enabled());
        assertEquals("META_WHATSAPP_CLOUD", response.provider());
        assertEquals(phoneRecordId, response.phoneRecordId());
        assertEquals("+56922222222", response.phoneNumber());
        assertEquals("123456789012345", response.providerPhoneNumberId());
        assertEquals("987654321098765", response.wabaId());
        assertTrue(response.credentialReferenceConfigured());
        assertEquals(certifiedAt, response.certifiedAt());
    }

    @Test
    void statusFailsClosedWhenMultipleMetaPhonesAreConfigured() {
        UUID businessId = UUID.randomUUID();

        MetaWhatsAppTenantConfigRepository configs = mock(MetaWhatsAppTenantConfigRepository.class);
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        PhoneNumber first = mock(PhoneNumber.class);
        PhoneNumber second = mock(PhoneNumber.class);

        MetaWhatsAppTenantConfig config = new MetaWhatsAppTenantConfig();
        config.setBusinessId(businessId);
        config.setCredentialRef("ACME_01");
        config.setEnabled(false);

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(configs.findById(businessId)).thenReturn(Optional.of(config));
        when(phones.findAllByBusinessIdOrderByCreatedAtDesc(businessId)).thenReturn(List.of(first, second));
        when(first.getWhatsappProvider()).thenReturn("META_WHATSAPP_CLOUD");
        when(first.getWhatsappExternalId()).thenReturn("1111111111");
        when(second.getWhatsappProvider()).thenReturn("META_WHATSAPP_CLOUD");
        when(second.getWhatsappExternalId()).thenReturn("2222222222");

        var service = new MetaWhatsAppTenantConfigurationService(configs, phones, tenantProvider);
        var response = service.status();

        assertEquals("INCOMPLETE", response.status());
        assertFalse(response.configured());
        assertFalse(response.enabled());
        assertNull(response.phoneRecordId());
        assertTrue(response.credentialReferenceConfigured());
    }

    @Test
    void activateEnablesTenantOnlyWhenConfigurationAndCredentialAreComplete() {
        UUID businessId = UUID.randomUUID();
        MetaWhatsAppTenantConfigRepository configs = mock(MetaWhatsAppTenantConfigRepository.class);
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        MetaWhatsAppCredentialAvailability credentials = mock(MetaWhatsAppCredentialAvailability.class);
        MetaWhatsAppCertificationReadinessService certification = mock(MetaWhatsAppCertificationReadinessService.class);
        MetaWhatsAppDeploymentReadinessService deployment = mock(MetaWhatsAppDeploymentReadinessService.class);
        AuditService audit = mock(AuditService.class);
        PhoneNumber phone = mock(PhoneNumber.class);

        MetaWhatsAppTenantConfig config = new MetaWhatsAppTenantConfig();
        config.setBusinessId(businessId);
        config.setCredentialRef("ACME_01");
        config.setEnabled(false);

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(configs.findById(businessId)).thenReturn(Optional.of(config));
        when(credentials.isAvailable("ACME_01")).thenReturn(true);
        when(phones.findAllByBusinessIdOrderByCreatedAtDesc(businessId)).thenReturn(List.of(phone));
        when(phone.getWhatsappProvider()).thenReturn("META_WHATSAPP_CLOUD");
        when(phone.getWhatsappExternalId()).thenReturn("123456789012345");
        when(phone.isActive()).thenReturn(true);
        when(certification.readiness()).thenReturn(certifiedReadiness());
        when(deployment.readiness()).thenReturn(stagingReadiness());

        var service = new MetaWhatsAppTenantConfigurationService(
                configs, phones, tenantProvider, credentials, certification, deployment, audit);
        var response = service.activate();

        assertTrue(config.isEnabled());
        verify(phone).setWhatsappEnabled(true);
        verify(configs).save(config);
        verify(phones).save(phone);
        assertEquals("CONFIGURED_ENABLED", response.status());
        assertTrue(response.configured());
        assertTrue(response.enabled());
        verify(audit).humanSuccess(
                eq(businessId),
                eq("META_WHATSAPP_ACTIVATE"),
                eq("META_WHATSAPP_CONFIG"),
                eq(businessId),
                eq(Map.of("provider", "META_WHATSAPP_CLOUD", "enabled", false)),
                eq(Map.of("provider", "META_WHATSAPP_CLOUD", "enabled", true)));
    }

    @Test
    void activateRefusesIncompleteCertificationAndChangesNothing() {
        UUID businessId = UUID.randomUUID();
        MetaWhatsAppTenantConfigRepository configs = mock(MetaWhatsAppTenantConfigRepository.class);
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        MetaWhatsAppCredentialAvailability credentials = mock(MetaWhatsAppCredentialAvailability.class);
        MetaWhatsAppCertificationReadinessService certification = mock(MetaWhatsAppCertificationReadinessService.class);
        MetaWhatsAppDeploymentReadinessService deployment = mock(MetaWhatsAppDeploymentReadinessService.class);
        AuditService audit = mock(AuditService.class);
        PhoneNumber phone = mock(PhoneNumber.class);

        MetaWhatsAppTenantConfig config = new MetaWhatsAppTenantConfig();
        config.setBusinessId(businessId);
        config.setCredentialRef("ACME_01");
        config.setEnabled(false);

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(configs.findById(businessId)).thenReturn(Optional.of(config));
        when(credentials.isAvailable("ACME_01")).thenReturn(true);
        when(phones.findAllByBusinessIdOrderByCreatedAtDesc(businessId)).thenReturn(List.of(phone));
        when(phone.getWhatsappProvider()).thenReturn("META_WHATSAPP_CLOUD");
        when(phone.getWhatsappExternalId()).thenReturn("123456789012345");
        when(phone.isActive()).thenReturn(true);
        when(certification.readiness()).thenReturn(new MetaWhatsAppCertificationReadinessResponse(
                "READY_FOR_PILOT_CERTIFICATION", true, false, List.of()));

        var service = new MetaWhatsAppTenantConfigurationService(
                configs, phones, tenantProvider, credentials, certification, deployment, audit);

        var error = assertThrows(IllegalStateException.class, service::activate);

        assertEquals("Meta WhatsApp certification is incomplete", error.getMessage());
        assertFalse(config.isEnabled());
        verify(deployment, never()).readiness();
        verify(configs, never()).save(any());
        verify(phones, never()).save(any());
        verify(phone, never()).setWhatsappEnabled(true);
        verifyNoInteractions(audit);
    }

    @Test
    void activateRefusesBlockedDeploymentAndChangesNothing() {
        UUID businessId = UUID.randomUUID();
        MetaWhatsAppTenantConfigRepository configs = mock(MetaWhatsAppTenantConfigRepository.class);
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        MetaWhatsAppCredentialAvailability credentials = mock(MetaWhatsAppCredentialAvailability.class);
        MetaWhatsAppCertificationReadinessService certification = mock(MetaWhatsAppCertificationReadinessService.class);
        MetaWhatsAppDeploymentReadinessService deployment = mock(MetaWhatsAppDeploymentReadinessService.class);
        PhoneNumber phone = mock(PhoneNumber.class);

        MetaWhatsAppTenantConfig config = new MetaWhatsAppTenantConfig();
        config.setBusinessId(businessId);
        config.setCredentialRef("ACME_01");
        config.setEnabled(false);

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(configs.findById(businessId)).thenReturn(Optional.of(config));
        when(credentials.isAvailable("ACME_01")).thenReturn(true);
        when(phones.findAllByBusinessIdOrderByCreatedAtDesc(businessId)).thenReturn(List.of(phone));
        when(phone.getWhatsappProvider()).thenReturn("META_WHATSAPP_CLOUD");
        when(phone.getWhatsappExternalId()).thenReturn("123456789012345");
        when(phone.isActive()).thenReturn(true);
        when(certification.readiness()).thenReturn(certifiedReadiness());
        when(deployment.readiness()).thenReturn(new MetaWhatsAppDeploymentReadinessResponse(
                "BLOCKED", false, true, true, true, false, false, "NONE", false, List.of()));

        var service = new MetaWhatsAppTenantConfigurationService(
                configs, phones, tenantProvider, credentials, certification, deployment);

        var error = assertThrows(IllegalStateException.class, service::activate);

        assertEquals("Meta WhatsApp deployment staging is not ready", error.getMessage());
        assertFalse(config.isEnabled());
        verify(configs, never()).save(any());
        verify(phones, never()).save(any());
        verify(phone, never()).setWhatsappEnabled(true);
    }

    @Test
    void activateRefusesMissingDeploymentCredentialAndChangesNothing() {
        UUID businessId = UUID.randomUUID();
        MetaWhatsAppTenantConfigRepository configs = mock(MetaWhatsAppTenantConfigRepository.class);
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        MetaWhatsAppCredentialAvailability credentials = mock(MetaWhatsAppCredentialAvailability.class);

        MetaWhatsAppTenantConfig config = new MetaWhatsAppTenantConfig();
        config.setBusinessId(businessId);
        config.setCredentialRef("ACME_01");
        config.setEnabled(false);

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(configs.findById(businessId)).thenReturn(Optional.of(config));
        when(credentials.isAvailable("ACME_01")).thenReturn(false);

        var service = new MetaWhatsAppTenantConfigurationService(
                configs, phones, tenantProvider, credentials);

        var error = assertThrows(IllegalStateException.class, service::activate);

        assertEquals("Meta WhatsApp credential is unavailable", error.getMessage());
        assertFalse(config.isEnabled());
        verify(configs, never()).save(any());
        verify(phones, never()).save(any());
    }

    @Test
    void activateRefusesAmbiguousMetaPhoneConfiguration() {
        UUID businessId = UUID.randomUUID();
        MetaWhatsAppTenantConfigRepository configs = mock(MetaWhatsAppTenantConfigRepository.class);
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        MetaWhatsAppCredentialAvailability credentials = mock(MetaWhatsAppCredentialAvailability.class);
        PhoneNumber first = mock(PhoneNumber.class);
        PhoneNumber second = mock(PhoneNumber.class);

        MetaWhatsAppTenantConfig config = new MetaWhatsAppTenantConfig();
        config.setBusinessId(businessId);
        config.setCredentialRef("ACME_01");
        config.setEnabled(false);

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(configs.findById(businessId)).thenReturn(Optional.of(config));
        when(credentials.isAvailable("ACME_01")).thenReturn(true);
        when(phones.findAllByBusinessIdOrderByCreatedAtDesc(businessId)).thenReturn(List.of(first, second));
        when(first.getWhatsappProvider()).thenReturn("META_WHATSAPP_CLOUD");
        when(first.getWhatsappExternalId()).thenReturn("1111111111");
        when(second.getWhatsappProvider()).thenReturn("META_WHATSAPP_CLOUD");
        when(second.getWhatsappExternalId()).thenReturn("2222222222");

        var service = new MetaWhatsAppTenantConfigurationService(
                configs, phones, tenantProvider, credentials);

        var error = assertThrows(IllegalStateException.class, service::activate);

        assertEquals("Meta WhatsApp requires exactly one configured phone", error.getMessage());
        assertFalse(config.isEnabled());
        verify(configs, never()).save(any());
        verify(phones, never()).save(any());
    }

    @Test
    void deactivateDisablesTenantWithoutDeletingConfiguration() {
        UUID businessId = UUID.randomUUID();
        MetaWhatsAppTenantConfigRepository configs = mock(MetaWhatsAppTenantConfigRepository.class);
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        AuditService audit = mock(AuditService.class);
        PhoneNumber phone = mock(PhoneNumber.class);

        MetaWhatsAppTenantConfig config = new MetaWhatsAppTenantConfig();
        config.setBusinessId(businessId);
        config.setCredentialRef("ACME_01");
        config.setEnabled(true);

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(configs.findById(businessId)).thenReturn(Optional.of(config));
        when(phones.findAllByBusinessIdOrderByCreatedAtDesc(businessId)).thenReturn(List.of(phone));
        when(phone.getWhatsappProvider()).thenReturn("META_WHATSAPP_CLOUD");
        when(phone.getWhatsappExternalId()).thenReturn("123456789012345");
        when(phone.isWhatsappEnabled()).thenReturn(true);

        var service = new MetaWhatsAppTenantConfigurationService(
                configs, phones, tenantProvider, credentialRef -> false, null, null, audit);
        var response = service.deactivate();

        assertFalse(config.isEnabled());
        verify(configs).save(config);
        verify(phone).setWhatsappEnabled(false);
        verify(phones).save(phone);
        assertEquals("CONFIGURED_DISABLED", response.status());
        assertTrue(response.configured());
        assertFalse(response.enabled());

        verify(configs, never()).delete(any());
        verify(phones, never()).delete(any());
        verify(audit).humanSuccess(
                eq(businessId),
                eq("META_WHATSAPP_DEACTIVATE"),
                eq("META_WHATSAPP_CONFIG"),
                eq(businessId),
                eq(Map.of("provider", "META_WHATSAPP_CLOUD", "enabled", true)),
                eq(Map.of("provider", "META_WHATSAPP_CLOUD", "enabled", false)));
    }

    @Test
    void deactivateIsIdempotentWhenNothingIsConfigured() {
        UUID businessId = UUID.randomUUID();
        MetaWhatsAppTenantConfigRepository configs = mock(MetaWhatsAppTenantConfigRepository.class);
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(configs.findById(businessId)).thenReturn(Optional.empty());
        when(phones.findAllByBusinessIdOrderByCreatedAtDesc(businessId)).thenReturn(List.of());

        var service = new MetaWhatsAppTenantConfigurationService(configs, phones, tenantProvider);
        var response = service.deactivate();

        assertEquals("NOT_CONFIGURED", response.status());
        assertFalse(response.configured());
        assertFalse(response.enabled());
        verify(configs, never()).save(any());
        verify(phones, never()).save(any());
    }

    @Test
    void deactivateFailsClosedByDisablingEveryMetaPhoneWhenConfigurationIsAmbiguous() {
        UUID businessId = UUID.randomUUID();
        MetaWhatsAppTenantConfigRepository configs = mock(MetaWhatsAppTenantConfigRepository.class);
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        PhoneNumber first = mock(PhoneNumber.class);
        PhoneNumber second = mock(PhoneNumber.class);

        MetaWhatsAppTenantConfig config = new MetaWhatsAppTenantConfig();
        config.setBusinessId(businessId);
        config.setCredentialRef("ACME_01");
        config.setEnabled(true);

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(configs.findById(businessId)).thenReturn(Optional.of(config));
        when(phones.findAllByBusinessIdOrderByCreatedAtDesc(businessId)).thenReturn(List.of(first, second));
        when(first.getWhatsappProvider()).thenReturn("META_WHATSAPP_CLOUD");
        when(second.getWhatsappProvider()).thenReturn("META_WHATSAPP_CLOUD");
        when(first.getWhatsappExternalId()).thenReturn("1111111111");
        when(second.getWhatsappExternalId()).thenReturn("2222222222");
        when(first.isWhatsappEnabled()).thenReturn(true);
        when(second.isWhatsappEnabled()).thenReturn(true);

        var service = new MetaWhatsAppTenantConfigurationService(configs, phones, tenantProvider);
        var response = service.deactivate();

        assertFalse(config.isEnabled());
        verify(first).setWhatsappEnabled(false);
        verify(second).setWhatsappEnabled(false);
        verify(phones).save(first);
        verify(phones).save(second);
        assertEquals("INCOMPLETE", response.status());
        assertFalse(response.configured());
        assertFalse(response.enabled());
    }

    private static MetaWhatsAppCertificationReadinessResponse certifiedReadiness() {
        return new MetaWhatsAppCertificationReadinessResponse(
                "ALREADY_CERTIFIED", true, true, List.of());
    }

    private static MetaWhatsAppDeploymentReadinessResponse stagingReadiness() {
        return new MetaWhatsAppDeploymentReadinessResponse(
                "READY_FOR_TENANT_STAGING",
                true,
                true,
                true,
                true,
                false,
                false,
                "NONE",
                false,
                List.of());
    }

}
