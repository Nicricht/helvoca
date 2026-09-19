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

class DemoConversationScenariosTest {

    private record Fixture(
            RealtimeToolService realTools,
            CallTraceService trace,
            SimulatorStateService state,
            SimulatorToolExecutor executor,
            RealtimeCallContext context,
            UUID serviceId
    ) {}

    private Fixture fixture() {
        RealtimeToolService realTools = mock(RealtimeToolService.class);
        CallTraceService trace = mock(CallTraceService.class);
        SimulatorStateService state = new SimulatorStateService();
        UUID callId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        RealtimeCallContext context = new RealtimeCallContext(
                callId, businessId, null, "web-simulator", "web-simulator", "simulator:" + callId);
        state.start(callId);
        return new Fixture(realTools, trace, state, new SimulatorToolExecutor(realTools, state, trace), context, serviceId);
    }

    @Test
    void conversation1AnswersSimpleBusinessInformationFromAuthoritativeBackend() {
        Fixture f = fixture();
        when(f.realTools().execute(eq(f.context()), eq("get_business_information"), anyString()))
                .thenReturn(success(new JSONObject()
                        .put("name", "Helvoca Demo Business")
                        .put("timezone", "America/Santiago")));

        JSONObject result = new JSONObject(f.executor().execute(f.context(), "get_business_information", "{}"));

        assertTrue(result.getBoolean("success"));
        assertEquals("Helvoca Demo Business", result.getJSONObject("data").getString("name"));
        assertEquals("America/Santiago", result.getJSONObject("data").getString("timezone"));
        verify(f.realTools()).execute(eq(f.context()), eq("get_business_information"), eq("{}"));
    }

    @Test
    void conversation2AnswersServiceAndPriceFromAuthoritativeBackend() {
        Fixture f = fixture();
        when(f.realTools().execute(eq(f.context()), eq("list_services"), anyString()))
                .thenReturn(success(new JSONObject().put("services", new JSONArray()
                        .put(new JSONObject()
                                .put("id", f.serviceId().toString())
                                .put("name", "Consulta inicial")
                                .put("price", 19990)))));

        JSONObject result = new JSONObject(f.executor().execute(f.context(), "list_services", "{}"));

        assertTrue(result.getBoolean("success"));
        JSONObject service = result.getJSONObject("data").getJSONArray("services").getJSONObject(0);
        assertEquals("Consulta inicial", service.getString("name"));
        assertEquals(19990, service.getInt("price"));
        verify(f.realTools()).execute(eq(f.context()), eq("list_services"), eq("{}"));
    }

    @Test
    void conversation3CompletesPrimaryBookingActionInsideSafeSimulatorState() {
        Fixture f = fixture();
        when(f.realTools().execute(eq(f.context()), eq("check_booking_availability"), anyString()))
                .thenReturn(success(new JSONObject()
                        .put("serviceId", f.serviceId().toString())
                        .put("serviceName", "Consulta inicial")
                        .put("startAt", "2030-01-10T15:00:00Z")
                        .put("endAt", "2030-01-10T15:30:00Z")
                        .put("localStart", "2030-01-10T12:00:00-03:00")
                        .put("available", true)
                        .put("withinBusinessHours", true)));

        JSONObject registered = new JSONObject(f.executor().execute(
                f.context(), "register_caller", new JSONObject().put("name", "Cliente Demo").toString()));
        assertTrue(registered.getBoolean("success"));

        JSONObject booking = new JSONObject(f.executor().execute(
                f.context(),
                "create_booking",
                new JSONObject()
                        .put("serviceId", f.serviceId().toString())
                        .put("startAt", "2030-01-10T15:00:00Z")
                        .toString()));

        assertTrue(booking.getBoolean("success"));
        assertEquals(1, f.state().bookings(f.context().callId()).size());
        verify(f.realTools()).execute(eq(f.context()), eq("check_booking_availability"), anyString());
        verify(f.realTools(), never()).execute(eq(f.context()), eq("register_caller"), anyString());
        verify(f.realTools(), never()).execute(eq(f.context()), eq("create_booking"), anyString());
    }

    @Test
    void conversation4CorrectionReplacesPreviousSimulatedCustomerData() {
        Fixture f = fixture();

        JSONObject first = new JSONObject(f.executor().execute(
                f.context(),
                "register_caller",
                new JSONObject().put("name", "Cliente Dmeo").put("email", "mal@example.invalid").toString()));
        UUID firstId = UUID.fromString(first.getJSONObject("data").getString("customerId"));

        JSONObject corrected = new JSONObject(f.executor().execute(
                f.context(),
                "register_caller",
                new JSONObject().put("name", "Cliente Demo").put("email", "bien@example.invalid").toString()));
        UUID correctedId = UUID.fromString(corrected.getJSONObject("data").getString("customerId"));

        JSONObject found = new JSONObject(f.executor().execute(f.context(), "find_caller", "{}"));
        JSONObject data = found.getJSONObject("data");

        assertEquals(firstId, correctedId);
        assertTrue(data.getBoolean("found"));
        assertEquals("Cliente Demo", data.getString("name"));
        assertEquals("bien@example.invalid", data.getString("email"));
        verifyNoInteractions(f.realTools());
    }

    @Test
    void conversation5UnknownQuestionIsRecordedWithoutInventingAnAnswer() {
        Fixture f = fixture();
        when(f.realTools().execute(eq(f.context()), eq("search_knowledge"), anyString()))
                .thenReturn(success(new JSONObject().put("results", new JSONArray())));

        JSONObject search = new JSONObject(f.executor().execute(
                f.context(),
                "search_knowledge",
                new JSONObject().put("query", "¿Tienen estacionamiento techado?").toString()));

        assertTrue(search.getBoolean("success"));
        assertTrue(search.getJSONObject("data").getJSONArray("results").isEmpty());

        JSONObject recorded = new JSONObject(f.executor().execute(
                f.context(),
                "record_unanswered_question",
                new JSONObject().put("question", "¿Tienen estacionamiento techado?").toString()));

        assertTrue(recorded.getBoolean("success"));
        assertEquals("SIMULATED", recorded.getJSONObject("data").getString("status"));
        assertEquals("¿Tienen estacionamiento techado?", recorded.getJSONObject("data").getString("question"));
        verify(f.realTools()).execute(eq(f.context()), eq("search_knowledge"), anyString());
        verify(f.realTools(), never()).execute(eq(f.context()), eq("record_unanswered_question"), anyString());
    }

    private static String success(JSONObject data) {
        return new JSONObject()
                .put("success", true)
                .put("data", data)
                .put("error", JSONObject.NULL)
                .toString();
    }
}
