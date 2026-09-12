package cl.helvoca.telephony.twilio;

import cl.helvoca.ai.live.OpenAiLiveSipService;
import cl.helvoca.call.CallSummaryService;
import cl.helvoca.telephony.twilio.trial.TrialConversationStateService;
import cl.helvoca.telephony.twilio.trial.TrialVoiceConversationService;
import cl.helvoca.telephony.twilio.trial.TrialVoiceProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class GptLiveOnlyVoiceRoutingTest {

    private static TwilioVoiceController controller(TwilioCallService calls,
                                                    TwimlFactory twiml,
                                                    OpenAiLiveSipService liveSip) {
        return new TwilioVoiceController(
                calls,
                twiml,
                liveSip,
                mock(TrialVoiceProperties.class),
                mock(TrialVoiceConversationService.class),
                mock(TrialConversationStateService.class),
                mock(CallSummaryService.class));
    }

    @Test
    void legacyTrialVoiceEndpointBridgesDirectlyToGptLive() {
        TwilioCallService calls = mock(TwilioCallService.class);
        TwimlFactory twiml = mock(TwimlFactory.class);
        OpenAiLiveSipService liveSip = mock(OpenAiLiveSipService.class);
        when(liveSip.isReady()).thenReturn(true);
        when(liveSip.twiml("+14355652512", "+56966939611"))
                .thenReturn("<Response><Dial><Sip>sip:live</Sip></Dial></Response>");

        var response = controller(calls, twiml, liveSip)
                .trialIncoming("CA-TRIAL-LIVE", "+14355652512", "+56966939611");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("<Response><Dial><Sip>sip:live</Sip></Dial></Response>", response.getBody());
        verify(liveSip).twiml("+14355652512", "+56966939611");
        verify(calls, never()).startTrialInboundCall(anyString(), anyString(), anyString());
        verify(twiml, never()).trialGather(anyString());
    }

    @Test
    void outboundTestFailsClosedInsteadOfUsingLegacyMediaOrTtsWhenLiveIsUnavailable() {
        TwilioCallService calls = mock(TwilioCallService.class);
        TwimlFactory twiml = mock(TwimlFactory.class);
        OpenAiLiveSipService liveSip = mock(OpenAiLiveSipService.class);
        when(liveSip.isReady()).thenReturn(false);

        var response = controller(calls, twiml, liveSip)
                .outboundTest("CA-NO-LIVE", "+14355652512", "+56966939611");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response><Hangup/></Response>", response.getBody());
        verify(calls, never()).startOutboundTestCall(anyString(), anyString(), anyString());
        verifyNoInteractions(twiml);
    }
}
