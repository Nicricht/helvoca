package cl.helvoca.simulator;

import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.ai.realtime.RealtimeToolService;
import cl.helvoca.booking.BookingStatus;
import cl.helvoca.call.CallTraceService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SimulatorToolExecutorTest {

    @Test
    void createsBookingOnlyInsideSimulatorState() {
        RealtimeToolService realTools = mock(RealtimeToolService.class);
        CallTraceService trace = mock(CallTraceService.class);
        SimulatorStateService state = new SimulatorStateService();
        SimulatorToolExecutor executor = new SimulatorToolExecutor(realTools, state, trace);

        UUID callId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        RealtimeCallContext context = new RealtimeCallContext(
                callId, businessId, null, "web-simulator", "web-simulator", "simulator:" + callId);
        state.start(callId);

        JSONObject registered = new JSONObject(executor.execute(
                context, "register_caller", new JSONObject().put("name", "Cliente prueba").toString()));
        assertTrue(registered.getBoolean("success"));

        JSONObject availability = new JSONObject()
                .put("success", true)
                .put("data", new JSONObject()
                        .put("serviceId", serviceId.toString())
                        .put("serviceName", "Consulta")
                        .put("startAt", "2030-01-10T15:00:00Z")
                        .put("endAt", "2030-01-10T15:30:00Z")
                        .put("localStart", "2030-01-10T12:00:00-03:00")
                        .put("available", true)
                        .put("withinBusinessHours", true))
                .put("error", JSONObject.NULL);
        when(realTools.execute(eq(context), eq("check_booking_availability"), anyString()))
                .thenReturn(availability.toString());

        JSONObject result = new JSONObject(executor.execute(
                context,
                "create_booking",
                new JSONObject()
                        .put("serviceId", serviceId.toString())
                        .put("startAt", "2030-01-10T15:00:00Z")
                        .toString()));

        assertTrue(result.getBoolean("success"));
        assertEquals(1, state.bookings(callId).size());
        assertEquals(serviceId, state.bookings(callId).getFirst().serviceId());
        verify(realTools).execute(eq(context), eq("check_booking_availability"), anyString());
        verify(trace, atLeastOnce()).recordTool(eq(businessId), eq(callId), anyString(), any(JSONObject.class));
    }

    @Test
    void blocksBookingUntilSimulatedCallerIsRegistered() {
        RealtimeToolService realTools = mock(RealtimeToolService.class);
        CallTraceService trace = mock(CallTraceService.class);
        SimulatorStateService state = new SimulatorStateService();
        SimulatorToolExecutor executor = new SimulatorToolExecutor(realTools, state, trace);

        UUID callId = UUID.randomUUID();
        RealtimeCallContext context = new RealtimeCallContext(
                callId, UUID.randomUUID(), null, "web-simulator", "web-simulator", "simulator:" + callId);
        state.start(callId);

        JSONObject result = new JSONObject(executor.execute(
                context,
                "create_booking",
                new JSONObject()
                        .put("serviceId", UUID.randomUUID().toString())
                        .put("startAt", "2030-01-10T15:00:00Z")
                        .toString()));

        assertFalse(result.getBoolean("success"));
        assertEquals("CUSTOMER_NOT_REGISTERED", result.getJSONObject("error").getString("code"));
        verifyNoInteractions(realTools);
    }

    @Test
    void completesCommercialDemoFlowWithoutPersistingRealMutations() {
        RealtimeToolService realTools = mock(RealtimeToolService.class);
        CallTraceService trace = mock(CallTraceService.class);
        SimulatorStateService state = new SimulatorStateService();
        SimulatorToolExecutor executor = new SimulatorToolExecutor(realTools, state, trace);

        UUID callId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        RealtimeCallContext context = new RealtimeCallContext(
                callId, businessId, null, "web-simulator", "web-simulator", "simulator:" + callId);
        state.start(callId);

        when(realTools.execute(eq(context), eq("get_business_information"), anyString()))
                .thenReturn(new JSONObject()
                        .put("success", true)
                        .put("data", new JSONObject().put("name", "Helvoca Demo Business"))
                        .put("error", JSONObject.NULL)
                        .toString());
        when(realTools.execute(eq(context), eq("list_services"), anyString()))
                .thenReturn(new JSONObject()
                        .put("success", true)
                        .put("data", new JSONObject().put("services", new JSONArray()
                                .put(new JSONObject()
                                        .put("id", serviceId.toString())
                                        .put("name", "Consulta inicial")
                                        .put("price", 19990))))
                        .put("error", JSONObject.NULL)
                        .toString());
        when(realTools.execute(eq(context), eq("check_booking_availability"), anyString()))
                .thenAnswer(invocation -> {
                    JSONObject args = new JSONObject(invocation.getArgument(2, String.class));
                    String startAt = args.getString("startAt");
                    boolean rescheduled = startAt.startsWith("2030-01-11");
                    return new JSONObject()
                            .put("success", true)
                            .put("data", new JSONObject()
                                    .put("serviceId", serviceId.toString())
                                    .put("serviceName", "Consulta inicial")
                                    .put("startAt", startAt)
                                    .put("endAt", rescheduled
                                            ? "2030-01-11T15:30:00Z"
                                            : "2030-01-10T15:30:00Z")
                                    .put("localStart", rescheduled
                                            ? "2030-01-11T12:00:00-03:00"
                                            : "2030-01-10T12:00:00-03:00")
                                    .put("available", true)
                                    .put("withinBusinessHours", true))
                            .put("error", JSONObject.NULL)
                            .toString();
                });

        JSONObject business = new JSONObject(executor.execute(context, "get_business_information", "{}"));
        assertTrue(business.getBoolean("success"));
        assertEquals("Helvoca Demo Business", business.getJSONObject("data").getString("name"));

        JSONObject services = new JSONObject(executor.execute(context, "list_services", "{}"));
        assertTrue(services.getBoolean("success"));
        assertEquals("Consulta inicial",
                services.getJSONObject("data").getJSONArray("services").getJSONObject(0).getString("name"));

        JSONObject callerBefore = new JSONObject(executor.execute(context, "find_caller", "{}"));
        assertFalse(callerBefore.getJSONObject("data").getBoolean("found"));

        JSONObject registered = new JSONObject(executor.execute(
                context, "register_caller", new JSONObject().put("name", "Cliente Demo").toString()));
        assertTrue(registered.getBoolean("success"));

        JSONObject availability = new JSONObject(executor.execute(
                context,
                "check_booking_availability",
                new JSONObject()
                        .put("serviceId", serviceId.toString())
                        .put("startAt", "2030-01-10T15:00:00Z")
                        .toString()));
        assertTrue(availability.getJSONObject("data").getBoolean("available"));

        JSONObject created = new JSONObject(executor.execute(
                context,
                "create_booking",
                new JSONObject()
                        .put("serviceId", serviceId.toString())
                        .put("startAt", "2030-01-10T15:00:00Z")
                        .toString()));
        assertTrue(created.getBoolean("success"));
        UUID bookingId = UUID.fromString(created.getJSONObject("data").getString("bookingId"));

        JSONObject listed = new JSONObject(executor.execute(context, "list_customer_bookings", "{}"));
        assertEquals(1, listed.getJSONObject("data").getJSONArray("bookings").length());

        JSONObject rescheduled = new JSONObject(executor.execute(
                context,
                "reschedule_booking",
                new JSONObject()
                        .put("bookingId", bookingId.toString())
                        .put("newStartAt", "2030-01-11T15:00:00Z")
                        .toString()));
        assertTrue(rescheduled.getBoolean("success"));
        assertEquals("2030-01-11T12:00:00-03:00",
                rescheduled.getJSONObject("data").getString("localStart"));

        JSONObject cancelled = new JSONObject(executor.execute(
                context,
                "cancel_booking",
                new JSONObject().put("bookingId", bookingId.toString()).toString()));
        assertTrue(cancelled.getBoolean("success"));
        assertEquals("CANCELLED", cancelled.getJSONObject("data").getString("status"));
        assertEquals(BookingStatus.CANCELLED, state.booking(callId, bookingId).status());

        verify(realTools).execute(eq(context), eq("get_business_information"), anyString());
        verify(realTools).execute(eq(context), eq("list_services"), anyString());
        verify(realTools, times(3)).execute(eq(context), eq("check_booking_availability"), anyString());
        verify(realTools, never()).execute(eq(context), eq("register_caller"), anyString());
        verify(realTools, never()).execute(eq(context), eq("create_booking"), anyString());
        verify(realTools, never()).execute(eq(context), eq("reschedule_booking"), anyString());
        verify(realTools, never()).execute(eq(context), eq("cancel_booking"), anyString());
        verify(trace, atLeastOnce()).recordTool(eq(businessId), eq(callId), anyString(), any(JSONObject.class));
    }

}
