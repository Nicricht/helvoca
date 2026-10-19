package cl.helvoca.telephony.twilio;

import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TwilioCertificationCommandCallLauncherTest {
    private static final String TOKEN = "11111111-1111-1111-1111-111111111111";
    private static final String CALL_SID = "CA0123456789abcdef0123456789abcdef";

    @Test
    void validConfigurationRequiresExactAllowedTarget() {
        TwilioCertificationCommandCallLauncher launcher = launcher(
                properties(), "+56966939611", "+56966939611", "+56975856664", mock(HttpClient.class));

        assertTrue(launcher.validConfiguration());
    }

    @Test
    void forbiddenTargetFailsClosed() {
        TwilioCertificationCommandCallLauncher launcher = launcher(
                properties(), "+56975856664", "+56975856664", "+56975856664", mock(HttpClient.class));

        assertFalse(launcher.validConfiguration());
    }

    @Test
    void missingProviderCredentialsFailClosed() {
        TwilioProperties properties = new TwilioProperties();
        properties.setPublicBaseUrl("https://recepvoz.example");
        TwilioCertificationCommandCallLauncher launcher = launcher(
                properties, "+56966939611", "+56966939611", "+56975856664", mock(HttpClient.class));

        assertFalse(launcher.validConfiguration());
    }

    @Test
    void certificationTokenIsPartOfSignedPathNotQueryString() {
        TwilioCertificationCommandCallLauncher launcher = launcher(
                properties(), "+56966939611", "+56966939611", "+56975856664", mock(HttpClient.class));

        String url = launcher.certificationVoiceUrl(TOKEN);

        assertEquals(
                "https://recepvoz.example/webhooks/v1/twilio/inbound-certification/" + TOKEN,
                url);
        assertFalse(url.contains("?token="));
    }

    @Test
    void validTokenCreatesCallThroughInjectedHttpClient() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(201);
        when(response.body()).thenReturn("{\"sid\":\"" + CALL_SID + "\"}");
        when(http.send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(response);
        TwilioCertificationCommandCallLauncher launcher = launcher(
                properties(), "+56966939611", "+56966939611", "+56975856664", http);

        assertEquals(CALL_SID, launcher.launch(TOKEN));

        verify(http).send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    }

    @Test
    void providerHttpFailureIsRejected() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(503);
        when(http.send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(response);
        TwilioCertificationCommandCallLauncher launcher = launcher(
                properties(), "+56966939611", "+56966939611", "+56975856664", http);

        assertThrows(IllegalStateException.class, () -> launcher.launch(TOKEN));
    }

    @Test
    void providerResponseWithoutValidCallSidIsRejected() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(201);
        when(response.body()).thenReturn("{}");
        when(http.send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(response);
        TwilioCertificationCommandCallLauncher launcher = launcher(
                properties(), "+56966939611", "+56966939611", "+56975856664", http);

        assertThrows(IllegalStateException.class, () -> launcher.launch(TOKEN));
    }

    @Test
    void invalidCallbackTokenFailsBeforeProviderCall() {
        HttpClient http = mock(HttpClient.class);
        TwilioCertificationCommandCallLauncher launcher = launcher(
                properties(), "+56966939611", "+56966939611", "+56975856664", http);

        assertThrows(IllegalArgumentException.class, () -> launcher.launch("bad-token"));
        verifyNoInteractions(http);
    }


    @Test
    void providerEnforcesConfiguredCallDurationSeparatelyFromRingingTimeout() throws Exception {
        assertProviderTimeLimit(75, 75);
        assertProviderTimeLimit(10, 20);
        assertProviderTimeLimit(300, 180);
    }

    private static void assertProviderTimeLimit(int configuredSeconds, int expectedSeconds) throws Exception {
        AtomicReference<HttpRequest> observedRequest = new AtomicReference<>();
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(201);
        when(response.body()).thenReturn("{\"sid\":\"" + CALL_SID + "\"}");
        when(http.send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenAnswer(invocation -> {
                    observedRequest.set(invocation.getArgument(0));
                    return response;
                });

        TwilioCertificationCommandCallLauncher launcher = new TwilioCertificationCommandCallLauncher(
                properties(), "+14355652512", "+56966939611", "+56966939611",
                "+56975856664", configuredSeconds, mock(TwilioCallControl.class), http);

        assertEquals(expectedSeconds <= 75, launcher.durationWithinDemoLimit());

        assertEquals(CALL_SID, launcher.launch(TOKEN));
        HttpRequest request = observedRequest.get();
        assertNotNull(request);
        assertEquals("POST", request.method());
        String body = publishedBody(request);
        assertTrue(Arrays.asList(body.split("&")).contains("TimeLimit=" + expectedSeconds),
                "Twilio must independently enforce the bounded conversation duration");
        assertTrue(Arrays.asList(body.split("&")).contains("Timeout=25"),
                "Ringing timeout is not a conversation time limit");
    }

    private static String publishedBody(HttpRequest request) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CompletableFuture<Void> finished = new CompletableFuture<>();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
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
            public void onError(Throwable error) {
                finished.completeExceptionally(error);
            }

            @Override
            public void onComplete() {
                finished.complete(null);
            }
        });
        finished.get(5, TimeUnit.SECONDS);
        return output.toString(StandardCharsets.UTF_8);
    }

    private static TwilioCertificationCommandCallLauncher launcher(
            TwilioProperties properties,
            String to,
            String allowedTo,
            String forbiddenTo,
            HttpClient http) {
        return new TwilioCertificationCommandCallLauncher(
                properties,
                "+14355652512",
                to,
                allowedTo,
                forbiddenTo,
                150,
                mock(TwilioCallControl.class),
                http);
    }

    private static TwilioProperties properties() {
        TwilioProperties properties = new TwilioProperties();
        properties.setAccountSid("test-account");
        properties.setAuthToken("test-secret");
        properties.setPublicBaseUrl("https://recepvoz.example");
        return properties;
    }
}
