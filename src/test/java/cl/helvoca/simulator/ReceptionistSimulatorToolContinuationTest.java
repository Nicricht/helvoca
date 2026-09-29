package cl.helvoca.simulator;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReceptionistSimulatorToolContinuationTest {

    @Test
    void toolResultsAreReturnedAsResponsesFunctionCallOutputs() {
        JSONArray calls = new JSONArray().put(new JSONObject()
                .put("type", "function_call")
                .put("call_id", "call_catalog_1")
                .put("name", "list_catalog")
                .put("arguments", "{}"));

        JSONArray input = ReceptionistSimulatorService.toolContinuationInput(
                calls,
                List.of("{\"success\":true,\"data\":{\"items\":[]}}"));

        assertEquals(1, input.length());
        JSONObject output = input.getJSONObject(0);
        assertEquals("function_call_output", output.getString("type"));
        assertEquals("call_catalog_1", output.getString("call_id"));
        assertEquals("{\"success\":true,\"data\":{\"items\":[]}}", output.getString("output"));
    }
}
