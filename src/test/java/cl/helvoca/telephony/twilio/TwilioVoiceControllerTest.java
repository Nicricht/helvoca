package cl.helvoca.telephony.twilio;

import cl.helvoca.call.CallSummaryService;
import cl.helvoca.telephony.twilio.trial.TrialVoiceConversationService;
import cl.helvoca.telephony.twilio.trial.TrialVoiceProperties;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class TwilioVoiceControllerTest {

    @Test
    void terminalStatusGeneratesSummaryForPersistedCall() {
        TwilioCallService calls = mock(TwilioCallService.class);
        TwimlFactory twiml = mock(TwimlFactory.class);
        TrialVoiceProperties trial = mock(TrialVoiceProperties.class);
        TrialVoiceConversationService conversation = mock(TrialVoiceConversationService.class);
        CallSummaryService summaries = mock(CallSummaryService.class);
        TwilioVoiceController controller = new TwilioVoiceController(calls, twiml, trial, conversation, summaries);

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
        TrialVoiceProperties trial = mock(TrialVoiceProperties.class);
        TrialVoiceConversationService conversation = mock(TrialVoiceConversationService.class);
        CallSummaryService summaries = mock(CallSummaryService.class);
        TwilioVoiceController controller = new TwilioVoiceController(calls, twiml, trial, conversation, summaries);

        UUID callId = UUID.randomUUID();
        when(calls.updateStatus("CA-ACTIVE", "in-progress", null)).thenReturn(callId);

        var response = controller.status("CA-ACTIVE", "in-progress", null);

        assertEquals(204, response.getStatusCode().value());
        verify(calls).updateStatus("CA-ACTIVE", "in-progress", null);
        verifyNoInteractions(summaries);
    }
}
