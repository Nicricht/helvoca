package cl.helvoca.telephony.twilio;

import cl.helvoca.ai.live.OpenAiLiveSipService;
import cl.helvoca.call.CallSummaryService;
import cl.helvoca.telephony.twilio.trial.TrialConversationStateService;
import cl.helvoca.telephony.twilio.trial.TrialVoiceConversationService;
import cl.helvoca.telephony.twilio.trial.TrialVoiceProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

class GptLiveOnlyVoiceRoutingTest {
    private static final String SILENT_HANGUP =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response><Hangup/></Response>";

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

    private static void assertNoLegacyTts(String xml) {
        assertFalse(xml.contains("<Say"), "GPT-Live route must never emit Twilio <Say>");
        assertFalse(xml.contains("<Gather"), "GPT-Live route must never emit Twilio <Gather>");
        assertFalse(xml.toLowerCase().contains("polly"), "GPT-Live route must never select a Polly voice");
    }

    @Test
    void outboundTestRoutesDirectlyToGptLiveWithoutLegacyTts() {
        TwilioCallService calls = mock(TwilioCallService.class);
        TwimlFactory twiml = mock(TwimlFactory.class);
        OpenAiLiveSipService liveSip = mock(OpenAiLiveSipService.class);
        when(liveSip.isReady()).thenReturn(true);
        when(liveSip.twiml("+14355652512", "+56966939611"))
                .thenReturn("<Response><Dial><Sip>sip:proj_test@sip.api.openai.com;secure=true</Sip></Dial></Response>");

        var response = controller(calls, twiml, liveSip)
                .outboundTest("CA-OUT-LIVE", "+14355652512", "+56966939611");

        assertEquals(200, response.getStatusCode().value());
        assertNoLegacyTts(response.getBody());
        verify(liveSip).twiml("+14355652512", "+56966939611");
        verify(calls, never()).startOutboundTestCall(anyString(), anyString(), anyString());
        verifyNoInteractions(twiml);
    }

    @Test
    void productionVoiceRoutesDirectlyToGptLiveWithoutLegacyTts() {
        TwilioCallService calls = mock(TwilioCallService.class);
        TwimlFactory twiml = mock(TwimlFactory.class);
        OpenAiLiveSipService liveSip = mock(OpenAiLiveSipService.class);
        when(liveSip.isReady()).thenReturn(true);
        when(liveSip.twiml("+14355652512", "+56966939611"))
                .thenReturn("<Response><Dial><Sip>sip:proj_test@sip.api.openai.com;secure=true</Sip></Dial></Response>");

        var response = controller(calls, twiml, liveSip)
                .incoming("CA-IN-LIVE", "+56966939611", "+14355652512");

        assertEquals(200, response.getStatusCode().value());
        assertNoLegacyTts(response.getBody());
        verify(liveSip).twiml("+14355652512", "+56966939611");
        verify(calls, never()).startInboundCall(anyString(), anyString(), anyString());
        verifyNoInteractions(twiml);
    }

    @Test
    void retiredTrialVoiceCannotBeUsedAsAHiddenTestRoute() {
        TwilioCallService calls = mock(TwilioCallService.class);
        TwimlFactory twiml = mock(TwimlFactory.class);
        OpenAiLiveSipService liveSip = mock(OpenAiLiveSipService.class);

        var response = controller(calls, twiml, liveSip)
                .trialIncoming("CA-TRIAL-RETIRED", "+14355652512", "+56966939611");

        assertEquals(200, response.getStatusCode().value());
        assertEquals(SILENT_HANGUP, response.getBody());
        assertNoLegacyTts(response.getBody());
        verifyNoInteractions(calls, twiml, liveSip);
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
        assertEquals(SILENT_HANGUP, response.getBody());
        assertNoLegacyTts(response.getBody());
        verify(calls, never()).startOutboundTestCall(anyString(), anyString(), anyString());
        verifyNoInteractions(twiml);
    }

    @Test
    void productionVoiceFailsClosedInsteadOfUsingLegacyMediaWhenLiveIsUnavailable() {
        TwilioCallService calls = mock(TwilioCallService.class);
        TwimlFactory twiml = mock(TwimlFactory.class);
        OpenAiLiveSipService liveSip = mock(OpenAiLiveSipService.class);
        when(liveSip.isReady()).thenReturn(false);

        var response = controller(calls, twiml, liveSip)
                .incoming("CA-IN-NO-LIVE", "+56966939611", "+14355652512");

        assertEquals(200, response.getStatusCode().value());
        assertEquals(SILENT_HANGUP, response.getBody());
        assertNoLegacyTts(response.getBody());
        verify(calls, never()).startInboundCall(anyString(), anyString(), anyString());
        verifyNoInteractions(twiml);
    }
}
