package cl.helvoca.messaging.outbound;

import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.phone.PhoneNumberRepository;
import cl.helvoca.telephony.twilio.TwilioProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TwilioWhatsAppMessagingProviderTest {
    @Mock PhoneNumberRepository phones;
    @Mock HttpClient http;

    @Test
    void sendsWhatsappThroughTwilioWithTenantSenderAndReturnsProviderSid() throws Exception {
        UUID businessId = UUID.randomUUID();
        PhoneNumber sender = new PhoneNumber();
        sender.setPhoneNumber("+56911111111");
        sender.setBusinessId(businessId);
        sender.setWhatsappEnabled(true);
        when(phones.findAllByBusinessIdAndActiveTrueAndWhatsappEnabledTrueOrderByCreatedAtDesc(businessId))
                .thenReturn(List.of(sender));

        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(201);
        when(response.body()).thenReturn("{\"sid\":\"SM1234567890\"}");
        doReturn(response).when(http).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        TwilioWhatsAppMessagingProvider provider = provider();

        MessagingProvider.SendResult result = provider.send(command(businessId));

        assertEquals("SM1234567890", result.providerMessageId());
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest request = requestCaptor.getValue();

        assertEquals("POST", request.method());
        assertEquals(
                "https://api.twilio.com/2010-04-01/Accounts/ACaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/Messages.json",
                request.uri().toString());
        String expectedBasic = Base64.getEncoder().encodeToString(
                "ACaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa:test-token".getBytes(StandardCharsets.UTF_8));
        assertEquals("Basic " + expectedBasic, request.headers().firstValue("Authorization").orElseThrow());
        assertEquals("application/x-www-form-urlencoded",
                request.headers().firstValue("Content-Type").orElseThrow());
        assertEquals(
                "To=whatsapp%3A%2B56933333333&From=whatsapp%3A%2B56911111111&Body=Mensaje+seguro",
                requestBody(request));
    }

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

    private String requestBody(HttpRequest request) {
        HttpResponse.BodySubscriber<String> subscriber =
                HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8);
        request.bodyPublisher().orElseThrow().subscribe(subscriber);
        return subscriber.getBody().toCompletableFuture().join();
    }
}
