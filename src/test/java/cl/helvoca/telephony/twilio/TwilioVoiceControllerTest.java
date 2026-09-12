package cl.helvoca.telephony.twilio;

import cl.helvoca.ai.live.OpenAiLiveSipService;
import cl.helvoca.call.CallSummaryService;
import cl.helvoca.telephony.twilio.trial.TrialConversationStateService;
import cl.helvoca.telephony.twilio.trial.TrialVoiceConversationService;
import cl.helvoca.telephony.twilio.trial.TrialVoiceProperties;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class TwilioVoiceControllerTest {
    private static final String SILENT_HANGUP =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response><Hangup/></Response>";

    @Test
    void readyLiveSipRoutesInboundWithoutOpeningLegacyMediaStream() {
        TwilioCallService calls = mock(TwilioCallService.class);
        TwimlFactory twiml = mock(TwimlFactory.class);
        OpenAiLiveSipService liveSip = mock(OpenAiLiveSipService.class);
        TrialVoiceProperties trial = mock(TrialVoiceProperties.class);
        TrialVoiceConversationService conversation = mock(TrialVoiceConversationService.class);
        TrialConversationStateService state = mock(TrialConversationStateService.class);
        CallSummaryService summaries = mock(CallSummaryService.class);
        TwilioVoiceController controller = new TwilioVoiceController(calls, twiml, liveSip, trial, conversation, state, summaries);
        when(liveSip.isReady()).thenReturn(true);
        when(liveSip.twiml("+14355652512", "+56911111111"))
                .thenReturn("<Response><Dial><Sip>sip:proj_test@sip.api.openai.com;secure=true</Sip></Dial></Response>");

        var response = controller.incoming("CA-LIVE", "+56911111111", "+14355652512");

        assertEquals(200, response.getStatusCode().value());
        verify(liveSip).twiml("+14355652512", "+56911111111");
        verify(calls, never()).startInboundCall(anyString(), anyString(), anyString());
        verifyNoInteractions(twiml, trial, conversation, state);
    }

    @Test
    void inboundFailsClosedWhenLiveSipIsUnavailable() {
        TwilioCallService calls = mock(TwilioCallService.class);
        TwimlFactory twiml = mock(TwimlFactory.class);
        OpenAiLiveSipService liveSip = mock(OpenAiLiveSipService.class);
        TrialVoiceProperties trial = mock(TrialVoiceProperties.class);
        TrialVoiceConversationService conversation = mock(TrialVoiceConversationService.class);
        TrialConversationStateService state = mock(TrialConversationStateService.class);
        CallSummaryService summaries = mock(CallSummaryService.class);
        TwilioVoiceController controller = new TwilioVoiceController(calls, twiml, liveSip, trial, conversation, state, summaries);
        when(liveSip.isReady()).thenReturn(false);

        var response = controller.incoming("CA-NO-LIVE", "+56911111111", "+14355652512");

        assertEquals(200, response.getStatusCode().value());
        assertEquals(SILENT_HANGUP, response.getBody());
        verify(calls, never()).startInboundCall(anyString(), anyString(), anyString());
        verifyNoInteractions(twiml, trial, conversation, state);
    }

    @Test
    void retiredTrialVoiceCannotStartAConversation() {
        TwilioCallService calls = mock(TwilioCallService.class);
        TwimlFactory twiml = mock(TwimlFactory.class);
        OpenAiLiveSipService liveSip = mock(OpenAiLiveSipService.class);
        TrialVoiceProperties trial = mock(TrialVoiceProperties.class);
        TrialVoiceConversationService conversation = mock(TrialVoiceConversationService.class);
        TrialConversationStateService state = mock(TrialConversationStateService.class);
        CallSummaryService summaries = mock(CallSummaryService.class);
        TwilioVoiceController controller = new TwilioVoiceController(calls, twiml, liveSip, trial, conversation, state, summaries);

        var response = controller.trialIncoming("CA-OLD-TRIAL", "+14355652512", "+56911111111");

        assertEquals(200, response.getStatusCode().value());
        assertEquals(SILENT_HANGUP, response.getBody());
        verifyNoInteractions(calls, twiml, liveSip, trial, conversation, state, summaries);
    }

    @Test
    void retiredTrialGatherCannotInvokeLegacyTtsOrConversationService() {
        TwilioCallService calls = mock(TwilioCallService.class);
        TwimlFactory twiml = mock(TwimlFactory.class);
        OpenAiLiveSipService liveSip = mock(OpenAiLiveSipService.class);
        TrialVoiceProperties trial = mock(TrialVoiceProperties.class);
        TrialVoiceConversationService conversation = mock(TrialVoiceConversationService.class);
        TrialConversationStateService state = mock(TrialConversationStateService.class);
        CallSummaryService summaries = mock(CallSummaryService.class);
        TwilioVoiceController controller = new TwilioVoiceController(calls, twiml, liveSip, trial, conversation, state, summaries);

        var response = controller.trialGather("CA-OLD-GATHER", "quiero reservar");

        assertEquals(200, response.getStatusCode().value());
        assertEquals(SILENT_HANGUP, response.getBody());
        verifyNoInteractions(calls, twiml, liveSip, trial, conversation, state, summaries);
    }

    @Test
    void retiredTrialTransferCallbackCannotResumeLegacyGatherFlow() {
        TwilioCallService calls = mock(TwilioCallService.class);
        TwimlFactory twiml = mock(TwimlFactory.class);
        OpenAiLiveSipService liveSip = mock(OpenAiLiveSipService.class);
        TrialVoiceProperties trial = mock(TrialVoiceProperties.class);
        TrialVoiceConversationService conversation = mock(TrialVoiceConversationService.class);
        TrialConversationStateService state = mock(TrialConversationStateService.class);
        CallSummaryService summaries = mock(CallSummaryService.class);
        TwilioVoiceController controller = new TwilioVoiceController(calls, twiml, liveSip, trial, conversation, state, summaries);

        var response = controller.trialTransferResult("CA-OLD-DIAL", "no-answer");

        assertEquals(200, response.getStatusCode().value());
        assertEquals(SILENT_HANGUP, response.getBody());
        verifyNoInteractions(calls, twiml, liveSip, trial, conversation, state, summaries);
    }

    @Test
    void retiredTrialStreamCallbackIsIgnored() {
        TwilioCallService calls = mock(TwilioCallService.class);
        TwimlFactory twiml = mock(TwimlFactory.class);
        OpenAiLiveSipService liveSip = mock(OpenAiLiveSipService.class);
        TrialVoiceProperties trial = mock(TrialVoiceProperties.class);
        TrialVoiceConversationService conversation = mock(TrialVoiceConversationService.class);
        TrialConversationStateService state = mock(TrialConversationStateService.class);
        CallSummaryService summaries = mock(CallSummaryService.class);
        TwilioVoiceController controller = new TwilioVoiceController(calls, twiml, liveSip, trial, conversation, state, summaries);

        var response = controller.trialStreamStatus("MZ-OLD", "stream-error", "CA-OLD", "legacy");

        assertEquals(204, response.getStatusCode().value());
        verifyNoInteractions(calls, twiml, liveSip, trial, conversation, state, summaries);
    }

    @Test
    void terminalStatusGeneratesSummaryForPersistedCall() {
        TwilioCallService calls = mock(TwilioCallService.class);
        TwimlFactory twiml = mock(TwimlFactory.class);
        OpenAiLiveSipService liveSip = mock(OpenAiLiveSipService.class);
        TrialVoiceProperties trial = mock(TrialVoiceProperties.class);
        TrialVoiceConversationService conversation = mock(TrialVoiceConversationService.class);
        TrialConversationStateService state = mock(TrialConversationStateService.class);
        CallSummaryService summaries = mock(CallSummaryService.class);
        TwilioVoiceController controller = new TwilioVoiceController(calls, twiml, liveSip, trial, conversation, state, summaries);

        UUID callId = UUID.randomUUID();
        when(calls.updateStatus("CA-TERMINAL", "completed", 42)).thenReturn(callId);

        var response = controller.status("CA-TERMINAL", "completed", 42);

        assertEquals(204, response.getStatusCode().value());
        verify(calls).updateStatus("CA-TERMINAL", "completed", 42);
        verify(summaries).generate(callId);
    }

    @Test
    void nonTerminalStatusDoesNotGenerateSummary() {
        TwilioCallService calls = mock(TwilioCallService.class);
        TwimlFactory twiml = mock(TwimlFactory.class);
        OpenAiLiveSipService liveSip = mock(OpenAiLiveSipService.class);
        TrialVoiceProperties trial = mock(TrialVoiceProperties.class);
        TrialVoiceConversationService conversation = mock(TrialVoiceConversationService.class);
        TrialConversationStateService state = mock(TrialConversationStateService.class);
        CallSummaryService summaries = mock(CallSummaryService.class);
        TwilioVoiceController controller = new TwilioVoiceController(calls, twiml, liveSip, trial, conversation, state, summaries);

        UUID callId = UUID.randomUUID();
        when(calls.updateStatus("CA-ACTIVE", "in-progress", null)).thenReturn(callId);

        var response = controller.status("CA-ACTIVE", "in-progress", null);

        assertEquals(204, response.getStatusCode().value());
        verify(calls).updateStatus("CA-ACTIVE", "in-progress", null);
        verifyNoInteractions(summaries);
    }
}
