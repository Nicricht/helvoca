package cl.helvoca.telephony.twilio;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TwilioCertificationCallStatusReadOnlyRunnerTest {
    private static final String ACCOUNT_SID = "AC" + "0123456789abcdef".repeat(2);
    private static final String CALL_SID = "CA" + "0123456789abcdef".repeat(2);
    private static final String OTHER_CALL_SID = "CA" + "fedcba9876543210".repeat(2);

    @Test
    void performsOnlyAuthenticatedGetForExactExistingCallSidAndMapsAllFields() throws Exception {
        TwilioProperties properties = properties();
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> response = response(200, """
                {"sid":"%s","account_sid":"%s",
                 "status":"no-answer","duration":"0","price":"-0.00750",
                 "price_unit":"USD","error_code":"30005","direction":"outbound-api",
                 "answered_by":"human","from":"%s","to":"%s",
                 "start_time":"2026-11-01T11:28:30Z","end_time":"2026-11-01T11:28:55Z",
                 "date_updated":"2026-11-01T11:28:55Z"}
                """.formatted(CALL_SID, ACCOUNT_SID, "+1" + "6812706780", "+56" + "966939611"));
        AtomicReference<HttpRequest> captured = new AtomicReference<>();
        when(http.send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenAnswer(invocation -> {
                    captured.set(invocation.getArgument(0));
                    return response;
                });

        var result = runner(true, CALL_SID, properties, http).lookup(CALL_SID);

        HttpRequest request = captured.get();
        assertNotNull(request);
        assertEquals("GET", request.method());
        assertEquals("https://api.twilio.com/2010-04-01/Accounts/" + ACCOUNT_SID
                + "/Calls/" + CALL_SID + ".json", request.uri().toString());
        assertEquals("Basic " + Base64.getEncoder().encodeToString(
                (ACCOUNT_SID + ":test-secret").getBytes(StandardCharsets.UTF_8)),
                request.headers().firstValue("Authorization").orElseThrow());
        assertEquals("no-answer", result.status());
        assertEquals("0", result.duration());
        assertEquals("-0.00750", result.price());
        assertEquals("USD", result.priceUnit());
        assertEquals("30005", result.errorCode());
        assertEquals("outbound-api", result.direction());
        assertEquals("human", result.answeredBy());
        assertEquals("…6780", result.fromSuffix());
        assertEquals("…9611", result.toSuffix());
        assertEquals("2026-11-01T11:28:30Z", result.startTime());
        assertEquals("2026-11-01T11:28:55Z", result.endTime());
        assertEquals("2026-11-01T11:28:55Z", result.dateUpdated());
        verify(http, times(1)).send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    }

    @Test
    void handlesNullErrorCodeAndBlankOrShortNumbers() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> providerResponse = response(200, """
                {"sid":"%s","account_sid":"%s","status":"failed",
                 "error_code":null,"from":"","to":"abc"}
                """.formatted(CALL_SID, ACCOUNT_SID));
        when(http.send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(providerResponse);

        var result = runner(true, CALL_SID, properties(), http).lookup(CALL_SID);

        assertEquals("", result.errorCode());
        assertEquals("****", result.fromSuffix());
        assertEquals("****", result.toSuffix());
    }

    @Test
    void rejectsMalformedOrMissingCallSidBeforeAnyHttpRequest() {
        HttpClient http = mock(HttpClient.class);
        var runner = runner(true, CALL_SID, properties(), http);

        assertThrows(IllegalArgumentException.class, () -> runner.lookup(null));
        assertThrows(IllegalArgumentException.class, () -> runner.lookup("CA-invalid"));
        verifyNoInteractions(http);
    }

    @Test
    void rejectsMissingOrMalformedTwilioCredentialsBeforeAnyHttpRequest() {
        HttpClient http = mock(HttpClient.class);
        TwilioProperties missingAccount = new TwilioProperties();
        missingAccount.setAuthToken("test-secret");
        assertThrows(IllegalStateException.class,
                () -> runner(true, CALL_SID, missingAccount, http).lookup(CALL_SID));

        TwilioProperties malformedAccount = properties();
        malformedAccount.setAccountSid("not-an-account-sid");
        assertThrows(IllegalStateException.class,
                () -> runner(true, CALL_SID, malformedAccount, http).lookup(CALL_SID));

        TwilioProperties missingToken = properties();
        missingToken.setAuthToken("");
        assertThrows(IllegalStateException.class,
                () -> runner(true, CALL_SID, missingToken, http).lookup(CALL_SID));
        verifyNoInteractions(http);
    }

    @Test
    void rejectsEveryNonSuccessfulProviderResponse() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> response199 = response(199, "{}");
        HttpResponse<String> response503 = response(503, "{}");
        when(http.send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(response199, response503);
        var runner = runner(true, CALL_SID, properties(), http);
        assertThrows(IllegalStateException.class, () -> runner.lookup(CALL_SID));
        assertThrows(IllegalStateException.class, () -> runner.lookup(CALL_SID));
    }

    @Test
    void rejectsCallSidOrAccountIdentityMismatch() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> wrongCallSid = response(200, """
                {"sid":"%s","account_sid":"%s","status":"no-answer"}
                """.formatted(OTHER_CALL_SID, ACCOUNT_SID));
        HttpResponse<String> wrongAccountSid = response(200, """
                {"sid":"%s","account_sid":"AC%s","status":"no-answer"}
                """.formatted(CALL_SID, "f".repeat(32)));
        when(http.send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(wrongCallSid, wrongAccountSid);
        var runner = runner(true, CALL_SID, properties(), http);
        assertThrows(IllegalStateException.class, () -> runner.lookup(CALL_SID));
        assertThrows(IllegalStateException.class, () -> runner.lookup(CALL_SID));
    }

    @Test
    void startupRunnerIsDisabledByDefaultAndReturnsForMissingSid() {
        TwilioProperties properties = properties();
        var publicConstructor = new TwilioCertificationCallStatusReadOnlyRunner(false, CALL_SID, properties);
        publicConstructor.run(null);

        var nullSid = runner(true, null, properties, mock(HttpClient.class));
        nullSid.run(null);
        var blankSid = runner(true, " ", properties, mock(HttpClient.class));
        blankSid.run(null);
    }

    @Test
    void startupRunnerPerformsOneReadOnlyGetAtMostOnce() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> providerResponse = response(200, """
                {"sid":"%s","account_sid":"%s","status":"completed"}
                """.formatted(CALL_SID, ACCOUNT_SID));
        when(http.send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(providerResponse);
        var runner = runner(true, CALL_SID, properties(), http);

        runner.run(null);
        runner.run(null);

        verify(http, times(1)).send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    }

    @Test
    void startupRunnerContainsProviderErrorsAndRestoresInterruptFlag() throws Exception {
        HttpClient failedHttp = mock(HttpClient.class);
        when(failedHttp.send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenThrow(new IOException("provider unavailable"));
        runner(true, CALL_SID, properties(), failedHttp).run(null);

        HttpClient interruptedHttp = mock(HttpClient.class);
        when(interruptedHttp.send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenThrow(new InterruptedException("interrupted"));
        runner(true, CALL_SID, properties(), interruptedHttp).run(null);
        assertTrue(Thread.currentThread().isInterrupted());
        Thread.interrupted();
    }

    @Test
    void startupRunnerContainsMalformedProviderResponseWithoutRetrying() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> malformedResponse = response(200, "not-json");
        when(http.send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(malformedResponse);
        var runner = runner(true, CALL_SID, properties(), http);

        assertDoesNotThrow(() -> runner.run(null));
        runner.run(null);

        verify(http, times(1)).send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    }

    @Test
    void masksNullBlankAndShortSuffixInputs() throws Exception {
        Method suffix = TwilioCertificationCallStatusReadOnlyRunner.class
                .getDeclaredMethod("suffix", String.class);
        suffix.setAccessible(true);
        assertEquals("", suffix.invoke(null, new Object[]{null}));
        assertEquals("", suffix.invoke(null, ""));
        assertEquals("****", suffix.invoke(null, "12"));
        assertEquals("…6780", suffix.invoke(null, "+16812706780"));
    }

    private static TwilioCertificationCallStatusReadOnlyRunner runner(
            boolean enabled, String sid, TwilioProperties properties, HttpClient http) {
        return new TwilioCertificationCallStatusReadOnlyRunner(enabled, sid, properties, http);
    }

    private static TwilioProperties properties() {
        TwilioProperties properties = new TwilioProperties();
        properties.setAccountSid(ACCOUNT_SID);
        properties.setAuthToken("test-secret");
        return properties;
    }

    private static HttpResponse<String> response(int status, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        return response;
    }
}
