package cl.helvoca.messaging.meta;

import cl.helvoca.messaging.outbound.MetaWhatsAppMessagingProvider;
import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.phone.PhoneNumberRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MetaWhatsAppTestBootstrapRunnerTest {
    private static final String ANCHOR = "+14355652512";
    private static final String PHONE_NUMBER_ID = "1258818237324057";
    private static final String WABA_ID = "1388561203388953";

    @Test
    void bootstrapsMetaTestSenderForAnchorTenant() {
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        MetaWhatsAppTenantConfigRepository configs = mock(MetaWhatsAppTenantConfigRepository.class);

        PhoneNumber phone = new PhoneNumber();
        UUID businessId = UUID.randomUUID();
        phone.setBusinessId(businessId);
        phone.setPhoneNumber(ANCHOR);
        phone.setActive(true);
        phone.setWhatsappEnabled(false);
        phone.setWhatsappProvider("TWILIO_WHATSAPP");

        when(phones.findByPhoneNumberAndActiveTrue(ANCHOR)).thenReturn(Optional.of(phone));
        when(configs.findById(businessId)).thenReturn(Optional.empty());

        MetaWhatsAppTestBootstrapRunner runner = new MetaWhatsAppTestBootstrapRunner(
                true,
                ANCHOR,
                PHONE_NUMBER_ID,
                WABA_ID,
                "PILOT_01",
                phones,
                configs);

        runner.run(null);

        assertEquals(MetaWhatsAppMessagingProvider.ID, phone.getWhatsappProvider());
        assertEquals(PHONE_NUMBER_ID, phone.getWhatsappExternalId());
        assertTrue(phone.isWhatsappEnabled());
        assertNull(phone.getWhatsappCertifiedAt());
        verify(phones).save(phone);

        var configCaptor = org.mockito.ArgumentCaptor.forClass(MetaWhatsAppTenantConfig.class);
        verify(configs).save(configCaptor.capture());
        MetaWhatsAppTenantConfig config = configCaptor.getValue();
        assertEquals(businessId, config.getBusinessId());
        assertEquals("PILOT_01", config.getCredentialRef());
        assertEquals(WABA_ID, config.getWabaId());
        assertTrue(config.isEnabled());
    }

    @Test
    void doesNothingWhenDisabled() {
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        MetaWhatsAppTenantConfigRepository configs = mock(MetaWhatsAppTenantConfigRepository.class);

        new MetaWhatsAppTestBootstrapRunner(
                false,
                ANCHOR,
                PHONE_NUMBER_ID,
                WABA_ID,
                "PILOT_01",
                phones,
                configs).run(null);

        verifyNoInteractions(phones, configs);
    }

    @Test
    void rejectsInvalidPhoneNumberIdBeforeDatabaseAccess() {
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        MetaWhatsAppTenantConfigRepository configs = mock(MetaWhatsAppTenantConfigRepository.class);

        MetaWhatsAppTestBootstrapRunner runner = new MetaWhatsAppTestBootstrapRunner(
                true,
                ANCHOR,
                "bad-id",
                WABA_ID,
                "PILOT_01",
                phones,
                configs);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> runner.run(null));
        assertEquals("HELVOCA_META_WHATSAPP_TEST_PHONE_NUMBER_ID is invalid", error.getMessage());
        verifyNoInteractions(phones, configs);
    }
}
