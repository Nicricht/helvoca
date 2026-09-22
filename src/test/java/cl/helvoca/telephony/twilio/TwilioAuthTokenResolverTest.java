package cl.helvoca.telephony.twilio;

import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TwilioAuthTokenResolverTest {
    private static final String PARENT_SID = "ACaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String SUBACCOUNT_SID = "ACbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @Test
    void returnsParentTokenWithoutNetworkCall() {
        TwilioProperties properties = new TwilioProperties();
        properties.setAccountSid(PARENT_SID);
        properties.setAuthToken("parent-token");
        HttpClient http = mock(HttpClient.class);

        TwilioAuthTokenResolver resolver = new TwilioAuthTokenResolver(
                properties,
                SUBACCOUNT_SID,
                http);

        assertEquals("parent-token", resolver.resolve(PARENT_SID));
        assertEquals("parent-token", resolver.resolve(""));
        verifyNoInteractions(http);
    }

    @Test
    void fetchesAndCachesConfiguredSubaccountTokenUsingParentCredentials() throws Exception {
        TwilioProperties properties = new TwilioProperties();
        properties.setAccountSid(PARENT_SID);
        properties.setAuthToken("parent-token");

        HttpClient http = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {
                  "sid":"ACbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                  "owner_account_sid":"ACaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                  "auth_token":"sub-token"
                }
                """);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        TwilioAuthTokenResolver resolver = new TwilioAuthTokenResolver(
                properties,
                SUBACCOUNT_SID,
                http);

        assertEquals("sub-token", resolver.resolve(SUBACCOUNT_SID));
        assertEquals("sub-token", resolver.resolve(SUBACCOUNT_SID));
        verify(http, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void refusesUnknownSubaccountWithoutNetworkCall() {
        TwilioProperties properties = new TwilioProperties();
        properties.setAccountSid(PARENT_SID);
        properties.setAuthToken("parent-token");
        HttpClient http = mock(HttpClient.class);

        TwilioAuthTokenResolver resolver = new TwilioAuthTokenResolver(
                properties,
                SUBACCOUNT_SID,
                http);

        assertEquals("", resolver.resolve("ACcccccccccccccccccccccccccccccccc"));
        verifyNoInteractions(http);
    }
}
