package cl.helvoca.simulator;

import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.ai.realtime.RealtimeToolService;
import cl.helvoca.call.CallTraceService;
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
}
