package cl.helvoca.ai.realtime;

import cl.helvoca.call.CallSummaryService;
import cl.helvoca.call.CallTranscriptService;
import cl.helvoca.voice.VoiceTransportSession;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.net.http.HttpClient;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OpenAiRealtimeTransferTest {

    @Test
    void successfulToolResultIsNotEnoughUntilCarrierAcceptsTransfer() throws Exception {
        UUID callId = UUID.randomUUID();
        VoiceTransportSession transport = mock(VoiceTransportSession.class);
        when(transport.transferToHuman("+56922222222")).thenReturn(false);
        OpenAiRealtimeBridge bridge = bridge(callId, transport);

        String toolResult = new JSONObject()
                .put("success", true)
                .put("data", new JSONObject()
                        .put("transferRequested", true)
                        .put("targetPhone", "+56922222222"))
                .put("error", JSONObject.NULL)
                .toString();

        String result = invokeTransfer(bridge, toolResult);

        assertNotNull(result);
        JSONObject parsed = new JSONObject(result);
        assertFalse(parsed.getBoolean("success"));
        assertEquals("HUMAN_TRANSFER_FAILED", parsed.getJSONObject("error").getString("code"));
        verify(transport).transferToHuman("+56922222222");
    }

    @Test
    void carrierAcceptanceCompletesTransferSideEffect() throws Exception {
        UUID callId = UUID.randomUUID();
        VoiceTransportSession transport = mock(VoiceTransportSession.class);
        when(transport.transferToHuman("+56922222222")).thenReturn(true);
        OpenAiRealtimeBridge bridge = bridge(callId, transport);

        String toolResult = new JSONObject()
                .put("success", true)
                .put("data", new JSONObject().put("targetPhone", "+56922222222"))
                .put("error", JSONObject.NULL)
                .toString();

        assertNull(invokeTransfer(bridge, toolResult));
        verify(transport).transferToHuman("+56922222222");
    }

    private static OpenAiRealtimeBridge bridge(UUID callId, VoiceTransportSession transport) {
        RealtimeCallContext context = new RealtimeCallContext(
                callId, UUID.randomUUID(), null, "+56911111111", "+17372508034", "MZstream");
        return new OpenAiRealtimeBridge(
                context,
                transport,
                new OpenAiRealtimeProperties(),
                mock(RealtimeToolService.class),
                mock(CallTranscriptService.class),
                mock(CallSummaryService.class),
                mock(HttpClient.class));
    }

    private static String invokeTransfer(OpenAiRealtimeBridge bridge, String result) throws Exception {
        Method method = OpenAiRealtimeBridge.class.getDeclaredMethod("executeHumanTransfer", String.class);
        method.setAccessible(true);
        return (String) method.invoke(bridge, result);
    }
}
