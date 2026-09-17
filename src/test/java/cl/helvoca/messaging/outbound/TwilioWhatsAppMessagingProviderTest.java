package cl.helvoca.messaging.outbound;

import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.phone.PhoneNumberRepository;
import cl.helvoca.telephony.twilio.TwilioProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TwilioWhatsAppMessagingProviderTest {
    @Mock PhoneNumberRepository phones;
    @Mock HttpClient http;

    @Test
    void sendsWhatsappMessageToTwilioWithExpectedRequestAndReturnsProviderSid() throws Exception {
        UUID businessId = UUID.randomUUID();
        PhoneNumber sender = new PhoneNumber();
        sender.setPhoneNumber("+56922222222");
        sender.setBusinessId(businessId);
        sender.setWhatsappEnabled(true);
        when(phones.findAllByBusinessIdAndActiveTrueAndWhatsappEnabledTrueOrderByCreatedAtDesc(businessId))
                .thenReturn(List.of(sender));

        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(201);
        when(response.body()).thenReturn("{\"sid\":\"SM0123456789abcdef0123456789abcdef\"}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        TwilioWhatsAppMessagingProvider provider = provider();
        MessagingProvider.SendResult result = provider.send(command(businessId));

        assertEquals("SM0123456789abcdef0123456789abcdef", result.providerMessageId());

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest request = requestCaptor.getValue();

        assertEquals("POST", request.method());
        assertEquals(
                "https://api.twilio.com/2010-04-01/Accounts/ACaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/Messages.json",
                request.uri().toString());
        assertEquals("application/x-www-form-urlencoded",
                request.headers().firstValue("Content-Type").orElseThrow());
        String expectedBasic = Base64.getEncoder().encodeToString(
                "ACaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa:test-token".getBytes(StandardCharsets.UTF_8));
        assertEquals("Basic " + expectedBasic,
                request.headers().firstValue("Authorization").orElseThrow());

        String body = bodyOf(request);
        assertTrue(body.contains("To=whatsapp%3A%2B56933333333"));
        assertTrue(body.contains("From=whatsapp%3A%2B56922222222"));
        assertTrue(body.contains("Body=Mensaje+seguro"));
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

    private static String bodyOf(HttpRequest request) {
        HttpRequest.BodyPublisher publisher = request.bodyPublisher().orElseThrow();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CompletableFuture<String> body = new CompletableFuture<>();
        publisher.subscribe(new Flow.Subscriber<>() {
            private Flow.Subscription subscription;

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                this.subscription = subscription;
                this.subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                byte[] bytes = new byte[item.remaining()];
                item.get(bytes);
                output.writeBytes(bytes);
            }

            @Override
            public void onError(Throwable throwable) {
                body.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                body.complete(output.toString(StandardCharsets.UTF_8));
            }
        });
        return body.join();
    }
}
