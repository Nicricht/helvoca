package cl.helvoca.telephony.twilio;

import org.junit.jupiter.api.Test;

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

    @Test
    void performsOnlyAuthenticatedGetForExactExistingCallSid() throws Exception {
        TwilioProperties properties = properties();
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        AtomicReference<HttpRequest> captured = new AtomicReference<>();
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {"sid":"%s","account_sid":"%s",
                 "status":"no-answer","duration":"0","price":"-0.00750",
                 "price_unit":"USD","error_code":"30005","direction":"outbound-api",
                 "answered_by":null,"from":"%s","to":"%s",
                 "start_time":null,"end_time":"2026-11-01T11:28:55Z",
                 "date_updated":"2026-11-01T11:28:55Z"}
                """.formatted(CALL_SID, ACCOUNT_SID, "+1" + "6812706780", "+56" + "966939611"));
        when(http.send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenAnswer(invocation -> {
                    captured.set(invocation.getArgument(0));
                    return response;
                });

        TwilioCertificationCallStatusReadOnlyRunner runner =
                new TwilioCertificationCallStatusReadOnlyRunner(true, CALL_SID, properties, http);

        var result = runner.lookup(CALL_SID);

        HttpRequest request = captured.get();
        assertNotNull(request);
        assertEquals("GET", request.method());
        assertEquals("https://api.twilio.com/2010-04-01/Accounts/" + ACCOUNT_SID
                + "/Calls/" + CALL_SID + ".json", request.uri().toString());
        assertEquals("Basic " + Base64.getEncoder().encodeToString(
                (ACCOUNT_SID + ":test-secret").getBytes(StandardCharsets.UTF_8)),
                request.headers().firstValue("Authorization").orElseThrow());
        assertEquals("no-answer", result.status());
        assertEquals("30005", result.errorCode());
        assertEquals("-0.00750", result.price());
        assertEquals("USD", result.priceUnit());
        assertEquals("…6780", result.fromSuffix());
        assertEquals("…9611", result.toSuffix());
        verify(http, times(1)).send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    }

    @Test
    void rejectsResponseFromAnotherAccount() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {"sid":"%s","account_sid":"AC%s","status":"no-answer"}
                """.formatted(CALL_SID, "f".repeat(32)));
        when(http.send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(response);

        var runner = new TwilioCertificationCallStatusReadOnlyRunner(true, CALL_SID, properties(), http);
        assertThrows(IllegalStateException.class, () -> runner.lookup(CALL_SID));
    }

    @Test
    void rejectsMalformedCallSidBeforeAnyHttpRequest() {
        HttpClient http = mock(HttpClient.class);
        var runner = new TwilioCertificationCallStatusReadOnlyRunner(true, "CA-invalid", properties(), http);

        assertThrows(IllegalArgumentException.class, () -> runner.lookup("CA-invalid"));
        verifyNoInteractions(http);
    }

    private static TwilioProperties properties() {
        TwilioProperties properties = new TwilioProperties();
        properties.setAccountSid(ACCOUNT_SID);
        properties.setAuthToken("test-secret");
        return properties;
    }
}
