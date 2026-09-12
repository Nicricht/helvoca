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
import java.time.Instant;
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
    void officialIncomingSipShapeIsAcceptedWithCarrierCorrelation() throws Exception {
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
        String callSid = "CA0123456789abcdef0123456789abcdef";
        long issuedAt = Instant.now().getEpochSecond();
        UUID callId = UUID.randomUUID();
        RealtimeCallContext context = new RealtimeCallContext(
                callId, UUID.randomUUID(), null, callerPhone, businessPhone, "live:" + sessionId);

        when(lifecycle.startInboundCall("twilio", callSid, callerPhone, businessPhone)).thenReturn(callId);
        when(lifecycle.markStreamStarted(callId, callSid, "live:" + sessionId, "openai-live"))
                .thenReturn(context);
        when(tools.buildInstructions(context)).thenReturn("backend instructions");
        when(tools.execute(context, "get_business_information", "{}"))
                .thenReturn("{\"success\":true,\"data\":{\"name\":\"Restaurante Demo\"},\"error\":null}");

        OpenAiLiveSipService service = new OpenAiLiveSipService(openAi, live, signer, lifecycle, tools, sideband);

        JSONArray sipHeaders = headers(signer, businessPhone, callerPhone, callSid, issuedAt);
        JSONObject event = new JSONObject()
                .put("id", "evt_live_123")
                .put("created_at", 1_789_000_000L)
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

        verify(sideband).attach(sessionId, context, "Restaurante Demo");
        verify(lifecycle).startInboundCall("twilio", callSid, callerPhone, businessPhone);
        verify(lifecycle).markStreamStarted(callId, callSid, "live:" + sessionId, "openai-live");
    }

    @Test
    void sameLiveSessionIsDecidedOnlyOnceEvenWithDifferentWebhookIds() throws Exception {
        server.createContext("/v1/live/sessions/live_dedupe_123/accept", exchange -> {
            exchange.getRequestBody().readAllBytes();
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

        OpenAiLiveRouteSigner signer = new OpenAiLiveRouteSigner(live);
        CallLifecycleService lifecycle = mock(CallLifecycleService.class);
        RealtimeToolService tools = mock(RealtimeToolService.class);
        OpenAiLiveSidebandManager sideband = mock(OpenAiLiveSidebandManager.class);

        String businessPhone = "+14355652512";
        String callerPhone = "+56911111111";
        String callSid = "CAaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        String sessionId = "live_dedupe_123";
        long issuedAt = Instant.now().getEpochSecond();
        UUID callId = UUID.randomUUID();
        RealtimeCallContext context = new RealtimeCallContext(
                callId, UUID.randomUUID(), null, callerPhone, businessPhone, "live:" + sessionId);

        when(lifecycle.startInboundCall("twilio", callSid, callerPhone, businessPhone)).thenReturn(callId);
        when(lifecycle.markStreamStarted(callId, callSid, "live:" + sessionId, "openai-live"))
                .thenReturn(context);
        when(tools.buildInstructions(context)).thenReturn("backend instructions");
        when(tools.execute(context, "get_business_information", "{}"))
                .thenReturn("{\"success\":true,\"data\":{\"name\":\"Demo\"}}");

        OpenAiLiveSipService service = new OpenAiLiveSipService(openAi, live, signer, lifecycle, tools, sideband);
        JSONObject event = new JSONObject()
                .put("type", "live.transport.incoming")
                .put("data", new JSONObject()
                        .put("session_id", sessionId)
                        .put("type", "sip")
                        .put("sip_headers", headers(signer, businessPhone, callerPhone, callSid, issuedAt)));

        service.handleIncoming("webhook_a", event);
        service.handleIncoming("webhook_b", event);

        verify(lifecycle, times(1)).startInboundCall("twilio", callSid, callerPhone, businessPhone);
        verify(sideband, times(1)).attach(sessionId, context, "Demo");
    }

    @Test
    void liveWebhookDoesNotFallBackToRealtimeCallId() {
        OpenAiRealtimeProperties openAi = new OpenAiRealtimeProperties();
        openAi.setApiKey("sk-test");

        OpenAiLiveProperties live = new OpenAiLiveProperties();
        live.setEnabled(true);
        live.setProjectId("proj_test123");
        live.setWebhookSecret("whsec-test");

        OpenAiLiveSipService service = new OpenAiLiveSipService(
                openAi, live, new OpenAiLiveRouteSigner(live),
                mock(CallLifecycleService.class), mock(RealtimeToolService.class),
                mock(OpenAiLiveSidebandManager.class));

        JSONObject event = new JSONObject()
                .put("type", "live.transport.incoming")
                .put("data", new JSONObject()
                        .put("call_id", "rtc_should_not_be_used")
                        .put("type", "sip"));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.handleIncoming("webhook_call_id_only", event));
        assertEquals("Missing Live session id", error.getMessage());
    }

    @Test
    void liveWebhookRejectsNonLiveSessionPrefix() {
        OpenAiRealtimeProperties openAi = new OpenAiRealtimeProperties();
        openAi.setApiKey("sk-test");

        OpenAiLiveProperties live = new OpenAiLiveProperties();
        live.setEnabled(true);
        live.setProjectId("proj_test123");
        live.setWebhookSecret("whsec-test");

        OpenAiLiveSipService service = new OpenAiLiveSipService(
                openAi, live, new OpenAiLiveRouteSigner(live),
                mock(CallLifecycleService.class), mock(RealtimeToolService.class),
                mock(OpenAiLiveSidebandManager.class));

        JSONObject event = new JSONObject()
                .put("type", "live.transport.incoming")
                .put("data", new JSONObject()
                        .put("session_id", "rtc_wrong_surface")
                        .put("type", "sip"));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.handleIncoming("webhook_wrong_prefix", event));
        assertEquals("Invalid Live session id prefix", error.getMessage());
    }

    private static JSONArray headers(OpenAiLiveRouteSigner signer,
                                     String businessPhone,
                                     String callerPhone,
                                     String callSid,
                                     long issuedAt) {
        return new JSONArray()
                .put(new JSONObject().put("name", "x-recepvoz-business").put("value", businessPhone))
                .put(new JSONObject().put("name", "x-recepvoz-caller").put("value", callerPhone))
                .put(new JSONObject().put("name", "x-recepvoz-call").put("value", callSid))
                .put(new JSONObject().put("name", "x-recepvoz-issued-at").put("value", String.valueOf(issuedAt)))
                .put(new JSONObject().put("name", "x-recepvoz-route")
                        .put("value", signer.sign(businessPhone, callerPhone, callSid, issuedAt)));
    }
}
