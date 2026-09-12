package cl.helvoca.telephony.twilio;

import cl.helvoca.ai.live.OpenAiLiveSipService;
import cl.helvoca.call.CallSummaryService;
import cl.helvoca.telephony.twilio.trial.TrialConversationStateService;
import cl.helvoca.telephony.twilio.trial.TrialVoiceConversationService;
import cl.helvoca.telephony.twilio.trial.TrialVoiceProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class TwilioOutboundLiveFlowTest {

    @Test
    void outboundConsoleTestUsesTwilioNumberAsBusinessAndTesterAsCaller() {
        TwilioCallService calls = mock(TwilioCallService.class);
        TwimlFactory twiml = mock(TwimlFactory.class);
        OpenAiLiveSipService liveSip = mock(OpenAiLiveSipService.class);
        TrialVoiceProperties trial = mock(TrialVoiceProperties.class);
        TrialVoiceConversationService conversation = mock(TrialVoiceConversationService.class);
        TrialConversationStateService state = mock(TrialConversationStateService.class);
        CallSummaryService summaries = mock(CallSummaryService.class);
        TwilioVoiceController controller = new TwilioVoiceController(
                calls, twiml, liveSip, trial, conversation, state, summaries);

        when(liveSip.isReady()).thenReturn(true);
        when(liveSip.twiml("+14355652512", "+56911111111"))
                .thenReturn("<Response><Dial><Sip>sip:proj_test@sip.api.openai.com;secure=true</Sip></Dial></Response>");

        var response = controller.outboundTest("CA-OUTBOUND", "+14355652512", "+56911111111");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("<Response><Dial><Sip>sip:proj_test@sip.api.openai.com;secure=true</Sip></Dial></Response>",
                response.getBody());
        verify(liveSip).twiml("+14355652512", "+56911111111");
        verify(calls, never()).startOutboundTestCall(anyString(), anyString(), anyString());
    }
}
