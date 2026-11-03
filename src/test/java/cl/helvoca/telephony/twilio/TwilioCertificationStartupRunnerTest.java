package cl.helvoca.telephony.twilio;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TwilioCertificationStartupRunnerTest {
    private static final String ACCOUNT_SID = "AC-test-account";
    private static final String AUTH_TOKEN = "test-auth-token";
    private static final String FROM = "+15551234567";
    private static final String TO = "+15557654321";
    private static final String CALL_SID = "CA-test-call";
    private static final String BASE_URL = "https://aurora.example///";

    @Test
    void certificationCallbackBaseUrlStripsAllAndOnlyTrailingSlashes() {
        assertEquals("https://example.test",
                TwilioCertificationStartupRunner.trimTrailingSlash("https://example.test"));
        assertEquals("https://example.test",
                TwilioCertificationStartupRunner.trimTrailingSlash("https://example.test/"));
        assertEquals("https://example.test",
                TwilioCertificationStartupRunner.trimTrailingSlash("https://example.test////"));
        assertEquals("https://example.test/sub/path",
                TwilioCertificationStartupRunner.trimTrailingSlash("https://example.test/sub/path//"));
        assertEquals("",
                TwilioCertificationStartupRunner.trimTrailingSlash("/"));
        assertEquals("",
                TwilioCertificationStartupRunner.trimTrailingSlash(""));
    }

    @Test
    void twilioCertificationFormEncodingProtectsPhoneAndCallbackParameters() {
        assertEquals("To=%2B56912345678",
                TwilioCertificationStartupRunner.form("To", "+56912345678"));
        assertEquals("StatusCallbackEvent=call+answered",
                TwilioCertificationStartupRunner.form("StatusCallbackEvent", "call answered"));
        assertEquals("a%26b=x%3D1%26y%3D%2B",
                TwilioCertificationStartupRunner.form("a&b", "x=1&y=+"));
        assertEquals("voice=Caf%C3%A9",
                TwilioCertificationStartupRunner.form("voice", "Café"));
        assertEquals("empty=",
                TwilioCertificationStartupRunner.form("empty", ""));
    }

    @Test
    void startupRunnerSendsThreeMinuteProviderLimitSeparatelyFromRingingTimeout() {
        String body = TwilioCertificationStartupRunner.callBody(
                "+56912345678", "+15551234567",
                "https://example.test/voice", "https://example.test/status", 180);

        assertTrue(body.contains("Timeout=25"));
        assertTrue(body.contains("TimeLimit=180"));
    }

    @Test
    void createCallSendsAuthenticatedBoundedTwilioRequestAndReturnsCallSid() throws Exception {
        HttpClient http = mock(HttpClient.class);
        AtomicReference<HttpRequest> capturedRequest = new AtomicReference<>();
        stubProviderResponse(http, 201, "{\"sid\":\"" + CALL_SID + "\"}", capturedRequest);

        assertEquals(CALL_SID, invokeCreateCall(runnerWithHttp(http)));

        HttpRequest request = capturedRequest.get();
        assertNotNull(request);
        assertEquals(URI.create(
                "https://api.twilio.com/2010-04-01/Accounts/" + ACCOUNT_SID + "/Calls.json"),
                request.uri());
        assertEquals("POST", request.method());
        String expectedAuthorization = "Basic " + Base64.getEncoder().encodeToString(
                (ACCOUNT_SID + ":" + AUTH_TOKEN).getBytes(StandardCharsets.UTF_8));
        assertEquals(expectedAuthorization, request.headers().firstValue("Authorization").orElseThrow());
        assertEquals("application/x-www-form-urlencoded",
                request.headers().firstValue("Content-Type").orElseThrow());

        String body = publishedBody(request);
        assertTrue(body.contains("To=%2B15557654321"));
        assertTrue(body.contains("From=%2B15551234567"));
        assertTrue(body.contains(
                "Url=https%3A%2F%2Faurora.example%2Fwebhooks%2Fv1%2Ftwilio%2Foutbound-test"));
        assertTrue(body.contains(
                "StatusCallback=https%3A%2F%2Faurora.example%2Fwebhooks%2Fv1%2Ftwilio%2Fstatus"));
        assertTrue(body.contains("Timeout=25"));
        assertTrue(body.contains("TimeLimit=180"));
    }

    @Test
    void createCallUsesInboundCertificationWebhookForInboundDirection() throws Exception {
        HttpClient http = mock(HttpClient.class);
        AtomicReference<HttpRequest> capturedRequest = new AtomicReference<>();
        stubProviderResponse(http, 201, "{\"sid\":\"" + CALL_SID + "\"}", capturedRequest);

        assertEquals(CALL_SID, invokeCreateCall(runnerWithHttp(http, "inbound-certification")));

        String body = publishedBody(capturedRequest.get());
        assertTrue(body.contains(
                "Url=https%3A%2F%2Faurora.example%2Fwebhooks%2Fv1%2Ftwilio%2Finbound-certification"));
    }

    @Test
    void createCallRejectsNonSuccessfulProviderResponsesWithoutLeakingProviderBody() throws Exception {
        for (int status : new int[]{100, 503}) {
            HttpClient http = mock(HttpClient.class);
            stubProviderResponse(http, status,
                    "{\"message\":\"private-token-must-not-leak\"}", new AtomicReference<>());

            IllegalStateException error = assertThrows(
                    IllegalStateException.class,
                    () -> invokeCreateCall(runnerWithHttp(http)));

            assertTrue(error.getMessage().contains("http=" + status));
            assertTrue(error.getMessage().contains("[provider response omitted]"));
            assertFalse(error.getMessage().contains("private-token-must-not-leak"));
        }
    }

    @Test
    void createCallRejectsSuccessfulProviderResponseWithoutCallSid() throws Exception {
        HttpClient http = mock(HttpClient.class);
        stubProviderResponse(http, 201, "{}", new AtomicReference<>());

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> invokeCreateCall(runnerWithHttp(http)));

        assertEquals("Twilio create call returned no CallSid", error.getMessage());
    }

    @Test
    void maskedCertificationLogNumbersNeverDiscloseFullNumbers() {
        assertEquals("***", TwilioCertificationStartupRunner.mask(null));
        assertEquals("***", TwilioCertificationStartupRunner.mask(""));
        assertEquals("***", TwilioCertificationStartupRunner.mask("1234"));
        assertEquals("***5678", TwilioCertificationStartupRunner.mask("12345678"));
        assertEquals("***4321", TwilioCertificationStartupRunner.mask("+56912344321"));
    }

    @Test
    void twilioCreateCallErrorDiagnosticsNeverExposeProviderBody() {
        String sensitiveBody = "{\"message\":\"synthetic-caller@example.test token=private-token\"}";
        assertEquals("[provider response omitted]",
                TwilioCertificationStartupRunner.truncate(sensitiveBody));
        assertEquals("[provider response omitted]",
                TwilioCertificationStartupRunner.truncate("X".repeat(1000)));
        assertEquals("[provider response omitted]",
                TwilioCertificationStartupRunner.truncate(""));
        assertEquals("[provider response omitted]",
                TwilioCertificationStartupRunner.truncate(null));
    }

    @Test
    void normalizesInboundCertificationDirection() {
        assertEquals("inbound-certification",
                TwilioCertificationStartupRunner.normalizeDirection(" INBOUND-CERTIFICATION "));
    }

    @Test
    void fallsBackToOutboundTestForUnknownDirection() {
        assertEquals("outbound-test", TwilioCertificationStartupRunner.normalizeDirection("unexpected"));
        assertEquals("outbound-test", TwilioCertificationStartupRunner.normalizeDirection("inbound"));
        assertEquals("outbound-test", TwilioCertificationStartupRunner.normalizeDirection(null));
    }

    @Test
    void bakeOffVoiceMustBeCuratedAndOutboundOnly() {
        assertTrue(TwilioCertificationStartupRunner.validVoiceOverride("", "outbound-test"));
        assertTrue(TwilioCertificationStartupRunner.validVoiceOverride("Sadachbia", "outbound-test"));
        assertTrue(TwilioCertificationStartupRunner.validVoiceOverride(" leda ", "outbound-test"));
        assertFalse(TwilioCertificationStartupRunner.validVoiceOverride("Despina", "outbound-test"));
        assertFalse(TwilioCertificationStartupRunner.validVoiceOverride("Sadachbia", "inbound-certification"));
    }

    @Test
    void outboundTestAlwaysKeepsSafetyHangup() {
        assertTrue(TwilioCertificationStartupRunner.shouldScheduleSafetyHangup("outbound-test"));
        assertTrue(TwilioCertificationStartupRunner.shouldScheduleSafetyHangup("unexpected"));
    }

    @Test
    void inboundCertificationKeepsSafetyHangup() {
        assertTrue(TwilioCertificationStartupRunner.shouldScheduleSafetyHangup("inbound-certification"));
    }

    @Test
    void certificationTargetMustMatchExplicitAllowlist() {
        assertTrue(TwilioCertificationStartupRunner.isAllowedTarget("+56911111111", "+56911111111"));
        assertFalse(TwilioCertificationStartupRunner.isAllowedTarget("+56922222222", "+56911111111"));
        assertFalse(TwilioCertificationStartupRunner.isAllowedTarget("+56911111111", ""));
    }

    @Test
    void blocksExplicitlyForbiddenCertificationTarget() {
        assertTrue(TwilioCertificationStartupRunner.isForbiddenTarget("+56975856664", "+56975856664"));
        assertFalse(TwilioCertificationStartupRunner.isForbiddenTarget("+56911111111", "+56975856664"));
        assertFalse(TwilioCertificationStartupRunner.isForbiddenTarget("+56911111111", ""));
    }

    private static TwilioCertificationStartupRunner runnerWithHttp(HttpClient http) throws Exception {
        return runnerWithHttp(http, "outbound-test");
    }

    private static TwilioCertificationStartupRunner runnerWithHttp(HttpClient http, String direction) throws Exception {
        TwilioCertificationStartupRunner runner = new TwilioCertificationStartupRunner(
                false, ACCOUNT_SID, AUTH_TOKEN, FROM, TO, BASE_URL, TO, "+15550000000",
                180, direction, "", "", mock(TwilioCallControl.class));
        Field httpField = TwilioCertificationStartupRunner.class.getDeclaredField("http");
        httpField.setAccessible(true);
        httpField.set(runner, http);
        return runner;
    }

    private static String invokeCreateCall(TwilioCertificationStartupRunner runner) throws Exception {
        Method createCall = TwilioCertificationStartupRunner.class.getDeclaredMethod("createCall");
        createCall.setAccessible(true);
        try {
            return (String) createCall.invoke(runner);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception checked) throw checked;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException(cause);
        }
    }

    private static void stubProviderResponse(HttpClient http,
                                             int status,
                                             String body,
                                             AtomicReference<HttpRequest> capturedRequest) throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        when(http.send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenAnswer(invocation -> {
                    capturedRequest.set(invocation.getArgument(0));
                    return response;
                });
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
}
