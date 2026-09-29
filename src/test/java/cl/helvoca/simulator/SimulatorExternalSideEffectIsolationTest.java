package cl.helvoca.simulator;

import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.ai.realtime.RealtimeToolService;
import cl.helvoca.call.CallTraceService;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SimulatorExternalSideEffectIsolationTest {

    @Test
    void externalSideEffectToolsNeverEscapeTheSafeSimulatorBoundary() {
        RealtimeToolService realTools = mock(RealtimeToolService.class);
        CallTraceService trace = mock(CallTraceService.class);
        SimulatorStateService state = new SimulatorStateService();
        SimulatorToolExecutor executor = new SimulatorToolExecutor(realTools, state, trace);

        UUID callId = UUID.randomUUID();
        RealtimeCallContext context = new RealtimeCallContext(
                callId, UUID.randomUUID(), null,
                "+56911111111", "web-simulator", "simulator:" + callId);
        state.start(callId);

        assertSimulatedSuccess(executor.execute(
                context,
                "verify_caller_whatsapp",
                new JSONObject().put("confirmedSameNumber", true).toString()));

        assertSimulatedSuccess(executor.execute(
                context,
                "send_whatsapp_operation",
                new JSONObject()
                        .put("operationId", UUID.randomUUID().toString())
                        .put("purpose", "QUOTE")
                        .toString()));

        assertSimulatedSuccess(executor.execute(context, "transfer_to_human", "{}"));
        assertSimulatedSuccess(executor.execute(context, "end_call", "{}"));

        verifyNoInteractions(realTools);
    }

    private static void assertSimulatedSuccess(String raw) {
        assertNotNull(raw, "Simulator tools must return an isolated simulator result");
        JSONObject result = new JSONObject(raw);
        assertTrue(result.getBoolean("success"));
        assertTrue(result.getJSONObject("data").getBoolean("simulated"));
    }
}
