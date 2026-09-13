package cl.helvoca.ai.realtime;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RealtimeToolDefinitionsTest {

    @Test
    void bookingToolsRequireServiceIdsFromCatalogInsteadOfInventedUuids() {
        JSONArray tools = RealtimeToolDefinitions.all();
        assertCatalogSourced(tools, "list_available_slots");
        assertCatalogSourced(tools, "check_booking_availability");
        assertCatalogSourced(tools, "create_booking");
    }

    @Test
    void bookingToolsTellTheModelHowToContinueWithoutInventingState() {
        JSONArray tools = RealtimeToolDefinitions.all();
        assertDescriptionContains(tools, "list_available_slots", "create_booking");
        assertDescriptionContains(tools, "check_booking_availability", "create_booking");
        assertDescriptionContains(tools, "create_booking", "bookingId");
        assertDescriptionContains(tools, "cancel_booking", "create_booking");
        assertDescriptionContains(tools, "cancel_booking", "bookingId");
    }

    private static void assertCatalogSourced(JSONArray tools, String name) {
        for (int i = 0; i < tools.length(); i++) {
            JSONObject tool = tools.getJSONObject(i);
            if (!name.equals(tool.getString("name"))) continue;

            String description = tool.getString("description");
            String serviceDescription = tool.getJSONObject("parameters")
                    .getJSONObject("properties")
                    .getJSONObject("serviceId")
                    .getString("description");

            assertTrue(description.contains("list_services"));
            assertTrue(serviceDescription.contains("list_services"));
            assertTrue(serviceDescription.toLowerCase().contains("nunca invent"));
            return;
        }
        throw new AssertionError("Tool not found: " + name);
    }

    private static void assertDescriptionContains(JSONArray tools, String name, String expected) {
        for (int i = 0; i < tools.length(); i++) {
            JSONObject tool = tools.getJSONObject(i);
            if (!name.equals(tool.getString("name"))) continue;
            assertTrue(tool.getString("description").contains(expected),
                    () -> name + " should mention " + expected);
            return;
        }
        throw new AssertionError("Tool not found: " + name);
    }
}
