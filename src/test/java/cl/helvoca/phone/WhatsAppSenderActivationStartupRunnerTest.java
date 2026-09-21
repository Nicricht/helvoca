package cl.helvoca.phone;

import cl.helvoca.audit.AuditService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WhatsAppSenderActivationStartupRunnerTest {
    @Test
    void activatesConfiguredExistingPhoneOnce() throws Exception {
        UUID businessId = UUID.randomUUID();
        PhoneNumber phone = new PhoneNumber();
        phone.setBusinessId(businessId);
        phone.setPhoneNumber("+14355652512");
        phone.setActive(true);
        phone.setWhatsappEnabled(false);

        PhoneNumberRepository repository = mock(PhoneNumberRepository.class);
        AuditService audit = mock(AuditService.class);
        when(repository.findByPhoneNumber("+14355652512")).thenReturn(Optional.of(phone));
        when(repository.findAllByBusinessIdAndActiveTrueAndWhatsappEnabledTrueOrderByCreatedAtDesc(businessId))
                .thenReturn(List.of());

        WhatsAppSenderActivationStartupRunner runner =
                new WhatsAppSenderActivationStartupRunner(true, "+14355652512", repository, audit);

        runner.run(null);

        assertTrue(phone.isWhatsappEnabled());
        verify(repository).save(phone);
        verify(audit).success(
                businessId,
                "TWILIO_WHATSAPP_SENDER_ACTIVATED_ON_STARTUP",
                "WHATSAPP_SENDER",
                businessId);
    }

    @Test
    void doesNothingWhenDisabled() throws Exception {
        PhoneNumberRepository repository = mock(PhoneNumberRepository.class);
        WhatsAppSenderActivationStartupRunner runner =
                new WhatsAppSenderActivationStartupRunner(false, "+14355652512", repository);

        runner.run(null);

        verifyNoInteractions(repository);
    }

    @Test
    void refusesMetaWhatsappSender() {
        PhoneNumber phone = new PhoneNumber();
        phone.setBusinessId(UUID.randomUUID());
        phone.setPhoneNumber("+14355652512");
        phone.setActive(true);
        phone.setWhatsappProvider("META_WHATSAPP_CLOUD");

        PhoneNumberRepository repository = mock(PhoneNumberRepository.class);
        when(repository.findByPhoneNumber("+14355652512")).thenReturn(Optional.of(phone));

        WhatsAppSenderActivationStartupRunner runner =
                new WhatsAppSenderActivationStartupRunner(true, "+14355652512", repository);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> runner.run(null));

        assertTrue(error.getMessage().contains("TWILIO_WHATSAPP"));
        verify(repository, never()).save(any());
        verify(repository, never()).findAllByBusinessIdAndActiveTrueAndWhatsappEnabledTrueOrderByCreatedAtDesc(any());
    }

    @Test
    void refusesInactivePhone() {
        PhoneNumber phone = new PhoneNumber();
        phone.setBusinessId(UUID.randomUUID());
        phone.setPhoneNumber("+14355652512");
        phone.setActive(false);

        PhoneNumberRepository repository = mock(PhoneNumberRepository.class);
        when(repository.findByPhoneNumber("+14355652512")).thenReturn(Optional.of(phone));

        WhatsAppSenderActivationStartupRunner runner =
                new WhatsAppSenderActivationStartupRunner(true, "+14355652512", repository);

        assertThrows(IllegalStateException.class, () -> runner.run(null));
        verify(repository, never()).save(any());
    }
}
