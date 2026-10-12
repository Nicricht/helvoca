package cl.helvoca.ai.gemini;

import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.ai.realtime.RealtimeToolService;
import cl.helvoca.call.CallCertificationService;
import cl.helvoca.call.CallSummaryService;
import cl.helvoca.call.CallTranscriptService;
import cl.helvoca.telephony.CallLifecycleService;
import cl.helvoca.voice.VoiceProviderHealthRegistry;
import cl.helvoca.voice.VoiceTransportSession;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GeminiLiveToolReplayContractTest {
    @Test
    void duplicatedBookingToolIdReceivesSameResponseWithoutSecondMutation() {
        RealtimeCallContext context = context();
        RealtimeToolService tools = mock(RealtimeToolService.class);
        AtomicInteger backendWrites = new AtomicInteger();
        when(tools.execute(eq(context), eq("create_booking"), anyString()))
                .thenAnswer(invocation -> new JSONObject().put("success", true)
                        .put("data", new JSONObject()
                                .put("bookingId", "booking-1")
                                .put("writes", backendWrites.incrementAndGet()))
                        .toString());
        WebSocket socket = socket();
        GeminiLiveVoiceSession session = session(context, tools);
        session.onOpen(socket);

        session.onText(socket, event("action-1", "create_booking",
                new JSONObject().put("serviceId", "service-1").put("startAt", "later")), true);
        session.onText(socket, event("action-1", "create_booking",
                new JSONObject().put("startAt", "later").put("serviceId", "service-1")), true);

        verify(tools, times(1)).execute(eq(context), eq("create_booking"), anyString());
        List<JSONObject> replies = replies(socket);
        assertEquals(2, replies.size());
        assertEquals("action-1", replies.get(0).getString("id"));
        assertEquals("action-1", replies.get(1).getString("id"));
        assertEquals(replies.get(0).getJSONObject("response").toString(),
                replies.get(1).getJSONObject("response").toString());
        assertEquals(1, backendWrites.get());
    }

    @Test
    void conflictingReusedIdReturnsErrorInsteadOfCancellingAnotherBooking() {
        RealtimeCallContext context = context();
        RealtimeToolService tools = mock(RealtimeToolService.class);
        when(tools.execute(eq(context), eq("create_booking"), anyString())).thenReturn(
                new JSONObject().put("success", true)
                        .put("data", new JSONObject().put("requiresConfirmation", true)).toString());
        WebSocket socket = socket();
        GeminiLiveVoiceSession session = session(context, tools);
        session.onOpen(socket);

        session.onText(socket, event("action-2", "create_booking",
                new JSONObject().put("startAt", "later")), true);
        session.onText(socket, event("action-2", "cancel_booking",
                new JSONObject().put("bookingId", "different")), true);

        verify(tools, times(1)).execute(eq(context), eq("create_booking"), anyString());
        verify(tools, never()).execute(eq(context), eq("cancel_booking"), anyString());
        List<JSONObject> replies = replies(socket);
        assertEquals(2, replies.size());
        JSONObject rejection = replies.get(1).getJSONObject("response").getJSONObject("result");
        assertFalse(rejection.getBoolean("success"));
        assertEquals("TOOL_CALL_ID_CONFLICT", rejection.getJSONObject("error").getString("code"));
    }

    @Test
    void newToolIdsAlwaysRecheckAppointmentAvailability() {
        RealtimeCallContext context = context();
        RealtimeToolService tools = mock(RealtimeToolService.class);
        AtomicInteger requests = new AtomicInteger();
        when(tools.execute(eq(context), eq("check_booking_availability"), anyString()))
                .thenAnswer(invocation -> new JSONObject()
                        .put("success", true)
                        .put("data", new JSONObject().put("available", requests.incrementAndGet() == 1))
                        .toString());
        WebSocket socket = socket();
        GeminiLiveVoiceSession session = session(context, tools);
        session.onOpen(socket);

        JSONObject args = new JSONObject().put("serviceId", "service-1").put("startAt", "later");
        session.onText(socket, event("availability-1", "check_booking_availability", args), true);
        session.onText(socket, event("availability-2", "check_booking_availability", args), true);

        verify(tools, times(2)).execute(eq(context), eq("check_booking_availability"), anyString());
        List<JSONObject> replies = replies(socket);
        assertEquals(2, replies.size());
        assertTrue(replies.get(0).getJSONObject("response").getJSONObject("result")
                .getJSONObject("data").getBoolean("available"));
        assertFalse(replies.get(1).getJSONObject("response").getJSONObject("result")
                .getJSONObject("data").getBoolean("available"));
    }

    @Test
    void repeatedServiceCatalogReadsUseShortSafeCacheForDistinctIds() {
        RealtimeCallContext context = context();
        RealtimeToolService tools = mock(RealtimeToolService.class);
        when(tools.execute(eq(context), eq("list_services"), anyString())).thenReturn(
                new JSONObject().put("success", true)
                        .put("data", new JSONObject().put("services", new JSONArray())).toString());
        WebSocket socket = socket();
        GeminiLiveVoiceSession session = session(context, tools);
        session.onOpen(socket);

        session.onText(socket, event("services-1", "list_services", new JSONObject()), true);
        session.onText(socket, event("services-2", "list_services", new JSONObject()), true);

        verify(tools, times(1)).execute(eq(context), eq("list_services"), anyString());
        assertEquals(2, replies(socket).size());
    }

    @Test
    void malformedFunctionCallsAreNotExecutedAndDoNotGeneratePhantomResponses() {
        RealtimeCallContext context = context();
        RealtimeToolService tools = mock(RealtimeToolService.class);
        WebSocket socket = socket();
        GeminiLiveVoiceSession session = session(context, tools);
        session.onOpen(socket);
        session.onText(socket, new JSONObject().put("toolCall", new JSONObject()
                .put("functionCalls", new JSONArray()
                        .put(new JSONObject().put("name", "create_booking").put("id", ""))
                        .put(new JSONObject().put("name", "").put("id", "a"))
                        .put(new JSONObject().put("id", "missing-name"))
                        .put(new JSONObject().put("name", "missing-id"))))
                .toString(), true);

        verify(tools, never()).execute(any(), anyString(), anyString());
        assertTrue(replies(socket).isEmpty());
    }

    private static GeminiLiveVoiceSession session(RealtimeCallContext context,
                                                    RealtimeToolService tools) {
        return new GeminiLiveVoiceSession(
                context, mock(VoiceTransportSession.class), new GeminiLiveProperties(),
                tools, mock(CallTranscriptService.class), mock(CallSummaryService.class),
                mock(CallLifecycleService.class), mock(CallCertificationService.class),
                new VoiceProviderHealthRegistry(), HttpClient.newHttpClient());
    }

    private static WebSocket socket() {
        WebSocket socket = mock(WebSocket.class);
        when(socket.sendText(any(CharSequence.class), eq(true)))
                .thenReturn(CompletableFuture.completedFuture(socket));
        return socket;
    }

    private static RealtimeCallContext context() {
        return new RealtimeCallContext(UUID.randomUUID(), UUID.randomUUID(), null,
                "+56911111111", "+14355652512", "MZ-part5", null);
    }

    private static String event(String id, String name, JSONObject args) {
        return new JSONObject().put("toolCall", new JSONObject()
                .put("functionCalls", new JSONArray().put(new JSONObject()
                        .put("id", id).put("name", name).put("args", args)))).toString();
    }

    private static List<JSONObject> replies(WebSocket socket) {
        ArgumentCaptor<CharSequence> sent = ArgumentCaptor.forClass(CharSequence.class);
        verify(socket, atLeastOnce()).sendText(sent.capture(), eq(true));
        List<JSONObject> out = new ArrayList<>();
        for (CharSequence payload : sent.getAllValues()) {
            JSONObject frame = new JSONObject(payload.toString());
            if (!frame.has("toolResponse")) continue;
            JSONArray responses = frame.getJSONObject("toolResponse").getJSONArray("functionResponses");
            for (int i = 0; i < responses.length(); i++) out.add(responses.getJSONObject(i));
        }
        return out;
    }
}
