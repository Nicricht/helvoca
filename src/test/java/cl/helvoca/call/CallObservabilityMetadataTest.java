package cl.helvoca.call;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CallObservabilityMetadataTest {

    @Test
    void callResponseIncludesPersistedAiModel() {
        CallSession call = new CallSession();
        call.setAiProvider("openai");
        call.setAiModel("gpt-realtime-2.1");

        CallResponse response = CallResponse.from(call);

        assertEquals("openai", response.aiProvider());
        assertEquals("gpt-realtime-2.1", response.aiModel());
    }

    @Test
    void callActionResponseIncludesMeasuredDuration() {
        CallAction action = new CallAction();
        action.setActionType("SERVICES_LISTED");
        action.setSuccess(true);
        action.setDurationMs(42L);

        CallActionResponse response = CallActionResponse.from(action);

        assertEquals(42L, response.durationMs());
    }
}
