package cl.helvoca.simulator;

import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.ai.realtime.RealtimeToolService;
import cl.helvoca.call.CallTraceService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SimulatorCommercialErrorBranchesTest {

    @Test
    void quoteDeliveryCoversBackendFailureUncoveredAndCoveredAddress() {
        Fixture f = fixture();
        when(f.real.execute(any(), eq("validate_delivery_address"), anyString()))
                .thenReturn(error("ZONE_LOOKUP_FAILED", "falló"));
        assertEquals("ZONE_LOOKUP_FAILED", result(f.executor.execute(
                f.context, "quote_delivery", "{\"address\":\"A\"}")).getJSONObject("error").getString("code"));

        when(f.real.execute(any(), eq("validate_delivery_address"), anyString()))
                .thenReturn(success(new JSONObject().put("covered", false)));
        assertEquals("DELIVERY_ADDRESS_UNAVAILABLE", result(f.executor.execute(
                f.context, "quote_delivery", "{\"address\":\"A\"}")).getJSONObject("error").getString("code"));

        when(f.real.execute(any(), eq("validate_delivery_address"), anyString()))
                .thenReturn(success(new JSONObject().put("covered", true).put("address", "A").put("fee", 1000)));
        assertTrue(result(f.executor.execute(
                f.context, "quote_delivery", "{\"address\":\"A\"}")).getBoolean("success"));
    }

    @Test
    void createQuoteCoversCatalogAndItemValidationBranches() {
        UUID item = UUID.randomUUID();
        Fixture f = fixture();

        assertEquals("INVALID_ARGUMENT", code(f.executor.execute(f.context, "create_quote", "{\"title\":\" \"}")));
        assertEquals("REQUESTED", result(f.executor.execute(
                f.context, "create_quote", "{\"title\":\"Humana\"}")).getJSONObject("data").getString("status"));

        when(f.real.execute(any(), eq("list_catalog"), anyString())).thenReturn(error("DOWN", "x"));
        assertEquals("CATALOG_UNAVAILABLE", code(f.executor.execute(
                f.context, "create_quote", quoteArgs(item, 1).toString())));

        when(f.real.execute(any(), eq("list_catalog"), anyString())).thenReturn(success(new JSONObject()));
        assertEquals("CATALOG_UNAVAILABLE", code(f.executor.execute(
                f.context, "create_quote", quoteArgs(item, 1).toString())));

        when(f.real.execute(any(), eq("list_catalog"), anyString())).thenReturn(catalog(new JSONArray()
                .put(itemJson(item, "CLP", 1000))));
        JSONObject bad = new JSONObject().put("title", "Q").put("items", new JSONArray().put(JSONObject.NULL));
        assertEquals("INVALID_ARGUMENT", code(f.executor.execute(f.context, "create_quote", bad.toString())));
        assertEquals("INVALID_QUANTITY", code(f.executor.execute(
                f.context, "create_quote", quoteArgs(item, 0).toString())));
        assertEquals("CATALOG_ITEM_UNAVAILABLE", code(f.executor.execute(
                f.context, "create_quote", quoteArgs(UUID.randomUUID(), 1).toString())));

        JSONObject noPrice = itemJson(item, "CLP", null);
        when(f.real.execute(any(), eq("list_catalog"), anyString())).thenReturn(catalog(new JSONArray().put(noPrice)));
        assertEquals("CATALOG_PRICE_UNAVAILABLE", code(f.executor.execute(
                f.context, "create_quote", quoteArgs(item, 1).toString())));

        UUID second = UUID.randomUUID();
        when(f.real.execute(any(), eq("list_catalog"), anyString())).thenReturn(catalog(new JSONArray()
                .put(itemJson(item, "CLP", 1000))
                .put(itemJson(second, "USD", 10))));
        JSONObject mixed = new JSONObject().put("title", "Q").put("items", new JSONArray()
                .put(line(item, 1)).put(line(second, 1)));
        assertEquals("CURRENCY_MISMATCH", code(f.executor.execute(f.context, "create_quote", mixed.toString())));
    }

    @Test
    void orderCalculationCoversInputCatalogVariantStockAndFulfillmentErrors() {
        UUID item = UUID.randomUUID();
        UUID variant = UUID.randomUUID();
        Fixture f = fixture();

        assertEquals("INVALID_ARGUMENT", code(f.executor.execute(
                f.context, "quote_order", "{\"fulfillmentType\":\"PICKUP\"}")));

        when(f.real.execute(any(), eq("list_catalog"), anyString())).thenReturn(error("DOWN", "x"));
        assertEquals("CATALOG_UNAVAILABLE", code(f.executor.execute(
                f.context, "quote_order", orderArgs(item, 1, "PICKUP").toString())));

        when(f.real.execute(any(), eq("list_catalog"), anyString())).thenReturn(success(new JSONObject()));
        assertEquals("CATALOG_UNAVAILABLE", code(f.executor.execute(
                f.context, "quote_order", orderArgs(item, 1, "PICKUP").toString())));

        when(f.real.execute(any(), eq("list_catalog"), anyString())).thenReturn(catalog(new JSONArray()
                .put(itemJson(item, "CLP", 1000))));
        JSONObject nullItem = new JSONObject().put("items", new JSONArray().put(JSONObject.NULL))
                .put("fulfillmentType", "PICKUP");
        assertEquals("INVALID_ARGUMENT", code(f.executor.execute(f.context, "quote_order", nullItem.toString())));

        JSONObject badUuid = new JSONObject().put("items", new JSONArray()
                .put(new JSONObject().put("catalogItemId", "bad").put("quantity", 1)))
                .put("fulfillmentType", "PICKUP");
        assertEquals("INVALID_ARGUMENT", code(f.executor.execute(f.context, "quote_order", badUuid.toString())));
        assertEquals("INVALID_QUANTITY", code(f.executor.execute(
                f.context, "quote_order", orderArgs(item, 101, "PICKUP").toString())));
        assertEquals("CATALOG_ITEM_UNAVAILABLE", code(f.executor.execute(
                f.context, "quote_order", orderArgs(UUID.randomUUID(), 1, "PICKUP").toString())));

        JSONObject variantItem = itemJson(item, "CLP", 1000)
                .put("hasVariants", true)
                .put("variants", new JSONArray().put(new JSONObject().put("variantId", variant.toString())));
        when(f.real.execute(any(), eq("list_catalog"), anyString())).thenReturn(catalog(new JSONArray().put(variantItem)));
        assertEquals("VARIANT_SELECTION_REQUIRED", code(f.executor.execute(
                f.context, "quote_order", orderArgs(item, 1, "PICKUP").toString())));

        JSONObject invalidVariant = orderArgs(item, 1, "PICKUP");
        invalidVariant.getJSONArray("items").getJSONObject(0).put("variantId", "bad");
        assertEquals("INVALID_ARGUMENT", code(f.executor.execute(f.context, "quote_order", invalidVariant.toString())));

        JSONObject missingVariant = orderArgs(item, 1, "PICKUP");
        missingVariant.getJSONArray("items").getJSONObject(0).put("variantId", UUID.randomUUID().toString());
        assertEquals("VARIANT_NOT_FOUND", code(f.executor.execute(f.context, "quote_order", missingVariant.toString())));

        JSONObject noPrice = itemJson(item, "CLP", null).put("variants", new JSONArray());
        when(f.real.execute(any(), eq("list_catalog"), anyString())).thenReturn(catalog(new JSONArray().put(noPrice)));
        assertEquals("CATALOG_PRICE_UNAVAILABLE", code(f.executor.execute(
                f.context, "quote_order", orderArgs(item, 1, "PICKUP").toString())));

        when(f.real.execute(any(), eq("list_catalog"), anyString())).thenReturn(catalog(new JSONArray()
                .put(itemJson(item, "CLP", 1000))));
        when(f.real.execute(any(), eq("get_stock"), anyString())).thenReturn(success(new JSONObject()
                .put("availabilityKnown", true).put("available", 0)));
        assertEquals("INSUFFICIENT_STOCK", code(f.executor.execute(
                f.context, "quote_order", orderArgs(item, 1, "PICKUP").toString())));

        when(f.real.execute(any(), eq("get_stock"), anyString())).thenReturn(success(new JSONObject()
                .put("availabilityKnown", false)));
        JSONObject missingFulfillment = new JSONObject().put("items", new JSONArray().put(line(item, 1)));
        assertEquals("INVALID_ARGUMENT", code(f.executor.execute(
                f.context, "quote_order", missingFulfillment.toString())));
        assertEquals("INVALID_FULFILLMENT_TYPE", code(f.executor.execute(
                f.context, "quote_order", orderArgs(item, 1, "DRONE").toString())));
    }

    @Test
    void deliveryOrderCoversAddressValidationMinimumAndSuccessBranches() {
        UUID item = UUID.randomUUID();
        Fixture f = fixture();
        when(f.real.execute(any(), eq("list_catalog"), anyString())).thenReturn(catalog(new JSONArray()
                .put(itemJson(item, "", 1000))));
        when(f.real.execute(any(), eq("get_stock"), anyString())).thenReturn(success(new JSONObject()
                .put("availabilityKnown", true).put("available", 5)));

        assertEquals("DELIVERY_ADDRESS_REQUIRED", code(f.executor.execute(
                f.context, "quote_order", orderArgs(item, 1, "DELIVERY").toString())));

        JSONObject delivery = orderArgs(item, 1, "DELIVERY").put("address", "Demo 1");
        when(f.real.execute(any(), eq("validate_delivery_address"), anyString())).thenReturn(error("OUTSIDE", "fuera"));
        assertEquals("OUTSIDE", code(f.executor.execute(f.context, "quote_order", delivery.toString())));

        when(f.real.execute(any(), eq("validate_delivery_address"), anyString())).thenReturn(success(new JSONObject()
                .put("fee", 500).put("minimumOrder", 5000)));
        assertEquals("DELIVERY_MINIMUM_NOT_MET", code(f.executor.execute(f.context, "quote_order", delivery.toString())));

        when(f.real.execute(any(), eq("validate_delivery_address"), anyString())).thenReturn(success(new JSONObject()
                .put("fee", 500).put("minimumOrder", 0)));
        JSONObject ok = result(f.executor.execute(f.context, "quote_order", delivery.toString()));
        assertTrue(ok.getBoolean("success"));
        assertEquals(1500, ok.getJSONObject("data").getBigDecimal("total").intValueExact());
    }

    @Test
    void orderStatusCancellationAndUnknownToolsCoverRemainingControlBranches() {
        UUID item = UUID.randomUUID();
        Fixture f = fixture();
        when(f.real.execute(any(), eq("list_catalog"), anyString())).thenReturn(catalog(new JSONArray()
                .put(itemJson(item, "CLP", 1000))));
        when(f.real.execute(any(), eq("get_stock"), anyString())).thenReturn(success(new JSONObject()
                .put("availabilityKnown", true).put("available", 3)));

        assertEquals("ORDER_OPERATION_NOT_FOUND", code(f.executor.execute(
                f.context, "update_order", orderArgs(item, 1, "PICKUP")
                        .put("operationId", UUID.randomUUID().toString()).toString())));
        assertEquals("ORDER_OPERATION_NOT_FOUND", code(f.executor.execute(
                f.context, "create_order", new JSONObject()
                        .put("operationId", UUID.randomUUID().toString())
                        .put("confirmationToken", UUID.randomUUID().toString()).toString())));

        JSONObject quote = result(f.executor.execute(f.context, "quote_order", orderArgs(item, 1, "PICKUP").toString()));
        JSONObject qd = quote.getJSONObject("data");
        String op = qd.getString("operationId");
        String token = qd.getString("confirmationToken");

        assertEquals("ORDER_NOT_FOUND", code(f.executor.execute(
                f.context, "get_order_status", "{\"operationId\":\"" + UUID.randomUUID() + "\"}")));
        assertTrue(result(f.executor.execute(
                f.context, "get_order_status", "{\"operationId\":\"" + op + "\"}")).getBoolean("success"));
        assertTrue(result(f.executor.execute(f.context, "get_order_status", "{}")).getBoolean("success"));
        assertEquals("ORDER_NOT_FOUND", code(f.executor.execute(
                f.context, "cancel_order", "{\"orderId\":\"" + UUID.randomUUID() + "\"}")));

        JSONObject confirmed = result(f.executor.execute(f.context, "create_order",
                new JSONObject().put("operationId", op).put("confirmationToken", token).toString()));
        String orderId = confirmed.getJSONObject("data").getString("orderId");
        assertTrue(result(f.executor.execute(
                f.context, "get_order_status", "{\"orderId\":\"" + orderId + "\"}")).getBoolean("success"));
        assertTrue(result(f.executor.execute(
                f.context, "cancel_order", "{\"orderId\":\"" + orderId + "\"}")).getBoolean("success"));

        assertEquals("WHATSAPP_CONFIRMATION_REQUIRED", code(f.executor.execute(
                f.context, "verify_caller_whatsapp", "{\"confirmedSameNumber\":false}")));
        assertEquals("SIMULATOR_TOOL_BLOCKED", code(f.executor.execute(f.context, "evil_tool", "{}")));
        assertEquals("INVALID_ARGUMENT", code(f.executor.execute(f.context, "quote_delivery", "{}")));
        assertEquals("INVALID_ARGUMENT", code(f.executor.execute(f.context, "quote_order", "{bad json")));
    }

    private record Fixture(RealtimeToolService real, SimulatorToolExecutor executor, RealtimeCallContext context) {}

    private static Fixture fixture() {
        RealtimeToolService real = mock(RealtimeToolService.class);
        CallTraceService trace = mock(CallTraceService.class);
        SimulatorStateService state = new SimulatorStateService();
        SimulatorToolExecutor executor = new SimulatorToolExecutor(real, state, trace);
        UUID callId = UUID.randomUUID();
        RealtimeCallContext context = new RealtimeCallContext(
                callId, UUID.randomUUID(), null, "web-simulator", "web-simulator", "simulator:" + callId);
        state.start(callId);
        return new Fixture(real, executor, context);
    }

    private static JSONObject orderArgs(UUID item, int qty, String fulfillment) {
        return new JSONObject().put("items", new JSONArray().put(line(item, qty)))
                .put("fulfillmentType", fulfillment);
    }

    private static JSONObject quoteArgs(UUID item, int qty) {
        return new JSONObject().put("title", "Q")
                .put("items", new JSONArray().put(line(item, qty)));
    }

    private static JSONObject line(UUID item, int qty) {
        return new JSONObject().put("catalogItemId", item.toString()).put("quantity", qty);
    }

    private static JSONObject itemJson(UUID id, String currency, Object price) {
        JSONObject item = new JSONObject().put("id", id.toString()).put("name", "Producto")
                .put("currency", currency).put("variants", new JSONArray());
        if (price == null) item.put("price", JSONObject.NULL);
        else item.put("price", price);
        return item;
    }

    private static String catalog(JSONArray items) {
        return success(new JSONObject().put("items", items));
    }

    private static String code(String raw) {
        return result(raw).getJSONObject("error").getString("code");
    }

    private static JSONObject result(String raw) {
        assertNotNull(raw);
        return new JSONObject(raw);
    }

    private static String success(JSONObject data) {
        return new JSONObject().put("success", true).put("data", data).put("error", JSONObject.NULL).toString();
    }

    private static String error(String code, String message) {
        return new JSONObject().put("success", false).put("data", JSONObject.NULL)
                .put("error", new JSONObject().put("code", code).put("message", message)).toString();
    }
}
