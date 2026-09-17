package cl.helvoca.messaging.outbound;

import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.phone.PhoneNumberRepository;
import cl.helvoca.telephony.twilio.TwilioProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpClient;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TwilioWhatsAppMessagingProviderTest {
    @Mock PhoneNumberRepository phones;
    @Mock HttpClient http;

    @Test
    void refusesDeliveryWhenTenantHasNoExplicitWhatsappSender() {
        UUID businessId = UUID.randomUUID();
        when(phones.findAllByBusinessIdAndActiveTrueAndWhatsappEnabledTrueOrderByCreatedAtDesc(businessId))
                .thenReturn(List.of());
        TwilioWhatsAppMessagingProvider provider = provider();

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> provider.send(command(businessId)));

        assertTrue(error.getMessage().contains("no WhatsApp-enabled sender"));
        verifyNoInteractions(http);
    }

    @Test
    void refusesDeliveryWhenTenantSenderConfigurationIsAmbiguous() {
        UUID businessId = UUID.randomUUID();
        PhoneNumber first = new PhoneNumber();
        first.setPhoneNumber("+56911111111");
        first.setBusinessId(businessId);
        first.setWhatsappEnabled(true);
        PhoneNumber second = new PhoneNumber();
        second.setPhoneNumber("+56922222222");
        second.setBusinessId(businessId);
        second.setWhatsappEnabled(true);
        when(phones.findAllByBusinessIdAndActiveTrueAndWhatsappEnabledTrueOrderByCreatedAtDesc(businessId))
                .thenReturn(List.of(first, second));
        TwilioWhatsAppMessagingProvider provider = provider();

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> provider.send(command(businessId)));

        assertTrue(error.getMessage().contains("multiple WhatsApp-enabled senders"));
        verifyNoInteractions(http);
    }

    private TwilioWhatsAppMessagingProvider provider() {
        TwilioProperties twilio = new TwilioProperties();
        twilio.setAccountSid("ACaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        twilio.setAuthToken("test-token");
        return new TwilioWhatsAppMessagingProvider(twilio, phones, http);
    }

    private MessagingProvider.SendCommand command(UUID businessId) {
        return new MessagingProvider.SendCommand(
                businessId,
                UUID.randomUUID(),
                OutboundMessage.Channel.WHATSAPP,
                "+56933333333",
                "Mensaje seguro",
                "test-key");
    }
}
