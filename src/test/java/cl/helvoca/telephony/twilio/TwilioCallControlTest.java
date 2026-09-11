package cl.helvoca.telephony.twilio;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TwilioCallControlTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void transferUpdatesActiveCallWithDialTwimlAndBasicAuth() throws Exception {
        String accountSid = "AC" + "a".repeat(32);
        String callSid = "CA" + "b".repeat(32);
        String authToken = "test-auth-token";
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/2010-04-01/Accounts/" + accountSid + "/Calls/" + callSid + ".json", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().close();
        });
        server.start();

        TwilioProperties properties = new TwilioProperties();
        properties.setAuthToken(authToken);
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        TwilioCallControl control = new TwilioCallControl(properties, HttpClient.newHttpClient(), base);

        assertTrue(control.transferToHuman(accountSid, callSid, "+56922222222"));

        String expectedAuth = "Basic " + Base64.getEncoder().encodeToString(
                (accountSid + ":" + authToken).getBytes(StandardCharsets.UTF_8));
        assertEquals(expectedAuth, authorization.get());
        assertNotNull(body.get());
        assertTrue(body.get().startsWith("Twiml="));
        String twiml = URLDecoder.decode(body.get().substring("Twiml=".length()), StandardCharsets.UTF_8);
        assertTrue(twiml.contains("<Dial timeout=\"20\" answerOnBridge=\"true\">"));
        assertTrue(twiml.contains("<Number>+56922222222</Number>"));
        assertTrue(twiml.contains("<Hangup/>"));
    }

    @Test
    void transferFailsClosedForInvalidDestinationBeforeCallingCarrier() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        TwilioProperties properties = new TwilioProperties();
        properties.setAuthToken("token");
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        TwilioCallControl control = new TwilioCallControl(properties, HttpClient.newHttpClient(), base);

        assertFalse(control.transferToHuman(
                "AC" + "a".repeat(32),
                "CA" + "b".repeat(32),
                "not-a-phone"));
    }
}
