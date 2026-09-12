package cl.helvoca.telephony.twilio;

import cl.helvoca.ai.live.OpenAiLiveSipService;
import cl.helvoca.call.CallSummaryService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class TwilioVoiceControllerTest {
    private static final String SILENT_HANGUP =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response><Hangup/></Response>";
    private static final String CALL_SID = "CA0123456789abcdef0123456789abcdef";

    private static TwilioVoiceController controller(TwilioCallService calls,
                                                    OpenAiLiveSipService liveSip,
                                                    CallSummaryService summaries) {
        return new TwilioVoiceController(calls, liveSip, summaries);
    }

    @Test
    void readyLiveSipRoutesInboundWithoutOpeningMediaStream() {
        TwilioCallService calls = mock(TwilioCallService.class);
        OpenAiLiveSipService liveSip = mock(OpenAiLiveSipService.class);
        CallSummaryService summaries = mock(CallSummaryService.class);
        when(liveSip.isReady()).thenReturn(true);
        when(liveSip.twiml("+14355652512", "+56911111111", CALL_SID))
                .thenReturn("<Response><Dial><Sip>sip:proj_test@sip.api.openai.com;secure=true</Sip></Dial></Response>");

        var response = controller(calls, liveSip, summaries)
                .incoming(CALL_SID, "+56911111111", "+14355652512");

        assertEquals(200, response.getStatusCode().value());
        verify(liveSip).twiml("+14355652512", "+56911111111", CALL_SID);
        verifyNoInteractions(calls, summaries);
    }

    @Test
    void inboundFailsClosedWhenLiveSipIsUnavailable() {
        TwilioCallService calls = mock(TwilioCallService.class);
        OpenAiLiveSipService liveSip = mock(OpenAiLiveSipService.class);
        CallSummaryService summaries = mock(CallSummaryService.class);
        when(liveSip.isReady()).thenReturn(false);

        var response = controller(calls, liveSip, summaries)
                .incoming(CALL_SID, "+56911111111", "+14355652512");

        assertEquals(SILENT_HANGUP, response.getBody());
        verifyNoInteractions(calls, summaries);
    }

    @Test
    void terminalStatusGeneratesSummaryForPersistedCall() {
        TwilioCallService calls = mock(TwilioCallService.class);
        OpenAiLiveSipService liveSip = mock(OpenAiLiveSipService.class);
        CallSummaryService summaries = mock(CallSummaryService.class);
        UUID callId = UUID.randomUUID();
        when(calls.updateStatus(CALL_SID, "completed", 42)).thenReturn(callId);

        var response = controller(calls, liveSip, summaries).status(CALL_SID, "completed", 42);

        assertEquals(204, response.getStatusCode().value());
        verify(summaries).generate(callId);
    }

    @Test
    void nonTerminalStatusDoesNotGenerateSummary() {
        TwilioCallService calls = mock(TwilioCallService.class);
        OpenAiLiveSipService liveSip = mock(OpenAiLiveSipService.class);
        CallSummaryService summaries = mock(CallSummaryService.class);
        when(calls.updateStatus(CALL_SID, "in-progress", null)).thenReturn(UUID.randomUUID());

        var response = controller(calls, liveSip, summaries).status(CALL_SID, "in-progress", null);

        assertEquals(204, response.getStatusCode().value());
        verifyNoInteractions(summaries);
    }
}
