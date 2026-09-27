package cl.helvoca.telephony.twilio;

import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

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
