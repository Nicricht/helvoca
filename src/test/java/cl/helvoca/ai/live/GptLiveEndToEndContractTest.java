package cl.helvoca.ai.live;

import cl.helvoca.ai.realtime.OpenAiRealtimeProperties;
import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.ai.realtime.RealtimeToolService;
import cl.helvoca.call.CallSummaryService;
import cl.helvoca.telephony.CallLifecycleService;
import cl.helvoca.telephony.twilio.TwilioCallService;
import cl.helvoca.telephony.twilio.TwilioMediaStreamTwimlFactory;
import cl.helvoca.telephony.twilio.TwilioVoiceController;
import cl.helvoca.voice.VoiceAiProviderRegistry;
import cl.helvoca.voice.VoiceCallRouter;
import cl.helvoca.voice.VoiceProviderHealthRegistry;
import cl.helvoca.voice.VoiceProviderProperties;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GptLiveEndToEndContractTest {
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
    void twilioWebhookToSignedSipToLiveAcceptToSidebandIsOneCoherentFlow() throws Exception {
        AtomicReference<String> acceptBody = new AtomicReference<>();
        server.createContext("/v1/live/sessions/live_e2e_123/accept", exchange -> {
            acceptBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });

        OpenAiRealtimeProperties openAi = new OpenAiRealtimeProperties();
        openAi.setApiKey("sk-test");
        OpenAiLiveProperties live = new OpenAiLiveProperties();
        live.setEnabled(true);
        live.setProjectId("proj_test123");
        live.setWebhookSecret("whsec-e2e-test");
        live.setApiBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
        live.setModel("gpt-live-1");
        live.setBackendModel("gpt-5.6-luna");
        live.setVoice("marin");

        OpenAiLiveRouteSigner signer = new OpenAiLiveRouteSigner(live);
        CallLifecycleService lifecycle = mock(CallLifecycleService.class);
        RealtimeToolService tools = mock(RealtimeToolService.class);
        OpenAiLiveSidebandManager sideband = mock(OpenAiLiveSidebandManager.class);
        OpenAiLiveSipService liveSip = new OpenAiLiveSipService(
                openAi, live, signer, lifecycle, tools, sideband);

        VoiceProviderProperties voiceProperties = new VoiceProviderProperties();
        voiceProperties.setProviderOrder(List.of("openai-live"));
        VoiceCallRouter router = new VoiceCallRouter(
                voiceProperties,
                mock(VoiceAiProviderRegistry.class),
                new VoiceProviderHealthRegistry(),
                mock(TwilioMediaStreamTwimlFactory.class),
                liveSip);
        TwilioVoiceController controller = new TwilioVoiceController(
                mock(TwilioCallService.class), router, mock(CallSummaryService.class));

        String callSid = "CA0123456789abcdef0123456789abcdef";
        String businessPhone = "+14355652512";
        String callerPhone = "+56966939611";
        UUID callId = UUID.randomUUID();
        RealtimeCallContext context = new RealtimeCallContext(
                callId, UUID.randomUUID(), null, callerPhone, businessPhone, "live:live_e2e_123");

        when(lifecycle.startInboundCall("twilio", callSid, callerPhone, businessPhone)).thenReturn(callId);
        when(lifecycle.markStreamStarted(callId, callSid, "live:live_e2e_123", "openai-live"))
                .thenReturn(context);
        when(tools.buildInstructions(context)).thenReturn("backend instructions");
        when(tools.execute(context, "get_business_information", "{}"))
                .thenReturn("{\"success\":true,\"data\":{\"name\":\"Restaurante Demo\"},\"error\":null}");

        var twilioResponse = controller.outboundTest(callSid, businessPhone, callerPhone);
        assertEquals(200, twilioResponse.getStatusCode().value());
        String xml = twilioResponse.getBody();
        assertNotNull(xml);
        assertFalse(xml.contains("<Say"));
        assertFalse(xml.contains("<Gather"));
        assertFalse(xml.contains("<Stream"));

        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        String sipUri = document.getElementsByTagName("Sip").item(0).getTextContent();
        assertTrue(sipUri.startsWith("sip:proj_test123@sip.api.openai.com;secure=true?"));
        Map<String, String> route = query(sipUri.substring(sipUri.indexOf('?') + 1));

        JSONArray sipHeaders = new JSONArray();
        route.forEach((name, value) -> sipHeaders.put(
                new JSONObject().put("name", name).put("value", value)));
        JSONObject event = new JSONObject()
                .put("id", "evt_e2e_123")
                .put("type", "live.transport.incoming")
                .put("data", new JSONObject()
                        .put("session_id", "live_e2e_123")
                        .put("type", "sip")
                        .put("sip_headers", sipHeaders));

        liveSip.handleIncoming("wh_e2e_123", event);

        verify(lifecycle).startInboundCall("twilio", callSid, callerPhone, businessPhone);
        verify(lifecycle).markStreamStarted(callId, callSid, "live:live_e2e_123", "openai-live");
        verify(sideband).attach("live_e2e_123", context, "Restaurante Demo");
        JSONObject body = new JSONObject(acceptBody.get());
        assertEquals("live", body.getJSONObject("session").getString("type"));
        assertEquals("gpt-live-1", body.getJSONObject("session").getString("model"));
    }

    private static Map<String, String> query(String rawQuery) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String pair : rawQuery.split("&")) {
            int separator = pair.indexOf('=');
            if (separator <= 0) continue;
            String key = URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8);
            result.put(key, value);
        }
        return result;
    }
}
