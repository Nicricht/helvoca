package cl.helvoca.ai.live;

import cl.helvoca.ai.realtime.OpenAiRealtimeProperties;
import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.ai.realtime.RealtimeToolService;
import cl.helvoca.telephony.CallLifecycleService;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class OpenAiLiveAcceptRetryTest {
    private HttpServer server;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void retriesOnlyTransientSessionLookupAndThenAccepts() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/v1/live/sessions/live_retry_123/accept", exchange -> {
            int attempt = requests.incrementAndGet();
            if (attempt < 3) {
                byte[] body = "{\"error\":{\"message\":\"No session found\",\"code\":\"session_id_not_found\"}}"
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(404, body.length);
                exchange.getResponseBody().write(body);
            } else {
                exchange.sendResponseHeaders(200, -1);
            }
            exchange.close();
        });

        Fixture fixture = fixture("live_retry_123");
        fixture.service.handleIncoming("webhook_retry_123", fixture.event);

        assertEquals(3, requests.get());
        verify(fixture.sideband).attach("live_retry_123", fixture.context);
        verify(fixture.lifecycle, never()).updateStatus("live_retry_123", "failed", null);
    }

    @Test
    void doesNotRetryDecisionAlreadyMade() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/v1/live/sessions/live_decided_123/accept", exchange -> {
            requests.incrementAndGet();
            byte[] body = "{\"error\":{\"message\":\"Decision already made\",\"code\":\"decision_already_made\"}}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(409, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        Fixture fixture = fixture("live_decided_123");

        assertThrows(IllegalStateException.class,
                () -> fixture.service.handleIncoming("webhook_decided_123", fixture.event));
        assertEquals(1, requests.get());
        verify(fixture.sideband, never()).attach(anyString(), any());
    }

    private Fixture fixture(String sessionId) {
        OpenAiRealtimeProperties openAi = new OpenAiRealtimeProperties();
        openAi.setApiKey("sk-test");

        OpenAiLiveProperties live = new OpenAiLiveProperties();
        live.setEnabled(true);
        live.setProjectId("proj_test123");
        live.setWebhookSecret("whsec-test");
        live.setApiBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
        live.setModel("gpt-live-1");
        live.setBackendModel("gpt-5.6-luna");
        live.setVoice("marin");

        OpenAiLiveRouteSigner signer = new OpenAiLiveRouteSigner(live);
        CallLifecycleService lifecycle = mock(CallLifecycleService.class);
        RealtimeToolService tools = mock(RealtimeToolService.class);
        OpenAiLiveSidebandManager sideband = mock(OpenAiLiveSidebandManager.class);

        String businessPhone = "+14355652512";
        String callerPhone = "+56911111111";
        UUID callId = UUID.randomUUID();
        RealtimeCallContext context = new RealtimeCallContext(
                callId, UUID.randomUUID(), null, callerPhone, businessPhone, "live:" + sessionId);

        when(lifecycle.startInboundCall("openai-sip", sessionId, callerPhone, businessPhone)).thenReturn(callId);
        when(lifecycle.markStreamStarted(callId, sessionId, "live:" + sessionId, "openai-live"))
                .thenReturn(context);
        when(tools.buildInstructions(context)).thenReturn("backend instructions");
        when(tools.execute(context, "get_business_information", "{}"))
                .thenReturn("{\"success\":true,\"data\":{\"name\":\"Restaurante Demo\"},\"error\":null}");

        JSONArray sipHeaders = new JSONArray()
                .put(new JSONObject().put("name", "x-recepvoz-business").put("value", businessPhone))
                .put(new JSONObject().put("name", "x-recepvoz-caller").put("value", callerPhone))
                .put(new JSONObject().put("name", "x-recepvoz-route")
                        .put("value", signer.sign(businessPhone, callerPhone)));

        JSONObject event = new JSONObject()
                .put("id", "evt_" + sessionId)
                .put("created_at", 1_789_000_000L)
                .put("type", "live.transport.incoming")
                .put("data", new JSONObject()
                        .put("session_id", sessionId)
                        .put("type", "sip")
                        .put("sip_headers", sipHeaders));

        OpenAiLiveSipService service = new OpenAiLiveSipService(
                openAi, live, signer, lifecycle, tools, sideband);
        return new Fixture(service, lifecycle, sideband, context, event);
    }

    private record Fixture(OpenAiLiveSipService service,
                           CallLifecycleService lifecycle,
                           OpenAiLiveSidebandManager sideband,
                           RealtimeCallContext context,
                           JSONObject event) {
    }
}
