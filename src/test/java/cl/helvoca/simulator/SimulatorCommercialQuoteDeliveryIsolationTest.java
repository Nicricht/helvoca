package cl.helvoca.simulator;

import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.ai.realtime.RealtimeToolService;
import cl.helvoca.call.CallTraceService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SimulatorCommercialQuoteDeliveryIsolationTest {

    @Test
    void quoteAndDeliveryDraftsUseAuthoritativeReadsWithoutPersistingCommercialMutations() {
        RealtimeToolService realTools = mock(RealtimeToolService.class);
        CallTraceService trace = mock(CallTraceService.class);
        SimulatorStateService state = new SimulatorStateService();
        SimulatorToolExecutor executor = new SimulatorToolExecutor(realTools, state, trace);

        UUID businessId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        RealtimeCallContext context = new RealtimeCallContext(
                callId, businessId, null, "web-simulator", "web-simulator", "simulator:" + callId);
        state.start(callId);

        when(realTools.toolDefinitions(any())).thenReturn(new JSONArray()
                .put(tool("list_catalog"))
                .put(tool("validate_delivery_address"))
                .put(tool("quote_delivery"))
                .put(tool("create_quote")));

        when(realTools.execute(any(), eq("list_catalog"), anyString())).thenReturn(success(new JSONObject()
                .put("items", new JSONArray().put(new JSONObject()
                        .put("id", itemId.toString())
                        .put("name", "Caja de tornillos")
                        .put("price", 5490)
                        .put("currency", "CLP")
                        .put("variants", new JSONArray())))));

        when(realTools.execute(any(), eq("validate_delivery_address"), anyString())).thenReturn(success(new JSONObject()
                .put("covered", true)
                .put("address", "Av. Demo 123, Providencia")
                .put("deliveryZoneId", zoneId.toString())
                .put("deliveryZone", "Providencia Demo")
                .put("fee", 3990)
                .put("minimumOrder", 0)));

        Set<String> published = names(executor.toolDefinitions(context));
        assertTrue(published.containsAll(Set.of("quote_delivery", "create_quote")));

        JSONObject delivery = result(executor.execute(context, "quote_delivery",
                new JSONObject().put("address", "Av. Demo 123, Providencia").toString()));
        assertTrue(delivery.getBoolean("success"));
        JSONObject deliveryData = delivery.getJSONObject("data");
        assertTrue(deliveryData.getBoolean("simulated"));
        assertEquals("Providencia Demo", deliveryData.getString("deliveryZone"));
        assertEquals(3990, deliveryData.getBigDecimal("fee").intValueExact());
        assertEquals("AWAITING_CONFIRMATION", deliveryData.getString("status"));

        JSONObject quote = result(executor.execute(context, "create_quote",
                new JSONObject()
                        .put("title", "Tornillos para obra")
                        .put("items", new JSONArray().put(new JSONObject()
                                .put("catalogItemId", itemId.toString())
                                .put("quantity", 2)))
                        .toString()));
        assertTrue(quote.getBoolean("success"));
        JSONObject quoteData = quote.getJSONObject("data");
        assertTrue(quoteData.getBoolean("simulated"));
        assertEquals("READY", quoteData.getString("status"));
        assertEquals(10980, quoteData.getBigDecimal("amount").intValueExact());
        assertEquals("CLP", quoteData.getString("currency"));

        JSONObject humanReview = result(executor.execute(context, "create_quote",
                new JSONObject()
                        .put("title", "Evaluar instalación estructural")
                        .put("description", "Requiere evaluación humana")
                        .toString()));
        assertTrue(humanReview.getBoolean("success"));
        assertEquals("REQUESTED", humanReview.getJSONObject("data").getString("status"));
        assertTrue(humanReview.getJSONObject("data").isNull("amount"));

        verify(realTools, never()).execute(any(), eq("quote_delivery"), anyString());
        verify(realTools, never()).execute(any(), eq("create_quote"), anyString());
    }

    private static JSONObject tool(String name) {
        return new JSONObject().put("type", "function").put("name", name)
                .put("description", name)
                .put("parameters", new JSONObject().put("type", "object")
                        .put("properties", new JSONObject()));
    }

    private static Set<String> names(JSONArray definitions) {
        return IntStream.range(0, definitions.length())
                .mapToObj(i -> definitions.getJSONObject(i).getString("name"))
                .collect(Collectors.toSet());
    }

    private static JSONObject result(String raw) {
        assertNotNull(raw);
        return new JSONObject(raw);
    }

    private static String success(JSONObject data) {
        return new JSONObject()
                .put("success", true)
                .put("data", data)
                .put("error", JSONObject.NULL)
                .toString();
    }
}
