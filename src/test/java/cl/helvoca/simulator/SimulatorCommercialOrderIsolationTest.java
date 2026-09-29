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

class SimulatorCommercialOrderIsolationTest {

    @Test
    void commercialOrderFlowUsesAuthoritativeReadsButKeepsMutationsInsideSimulator() {
        RealtimeToolService realTools = mock(RealtimeToolService.class);
        CallTraceService trace = mock(CallTraceService.class);
        SimulatorStateService state = new SimulatorStateService();
        SimulatorToolExecutor executor = new SimulatorToolExecutor(realTools, state, trace);

        UUID businessId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        RealtimeCallContext first = context(businessId);
        RealtimeCallContext second = context(businessId);
        state.start(first.callId());
        state.start(second.callId());

        when(realTools.toolDefinitions(any())).thenReturn(new JSONArray()
                .put(tool("list_catalog"))
                .put(tool("get_stock"))
                .put(tool("quote_order"))
                .put(tool("update_order"))
                .put(tool("create_order"))
                .put(tool("get_order_status"))
                .put(tool("cancel_order")));

        when(realTools.execute(any(), eq("list_catalog"), anyString())).thenReturn(success(new JSONObject()
                .put("items", new JSONArray().put(new JSONObject()
                        .put("id", itemId.toString())
                        .put("name", "Alargador 6 tomas 3 m")
                        .put("price", 12990)
                        .put("currency", "CLP")
                        .put("variants", new JSONArray())))));

        when(realTools.execute(any(), eq("get_stock"), anyString())).thenReturn(success(new JSONObject()
                .put("catalogItemId", itemId.toString())
                .put("productName", "Alargador 6 tomas 3 m")
                .put("availabilityKnown", true)
                .put("available", 1)
                .put("lowStock", true)));

        Set<String> published = names(executor.toolDefinitions(first));
        assertTrue(published.containsAll(Set.of(
                "list_catalog", "get_stock", "quote_order", "update_order",
                "create_order", "get_order_status", "cancel_order")));

        JSONObject quoted = result(executor.execute(first, "quote_order",
                orderArgs(itemId, 1, "PICKUP").toString()));
        assertTrue(quoted.getBoolean("success"));
        JSONObject quoteData = quoted.getJSONObject("data");
        assertTrue(quoteData.getBoolean("simulated"));
        String operationId = quoteData.getString("operationId");
        String staleToken = quoteData.getString("confirmationToken");

        JSONObject updateArgs = orderArgs(itemId, 1, "PICKUP")
                .put("operationId", operationId);
        JSONObject updated = result(executor.execute(first, "update_order", updateArgs.toString()));
        assertTrue(updated.getBoolean("success"));
        String currentToken = updated.getJSONObject("data").getString("confirmationToken");
        assertNotEquals(staleToken, currentToken);

        JSONObject stale = result(executor.execute(first, "create_order", new JSONObject()
                .put("operationId", operationId)
                .put("confirmationToken", staleToken)
                .toString()));
        assertFalse(stale.getBoolean("success"));
        assertEquals("STALE_ORDER_CONFIRMATION", stale.getJSONObject("error").getString("code"));

        JSONObject confirmed = result(executor.execute(first, "create_order", new JSONObject()
                .put("operationId", operationId)
                .put("confirmationToken", currentToken)
                .toString()));
        assertTrue(confirmed.getBoolean("success"));
        assertEquals("CONFIRMED", confirmed.getJSONObject("data").getString("status"));

        JSONObject replay = result(executor.execute(first, "create_order", new JSONObject()
                .put("operationId", operationId)
                .put("confirmationToken", currentToken)
                .toString()));
        assertTrue(replay.getBoolean("success"));
        assertTrue(replay.getJSONObject("data").getBoolean("idempotentReplay"));

        JSONObject lastUnitRace = result(executor.execute(second, "quote_order",
                orderArgs(itemId, 1, "PICKUP").toString()));
        assertFalse(lastUnitRace.getBoolean("success"));
        assertEquals("INSUFFICIENT_STOCK", lastUnitRace.getJSONObject("error").getString("code"));

        verify(realTools, never()).execute(any(), eq("quote_order"), anyString());
        verify(realTools, never()).execute(any(), eq("update_order"), anyString());
        verify(realTools, never()).execute(any(), eq("create_order"), anyString());
        verify(realTools, never()).execute(any(), eq("cancel_order"), anyString());
    }

    private static RealtimeCallContext context(UUID businessId) {
        UUID callId = UUID.randomUUID();
        return new RealtimeCallContext(
                callId, businessId, null, "web-simulator", "web-simulator", "simulator:" + callId);
    }

    private static JSONObject orderArgs(UUID itemId, int quantity, String fulfillmentType) {
        return new JSONObject()
                .put("items", new JSONArray().put(new JSONObject()
                        .put("catalogItemId", itemId.toString())
                        .put("quantity", quantity)))
                .put("fulfillmentType", fulfillmentType);
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
