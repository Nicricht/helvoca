package cl.helvoca.telephony.twilio;

import cl.helvoca.call.CallSummaryService;
import cl.helvoca.voice.VoiceCallRouter;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class TwilioVoiceControllerTest {
    private static final String SILENT_HANGUP =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response><Hangup/></Response>";
    private static final String CALL_SID = "CA0123456789abcdef0123456789abcdef";

    private static TwilioVoiceController controller(TwilioCallService calls,
                                                    VoiceCallRouter router,
                                                    CallSummaryService summaries) {
        return new TwilioVoiceController(calls, router, summaries);
    }

    @Test
    void inboundUsesRouterDecisionWithoutKnowingProviderProtocol() {
        TwilioCallService calls = mock(TwilioCallService.class);
        VoiceCallRouter router = mock(VoiceCallRouter.class);
        CallSummaryService summaries = mock(CallSummaryService.class);
        String twiml = "<Response><Connect><Stream url=\"wss://example/ws\"/></Connect></Response>";
        when(router.route("+14355652512", "+56911111111", CALL_SID))
                .thenReturn(Optional.of(new VoiceCallRouter.RouteDecision(
                        "gemini", VoiceCallRouter.RouteMode.MEDIA_STREAM, twiml)));

        var response = controller(calls, router, summaries)
                .incoming(CALL_SID, "+56911111111", "+14355652512");

        assertEquals(200, response.getStatusCode().value());
        assertEquals(twiml, response.getBody());
        verify(router).route("+14355652512", "+56911111111", CALL_SID);
        verifyNoInteractions(calls, summaries);
    }

    @Test
    void inboundFailsClosedWhenNoVoiceProviderIsHealthy() {
        TwilioCallService calls = mock(TwilioCallService.class);
        VoiceCallRouter router = mock(VoiceCallRouter.class);
        CallSummaryService summaries = mock(CallSummaryService.class);
        when(router.route("+14355652512", "+56911111111", CALL_SID)).thenReturn(Optional.empty());

        var response = controller(calls, router, summaries)
                .incoming(CALL_SID, "+56911111111", "+14355652512");

        assertEquals(SILENT_HANGUP, response.getBody());
        verifyNoInteractions(calls, summaries);
    }

    @Test
    void streamErrorMarksMediaStreamStopped() {
        TwilioCallService calls = mock(TwilioCallService.class);
        VoiceCallRouter router = mock(VoiceCallRouter.class);
        CallSummaryService summaries = mock(CallSummaryService.class);

        var response = controller(calls, router, summaries)
                .streamStatus("MZ-1", "stream-error", CALL_SID, "network");

        assertEquals(204, response.getStatusCode().value());
        verify(calls).markStreamStopped("MZ-1");
    }

    @Test
    void terminalStatusGeneratesSummaryForPersistedCall() {
        TwilioCallService calls = mock(TwilioCallService.class);
        VoiceCallRouter router = mock(VoiceCallRouter.class);
        CallSummaryService summaries = mock(CallSummaryService.class);
        UUID callId = UUID.randomUUID();
        when(calls.updateStatus(CALL_SID, "completed", 42)).thenReturn(callId);

        var response = controller(calls, router, summaries).status(CALL_SID, "completed", 42);

        assertEquals(204, response.getStatusCode().value());
        verify(summaries).generate(callId);
    }

    @Test
    void nonTerminalStatusDoesNotGenerateSummary() {
        TwilioCallService calls = mock(TwilioCallService.class);
        VoiceCallRouter router = mock(VoiceCallRouter.class);
        CallSummaryService summaries = mock(CallSummaryService.class);
        when(calls.updateStatus(CALL_SID, "in-progress", null)).thenReturn(UUID.randomUUID());

        var response = controller(calls, router, summaries).status(CALL_SID, "in-progress", null);

        assertEquals(204, response.getStatusCode().value());
        verifyNoInteractions(summaries);
    }
}
