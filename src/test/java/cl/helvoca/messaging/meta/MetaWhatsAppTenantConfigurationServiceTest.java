package cl.helvoca.messaging.meta;

import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.phone.PhoneNumberRepository;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;

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
}
