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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OpenAiLiveIncomingContractTest {
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
    void officialIncomingSipShapeIsAcceptedWithProjectScopedLiveConfiguration() throws Exception {
        AtomicReference<String> requestPath = new AtomicReference<>();
        AtomicReference<String> projectHeader = new AtomicReference<>();
        AtomicReference<String> authorizationHeader = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();

        server.createContext("/v1/live/sessions/live_session_123/accept", exchange -> {
            requestPath.set(exchange.getRequestURI().getPath());
            projectHeader.set(exchange.getRequestHeaders().getFirst("OpenAI-Project"));
            authorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });

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
        String sessionId = "live_session_123";
        UUID callId = UUID.randomUUID();
        RealtimeCallContext context = new RealtimeCallContext(
                callId, UUID.randomUUID(), null, callerPhone, businessPhone, "live:" + sessionId);

        when(lifecycle.startInboundCall("openai-sip", sessionId, callerPhone, businessPhone)).thenReturn(callId);
        when(lifecycle.markStreamStarted(callId, sessionId, "live:" + sessionId, "openai-live"))
                .thenReturn(context);
        when(tools.execute(context, "get_business_information", "{}"))
                .thenReturn("{\"success\":true,\"data\":{\"name\":\"Restaurante Demo\"},\"error\":null}");

        OpenAiLiveSipService service = new OpenAiLiveSipService(openAi, live, signer, lifecycle, tools, sideband);

        JSONArray sipHeaders = new JSONArray()
                .put(new JSONObject().put("name", "x-recepvoz-business").put("value", businessPhone))
                .put(new JSONObject().put("name", "x-recepvoz-caller").put("value", callerPhone))
                .put(new JSONObject().put("name", "x-recepvoz-route")
                        .put("value", signer.sign(businessPhone, callerPhone)));
        JSONObject event = new JSONObject()
                .put("type", "live.transport.incoming")
                .put("data", new JSONObject()
                        .put("session_id", sessionId)
                        .put("type", "sip")
                        .put("sip_headers", sipHeaders));

        service.handleIncoming("webhook_123", event);

        assertEquals("/v1/live/sessions/live_session_123/accept", requestPath.get());
        assertEquals("proj_test123", projectHeader.get());
        assertEquals("Bearer sk-test", authorizationHeader.get());

        JSONObject body = new JSONObject(requestBody.get());
        JSONObject session = body.getJSONObject("session");
        assertEquals("live", session.getString("type"));
        assertEquals("gpt-live-1", session.getString("model"));
        assertEquals("marin", session.getJSONObject("audio").getJSONObject("output").getString("voice"));
        JSONObject responses = session.getJSONObject("delegation").getJSONObject("responses");
        assertEquals("gpt-5.6-luna", responses.getString("model"));
        assertEquals("auto", responses.getString("tool_choice"));
        assertFalse(responses.getBoolean("parallel_tool_calls"));
        assertTrue(responses.getJSONArray("tools").length() > 0);

        verify(sideband).attach(sessionId, context);
        verify(lifecycle).startInboundCall("openai-sip", sessionId, callerPhone, businessPhone);
    }
}
