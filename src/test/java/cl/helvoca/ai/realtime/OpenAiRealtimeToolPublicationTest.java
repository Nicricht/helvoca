package cl.helvoca.ai.realtime;

import cl.helvoca.call.CallSummaryService;
import cl.helvoca.call.CallTranscriptService;
import cl.helvoca.voice.VoiceTransportSession;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenAiRealtimeToolPublicationTest {

    @Test
    void sessionPublishesOnlyTenantAllowedTools() {
        RealtimeCallContext context = new RealtimeCallContext(
                UUID.randomUUID(), UUID.randomUUID(), null,
                "+56911111111", "+17372508034", "MZstream");

        RealtimeToolService tools = mock(RealtimeToolService.class);
        when(tools.buildInstructions(context)).thenReturn("tenant rules");
        when(tools.toolDefinitions(context)).thenReturn(
                new JSONArray().put(RealtimeToolDefinitions.endCall()));

        WebSocket socket = mock(WebSocket.class);
        when(socket.sendText(any(CharSequence.class), anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(socket));

        OpenAiRealtimeBridge bridge = new OpenAiRealtimeBridge(
                context,
                mock(VoiceTransportSession.class),
                new OpenAiRealtimeProperties(),
                tools,
                mock(CallTranscriptService.class),
                mock(CallSummaryService.class),
                HttpClient.newHttpClient());

        bridge.onOpen(socket);

        ArgumentCaptor<CharSequence> sent = ArgumentCaptor.forClass(CharSequence.class);
        verify(socket, org.mockito.Mockito.atLeast(2)).sendText(sent.capture(), anyBoolean());

        JSONObject update = sent.getAllValues().stream()
                .map(CharSequence::toString)
                .map(JSONObject::new)
                .filter(json -> "session.update".equals(json.optString("type")))
                .findFirst()
                .orElseThrow();

        JSONArray published = update.getJSONObject("session").getJSONArray("tools");
        assertEquals(1, published.length());
        assertEquals("end_call", published.getJSONObject(0).getString("name"));
        assertFalse(published.toString().contains("create_booking"));
        verify(tools).toolDefinitions(context);
    }

    @Test
    void missingTenantToolSetFailsClosed() {
        RealtimeCallContext context = new RealtimeCallContext(
                UUID.randomUUID(), UUID.randomUUID(), null,
                "+56911111111", "+17372508034", "MZstream");

        RealtimeToolService tools = mock(RealtimeToolService.class);
        when(tools.buildInstructions(context)).thenReturn("tenant rules");
        when(tools.toolDefinitions(context)).thenReturn(null);

        WebSocket socket = mock(WebSocket.class);
        when(socket.sendText(any(CharSequence.class), anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(socket));

        OpenAiRealtimeBridge bridge = new OpenAiRealtimeBridge(
                context,
                mock(VoiceTransportSession.class),
                new OpenAiRealtimeProperties(),
                tools,
                mock(CallTranscriptService.class),
                mock(CallSummaryService.class),
                HttpClient.newHttpClient());

        bridge.onOpen(socket);

        ArgumentCaptor<CharSequence> sent = ArgumentCaptor.forClass(CharSequence.class);
        verify(socket, org.mockito.Mockito.atLeast(2)).sendText(sent.capture(), anyBoolean());

        JSONObject update = sent.getAllValues().stream()
                .map(CharSequence::toString)
                .map(JSONObject::new)
                .filter(json -> "session.update".equals(json.optString("type")))
                .findFirst()
                .orElseThrow();

        assertEquals(0, update.getJSONObject("session").getJSONArray("tools").length());
    }
}
