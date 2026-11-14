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
        assertDescriptionContains(tools, "list_available_slots", "unavailabilityReasonKnown=false");
        assertDescriptionContains(tools, "list_available_slots", "jamás inventes alta demanda");
        assertDescriptionContains(tools, "check_booking_availability", "create_booking");
        assertDescriptionContains(tools, "check_booking_availability", "customerSafeExplanation");
        assertDescriptionContains(tools, "list_available_slots", "NO llames check_booking_availability");
        assertDescriptionContains(tools, "check_booking_availability", "NO uses esta herramienta");
        assertDescriptionContains(tools, "check_booking_availability", "revalida el horario en backend");
        assertDescriptionContains(tools, "create_booking", "bookingId");
        assertDescriptionContains(tools, "create_booking", "te confirmo la reserva");
        assertDescriptionContains(tools, "create_booking", "UNA sola vez");
        assertDescriptionContains(tools, "create_booking", "sí, ya, dale, claro, ok, bueno");
        assertDescriptionContains(tools, "create_booking", "NO vuelvas a preguntar");
        assertDescriptionContains(tools, "cancel_booking", "create_booking");
        assertDescriptionContains(tools, "cancel_booking", "bookingId");
    }

    @Test
    void calendarToolsExposeRequestedWeekdayForBackendValidation() {
        JSONArray tools = RealtimeToolDefinitions.all();
        assertParameterExists(tools, "list_available_slots", "requestedWeekday");
        assertParameterExists(tools, "check_booking_availability", "requestedWeekday");
        assertParameterExists(tools, "create_booking", "requestedWeekday");
        assertParameterExists(tools, "reschedule_booking", "requestedWeekday");
        for (String tool : new String[]{"list_available_slots", "check_booking_availability", "create_booking", "reschedule_booking"}) {
            assertParameterExists(tools, tool, "requestedWeekScope");
            assertParameterDescriptionContains(tools, tool, "requestedWeekScope", "NEXT_WEEK");
            assertParameterDescriptionContains(tools, tool, "requestedWeekScope", "THIS_WEEK");
        }
        assertDescriptionContains(tools, "get_business_information", "fecha local actual");
        assertDescriptionContains(tools, "get_business_information", "día de la semana");
        assertDescriptionContains(tools, "list_available_slots", "dayOfWeek");
        assertDescriptionContains(tools, "create_booking", "nunca lo calcules");
        assertDescriptionContains(tools, "reschedule_booking", "nunca lo calcules");
        assertDescriptionContains(tools, "create_booking", "si el cliente mencionó explícitamente un día de semana");
        assertDescriptionContains(tools, "create_booking", "Si no mencionó un día, omite requestedWeekday");
        assertDescriptionContains(tools, "reschedule_booking", "Si el cliente mencionó explícitamente un día de semana");
        assertDescriptionContains(tools, "reschedule_booking", "Si no mencionó un día, omite requestedWeekday");
        assertParameterDescriptionContains(tools, "create_booking", "requestedWeekday", "omitir si no lo mencionó");
        assertParameterDescriptionContains(tools, "reschedule_booking", "requestedWeekday", "omitir si no mencionó uno");
    }

    @Test
    void whatsappVerificationRequiresExplicitSameNumberConfirmation() {
        JSONArray tools = RealtimeToolDefinitions.all();
        for (int i = 0; i < tools.length(); i++) {
            JSONObject tool = tools.getJSONObject(i);
            if (!"verify_caller_whatsapp".equals(tool.getString("name"))) continue;

            String description = tool.getString("description");
            JSONObject params = tool.getJSONObject("parameters");
            assertTrue(description.contains("SOLO después"));
            assertTrue(params.getJSONArray("required").toList().contains("confirmedSameNumber"));
            assertTrue(params.getJSONObject("properties")
                    .getJSONObject("confirmedSameNumber")
                    .getString("description")
                    .contains("explícitamente"));
            return;
        }
        throw new AssertionError("Tool not found: verify_caller_whatsapp");
    }

    @Test
    void endCallRequiresClearClosingIntentAndFarewellFirst() {
        JSONArray tools = RealtimeToolDefinitions.all();
        assertDescriptionContains(tools, "end_call", "despídete");
        assertDescriptionContains(tools, "end_call", "No la uses por un silencio breve");
    }

    private static void assertParameterDescriptionContains(
            JSONArray tools, String name, String parameter, String expected) {
        for (int i = 0; i < tools.length(); i++) {
            JSONObject tool = tools.getJSONObject(i);
            if (!name.equals(tool.getString("name"))) continue;
            String description = tool.getJSONObject("parameters")
                    .getJSONObject("properties")
                    .getJSONObject(parameter)
                    .getString("description");
            assertTrue(description.contains(expected),
                    () -> name + "." + parameter + " should mention " + expected);
            return;
        }
        throw new AssertionError("Tool not found: " + name);
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

    private static void assertParameterExists(JSONArray tools, String name, String parameter) {
        for (int i = 0; i < tools.length(); i++) {
            JSONObject tool = tools.getJSONObject(i);
            if (!name.equals(tool.getString("name"))) continue;
            assertTrue(tool.getJSONObject("parameters").getJSONObject("properties").has(parameter),
                    () -> name + " should expose " + parameter);
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
