package cl.helvoca.simulator;

import cl.helvoca.ai.realtime.OpenAiRealtimeProperties;
import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.ai.realtime.RealtimeToolService;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.call.*;
import cl.helvoca.messaging.GeminiMessagingAiFallback;
import cl.helvoca.security.TenantProvider;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReceptionistSimulatorOpenAiLoopTest {

    @Test
    void openAiToolRoundTripsBackIntoTheModelAndReturnsNaturalText() throws Exception {
        List<String> bodies = new ArrayList<>();
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = server(requests, bodies,
                new JSONObject().put("id", "resp-1").put("output", new JSONArray().put(
                        new JSONObject()
                                .put("type", "function_call")
                                .put("call_id", "catalog-1")
                                .put("name", "list_catalog")
                                .put("arguments", "{}"))).toString(),
                new JSONObject().put("id", "resp-2").put("output_text", "Tenemos martillos disponibles.").toString());
        try {
            Fixture f = fixture(server);
            when(f.tools.execute(any(), eq("list_catalog"), anyString())).thenReturn(
                    success(new JSONObject().put("items", new JSONArray())));

            var response = f.service.message(f.sessionId, "¿Tienen martillos?");

            assertEquals("Tenemos martillos disponibles.", response.reply());
            assertEquals(2, requests.get());
            assertTrue(bodies.get(1).contains("previous_response_id"));
            assertTrue(bodies.get(1).contains("function_call_output"));
            verify(f.tools).execute(any(), eq("list_catalog"), eq("{}"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void missingResponseIdFallsBackToDeterministicToolSummary() throws Exception {
        HttpServer server = server(new AtomicInteger(), new ArrayList<>(),
                new JSONObject().put("output", new JSONArray().put(
                        new JSONObject()
                                .put("type", "function_call")
                                .put("call_id", "catalog-1")
                                .put("name", "list_catalog")
                                .put("arguments", "{}"))).toString());
        try {
            Fixture f = fixture(server);
            when(f.tools.execute(any(), eq("list_catalog"), anyString())).thenReturn(
                    success(new JSONObject().put("items", new JSONArray()
                            .put(new JSONObject()
                                    .put("name", "Martillo carpintero 16 oz")
                                    .put("price", 11990)
                                    .put("currency", "CLP")))));

            assertEquals("Tengo Martillo carpintero 16 oz por 11990 CLP.",
                    f.service.message(f.sessionId, "Muéstrame el catálogo").reply());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void missingResponseIdSummarizesAuthoritativeStockInsteadOfGenericSuccess() throws Exception {
        HttpServer server = server(new AtomicInteger(), new ArrayList<>(),
                new JSONObject().put("output", new JSONArray().put(
                        new JSONObject()
                                .put("type", "function_call")
                                .put("call_id", "stock-1")
                                .put("name", "get_stock")
                                .put("arguments", "{}"))).toString());
        try {
            Fixture f = fixture(server);
            when(f.tools.execute(any(), eq("get_stock"), anyString())).thenReturn(
                    success(new JSONObject()
                            .put("productName", "Alargador 6 tomas 3 m")
                            .put("availabilityKnown", true)
                            .put("available", 1)
                            .put("lowStock", true)));

            assertEquals("Queda 1 unidad disponible de Alargador 6 tomas 3 m.",
                    f.service.message(f.sessionId, "¿Cuántos alargadores quedan?").reply());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void missingResponseIdSummarizesOrderQuoteInsteadOfGenericSuccess() throws Exception {
        HttpServer server = server(new AtomicInteger(), new ArrayList<>(),
                new JSONObject().put("output", new JSONArray().put(
                        new JSONObject()
                                .put("type", "function_call")
                                .put("call_id", "order-1")
                                .put("name", "quote_order")
                                .put("arguments", "{}"))).toString());
        try {
            Fixture f = fixture(server);
            when(f.tools.execute(any(), eq("quote_order"), anyString())).thenReturn(
                    success(new JSONObject()
                            .put("status", "AWAITING_CONFIRMATION")
                            .put("total", 12990)
                            .put("currency", "CLP")));

            assertEquals("El pedido queda cotizado en 12990 CLP y espera tu confirmación.",
                    f.service.message(f.sessionId, "Cotízame uno").reply());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void missingResponseIdSummarizesDeliveryQuoteInsteadOfGenericSuccess() throws Exception {
        HttpServer server = server(new AtomicInteger(), new ArrayList<>(),
                new JSONObject().put("output", new JSONArray().put(
                        new JSONObject()
                                .put("type", "function_call")
                                .put("call_id", "delivery-1")
                                .put("name", "quote_delivery")
                                .put("arguments", "{}"))).toString());
        try {
            Fixture f = fixture(server);
            when(f.tools.execute(any(), eq("quote_delivery"), anyString())).thenReturn(
                    success(new JSONObject()
                            .put("address", "Av. Demo 123, Providencia")
                            .put("deliveryZone", "Providencia Demo")
                            .put("fee", 3990)));

            assertEquals("El despacho a Av. Demo 123, Providencia cuesta 3990 CLP.",
                    f.service.message(f.sessionId, "¿Cuánto cuesta el despacho?").reply());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void unusableFunctionCallProducesSafeFallbackInsteadOfLooping() throws Exception {
        HttpServer server = server(new AtomicInteger(), new ArrayList<>(),
                new JSONObject().put("id", "resp-1").put("output", new JSONArray().put(
                        new JSONObject()
                                .put("type", "function_call")
                                .put("call_id", "")
                                .put("name", "list_catalog")
                                .put("arguments", "{}"))).toString());
        try {
            Fixture f = fixture(server);
            assertEquals("No pude completar esa parte de la prueba. ¿Quieres intentarlo de otra forma?",
                    f.service.message(f.sessionId, "Catálogo").reply());
            verify(f.tools, never()).execute(any(), anyString(), anyString());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void geminiFallbackUsesOnlySimulatorPublishedToolNames() {
        Fixture f = fixture(null);
        when(f.openAi.hasApiKey()).thenReturn(false);
        when(f.gemini.configured()).thenReturn(true);
        when(f.gemini.respond(anyString(), anyList(), anySet(), any())).thenReturn("Respuesta Gemini segura");

        assertEquals("Respuesta Gemini segura",
                f.service.message(f.sessionId, "Necesito ayuda").reply());

        verify(f.gemini).respond(
                anyString(), anyList(), argThat(names -> names.size() == 1 && names.contains("list_catalog")), any());
        verify(f.calls).save(f.call);
    }

    @Test
    void continuationBuilderCoversNullBlankAndNullResultBranches() {
        assertTrue(ReceptionistSimulatorService.toolContinuationInput(null, List.of()).isEmpty());
        assertTrue(ReceptionistSimulatorService.toolContinuationInput(new JSONArray(), null).isEmpty());

        JSONArray calls = new JSONArray()
                .put(JSONObject.NULL)
                .put(new JSONObject().put("call_id", ""))
                .put(new JSONObject().put("call_id", "ok"));
        JSONArray output = ReceptionistSimulatorService.toolContinuationInput(
                calls, java.util.Arrays.asList("ignored", "ignored", null));

        assertEquals(1, output.length());
        assertEquals("", output.getJSONObject(0).getString("output"));
        assertEquals("ok", output.getJSONObject(0).getString("call_id"));
    }

    private static Fixture fixture(HttpServer server) {
        UUID businessId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        TenantProvider tenant = mock(TenantProvider.class);
        BusinessRepository businesses = mock(BusinessRepository.class);
        CallSessionRepository calls = mock(CallSessionRepository.class);
        CallTranscriptRepository transcripts = mock(CallTranscriptRepository.class);
        CallTranscriptService writer = mock(CallTranscriptService.class);
        CallActionRepository actions = mock(CallActionRepository.class);
        CallSummaryService summaries = mock(CallSummaryService.class);
        RealtimeToolService realTools = mock(RealtimeToolService.class);
        SimulatorToolExecutor tools = mock(SimulatorToolExecutor.class);
        SimulatorStateService state = new SimulatorStateService();
        OpenAiRealtimeProperties openAi = mock(OpenAiRealtimeProperties.class);
        GeminiMessagingAiFallback gemini = mock(GeminiMessagingAiFallback.class);
        CallSession call = mock(CallSession.class);

        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(call.getId()).thenReturn(sessionId);
        when(call.getBusinessId()).thenReturn(businessId);
        when(call.getStatus()).thenReturn(CallStatus.IN_PROGRESS);
        when(call.getTelephonyProvider()).thenReturn(ReceptionistSimulatorService.PROVIDER_ID);
        when(call.getCallerNumber()).thenReturn("web-simulator");
        when(call.getDestinationNumber()).thenReturn("web-simulator");
        when(call.getStreamSid()).thenReturn("simulator:" + sessionId);
        when(calls.findByIdAndBusinessId(sessionId, businessId)).thenReturn(Optional.of(call));
        when(calls.findById(sessionId)).thenReturn(Optional.of(call));
        when(actions.findAllByCallIdOrderByCreatedAtAsc(sessionId)).thenReturn(List.of());
        when(transcripts.findAllByCallIdOrderBySequenceNumberAsc(sessionId)).thenReturn(List.of());
        when(realTools.buildInstructions(any(RealtimeCallContext.class))).thenReturn("Instrucciones base.");
        when(tools.toolDefinitions(any())).thenReturn(new JSONArray()
                .put(tool("list_catalog"))
                .put(new JSONObject().put("type", "function").put("name", "")));
        when(openAi.hasApiKey()).thenReturn(true);
        when(openAi.getApiKey()).thenReturn("test-key");
        when(openAi.getTrialModel()).thenReturn("test-model");
        if (server != null) {
            when(openAi.getResponsesUrl()).thenReturn(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/responses");
        }

        state.start(sessionId);
        ReceptionistSimulatorService service = new ReceptionistSimulatorService(
                tenant, businesses, calls, transcripts, writer, actions, summaries,
                realTools, tools, state, openAi, gemini);
        return new Fixture(service, tools, openAi, gemini, calls, call, sessionId);
    }

    private static HttpServer server(AtomicInteger count, List<String> bodies, String... responses) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/responses", exchange -> {
            int index = count.getAndIncrement();
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String response = responses[Math.min(index, responses.length - 1)];
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return server;
    }

    private static JSONObject tool(String name) {
        return new JSONObject()
                .put("type", "function")
                .put("name", name)
                .put("description", name)
                .put("parameters", new JSONObject().put("type", "object").put("properties", new JSONObject()));
    }

    private static String success(JSONObject data) {
        return new JSONObject().put("success", true).put("data", data).put("error", JSONObject.NULL).toString();
    }

    private record Fixture(
            ReceptionistSimulatorService service,
            SimulatorToolExecutor tools,
            OpenAiRealtimeProperties openAi,
            GeminiMessagingAiFallback gemini,
            CallSessionRepository calls,
            CallSession call,
            UUID sessionId) {}
}
