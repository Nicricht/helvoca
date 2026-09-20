package cl.helvoca.messaging.meta;

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

class MetaWhatsAppTenantConfigurationServiceTest {

    @Test
    void storesOnlyNonSecretTenantConfigurationAndKeepsDeliveryDisabled() {
        UUID businessId = UUID.randomUUID();
        UUID phoneRecordId = UUID.randomUUID();

        MetaWhatsAppTenantConfigRepository configs = mock(MetaWhatsAppTenantConfigRepository.class);
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);

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

        var service = new MetaWhatsAppTenantConfigurationService(configs, phones, tenantProvider);
        var response = service.replace(new MetaWhatsAppTenantConfigurationRequest(
                phoneRecordId,
                "meta_whatsapp_cloud",
                "123456789012345",
                " acme_01 "));

        assertEquals("META_WHATSAPP_CLOUD", response.provider());
        assertEquals("123456789012345", response.providerPhoneNumberId());
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
                        && "ACME_01".equals(config.getCredentialRef())));
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
                        "ACME_01")));

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
                        "ACME_01")));

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
                        "ACME_01")));

        assertThrows(IllegalArgumentException.class, () -> service.replace(
                new MetaWhatsAppTenantConfigurationRequest(
                        UUID.randomUUID(),
                        "META_WHATSAPP_CLOUD",
                        "1234567890",
                        "../BAD")));

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

}
