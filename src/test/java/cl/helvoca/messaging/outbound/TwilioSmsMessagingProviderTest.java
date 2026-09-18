package cl.helvoca.messaging.outbound;

import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.phone.PhoneNumberRepository;
import cl.helvoca.telephony.twilio.TwilioProperties;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TwilioSmsMessagingProviderTest {
    @Test
    void sendsSmsThroughTenantTwilioNumber() throws Exception {
        UUID businessId = UUID.randomUUID();

        PhoneNumber sender = new PhoneNumber();
        sender.setBusinessId(businessId);
        sender.setProvider("TWILIO");
        sender.setPhoneNumber("+14355652512");
        sender.setActive(true);

        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        when(phones.findAllByBusinessIdOrderByCreatedAtDesc(businessId)).thenReturn(List.of(sender));

        HttpClient http = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(201);
        when(response.body()).thenReturn("{\"sid\":\"SM0123456789abcdef0123456789abcdef\"}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        TwilioProperties twilio = new TwilioProperties();
        twilio.setAccountSid("ACaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        twilio.setAuthToken("test-token");

        TwilioSmsMessagingProvider provider = new TwilioSmsMessagingProvider(twilio, phones, http);
        MessagingProvider.SendResult result = provider.send(new MessagingProvider.SendCommand(
                businessId,
                UUID.randomUUID(),
                OutboundMessage.Channel.SMS,
                "+56966939611",
                "Reserva confirmada",
                "sms-test"));

        assertEquals("SM0123456789abcdef0123456789abcdef", result.providerMessageId());

        var captor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        assertEquals(Map.of(
                "To", "+56966939611",
                "From", "+14355652512",
                "Body", "Reserva confirmada"), parseForm(bodyOf(captor.getValue())));
    }

    private static Map<String, String> parseForm(String body) {
        return Arrays.stream(body.split("&"))
                .map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(
                        pair -> URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                        pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)));
    }

    private static String bodyOf(HttpRequest request) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CompletableFuture<String> body = new CompletableFuture<>();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
            @Override public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            @Override public void onNext(ByteBuffer item) {
                byte[] bytes = new byte[item.remaining()];
                item.get(bytes);
                output.writeBytes(bytes);
            }
            @Override public void onError(Throwable throwable) { body.completeExceptionally(throwable); }
            @Override public void onComplete() { body.complete(output.toString(StandardCharsets.UTF_8)); }
        });
        return body.join();
    }
}
