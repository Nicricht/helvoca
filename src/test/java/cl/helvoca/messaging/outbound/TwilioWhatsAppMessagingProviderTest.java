package cl.helvoca.messaging.outbound;

import cl.helvoca.audit.AuditService;
import cl.helvoca.messaging.WhatsAppProperties;
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
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TwilioWhatsAppMessagingProviderTest {
    @Mock PhoneNumberRepository phones;
    @Mock HttpClient http;
    @Mock AuditService audit;

    @Test
    void sendsExpectedTwilioRequestAndReturnsProviderSid() throws Exception {
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

        MessagingProvider.SendResult result = provider().send(command(businessId));

        assertEquals("SM0123456789abcdef0123456789abcdef", result.providerMessageId());
        assertNotNull(sender.getWhatsappCertifiedAt());
        verify(phones).save(sender);
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(1)).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest request = requestCaptor.getValue();
        assertEquals("POST", request.method());
        assertEquals(
                "https://api.twilio.com/2010-04-01/Accounts/ACaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/Messages.json",
                request.uri().toString());
        assertEquals("application/x-www-form-urlencoded",
                request.headers().firstValue("Content-Type").orElseThrow());
        assertEquals("Basic " + Base64.getEncoder().encodeToString(
                        "ACaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa:test-token".getBytes(StandardCharsets.UTF_8)),
                request.headers().firstValue("Authorization").orElseThrow());
        assertEquals(Map.of(
                "To", "whatsapp:+56933333333",
                "From", "whatsapp:+56922222222",
                "Body", "Mensaje seguro",
                "StatusCallback", "https://helvoca.example/webhooks/v1/twilio/whatsapp-status"),
                parseForm(bodyOf(request)));
    }

    @Test
    void firstProductionSendAuditsTwilioCertification() throws Exception {
        UUID businessId = UUID.randomUUID();
        UUID phoneId = UUID.randomUUID();
        PhoneNumber sender = mock(PhoneNumber.class);
        when(sender.getPhoneNumber()).thenReturn("+56922222222");
        when(sender.getWhatsappCertifiedAt()).thenReturn(null);
        when(sender.getId()).thenReturn(phoneId);
        when(phones.findAllByBusinessIdAndActiveTrueAndWhatsappEnabledTrueOrderByCreatedAtDesc(businessId))
                .thenReturn(List.of(sender));

        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(201);
        when(response.body()).thenReturn("{\"sid\":\"SMaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        providerWithAudit().send(command(businessId));

        verify(sender).setWhatsappCertifiedAt(any(Instant.class));
        verify(phones).save(sender);
        verify(audit).success(
                businessId,
                "TWILIO_WHATSAPP_CERTIFICATION_COMPLETED",
                "WHATSAPP_SENDER",
                phoneId);
    }

    @Test
    void alreadyCertifiedProductionSenderDoesNotRewriteOrAuditCertification() throws Exception {
        UUID businessId = UUID.randomUUID();
        PhoneNumber sender = mock(PhoneNumber.class);
        when(sender.getPhoneNumber()).thenReturn("+56922222222");
        when(sender.getWhatsappCertifiedAt()).thenReturn(Instant.parse("2026-09-20T00:00:00Z"));
        when(phones.findAllByBusinessIdAndActiveTrueAndWhatsappEnabledTrueOrderByCreatedAtDesc(businessId))
                .thenReturn(List.of(sender));

        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(201);
        when(response.body()).thenReturn("{\"sid\":\"SMbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\"}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        providerWithAudit().send(command(businessId));

        verify(sender, never()).setWhatsappCertifiedAt(any());
        verify(phones, never()).save(sender);
        verifyNoInteractions(audit);
    }

    @Test
    void sendsThroughSandboxWithoutCertifyingTenantSender() throws Exception {
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
        when(response.body()).thenReturn("{\"sid\":\"SMfedcba9876543210fedcba9876543210\"}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        WhatsAppProperties whatsApp = new WhatsAppProperties();
        whatsApp.setSandboxEnabled(true);
        whatsApp.setSandboxNumber("+14155238886");

        MessagingProvider.SendResult result = provider(whatsApp).send(command(businessId));

        assertEquals("SMfedcba9876543210fedcba9876543210", result.providerMessageId());
        assertNull(sender.getWhatsappCertifiedAt());
        verify(phones, never()).save(sender);

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        assertEquals("whatsapp:+14155238886", parseForm(bodyOf(requestCaptor.getValue())).get("From"));
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
        return provider(new WhatsAppProperties());
    }

    private TwilioWhatsAppMessagingProvider provider(WhatsAppProperties whatsApp) {
        TwilioProperties twilio = new TwilioProperties();
        twilio.setAccountSid("ACaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        twilio.setAuthToken("test-token");
        twilio.setPublicBaseUrl("https://helvoca.example");
        return new TwilioWhatsAppMessagingProvider(twilio, phones, whatsApp, http);
    }

    private TwilioWhatsAppMessagingProvider providerWithAudit() {
        TwilioProperties twilio = new TwilioProperties();
        twilio.setAccountSid("ACaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        twilio.setAuthToken("test-token");
        twilio.setPublicBaseUrl("https://helvoca.example");
        return new TwilioWhatsAppMessagingProvider(twilio, phones, new WhatsAppProperties(), http, audit);
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
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
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
