package cl.helvoca.telephony.twilio;

import cl.helvoca.ai.live.OpenAiLiveSipService;
import cl.helvoca.call.CallSummaryService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

class GptLiveOnlyVoiceRoutingTest {
    private static final String SILENT_HANGUP =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response><Hangup/></Response>";
    private static final String INBOUND_SID = "CA11111111111111111111111111111111";
    private static final String OUTBOUND_SID = "CA22222222222222222222222222222222";

    private static TwilioVoiceController controller(TwilioCallService calls,
                                                    OpenAiLiveSipService liveSip) {
        return new TwilioVoiceController(calls, liveSip, mock(CallSummaryService.class));
    }

    private static void assertNoLegacyVoice(String xml) {
        assertFalse(xml.contains("<Say"));
        assertFalse(xml.contains("<Gather"));
        assertFalse(xml.contains("<Stream"));
        assertFalse(xml.toLowerCase().contains("polly"));
    }

    @Test
    void outboundTestRoutesDirectlyToGptLiveWithoutLegacyVoice() {
        TwilioCallService calls = mock(TwilioCallService.class);
        OpenAiLiveSipService liveSip = mock(OpenAiLiveSipService.class);
        when(liveSip.isReady()).thenReturn(true);
        when(liveSip.twiml("+14355652512", "+56966939611", OUTBOUND_SID))
                .thenReturn("<Response><Dial><Sip>sip:proj_test@sip.api.openai.com;secure=true</Sip></Dial></Response>");

        var response = controller(calls, liveSip)
                .outboundTest(OUTBOUND_SID, "+14355652512", "+56966939611");

        assertEquals(200, response.getStatusCode().value());
        assertNoLegacyVoice(response.getBody());
        verify(liveSip).twiml("+14355652512", "+56966939611", OUTBOUND_SID);
        verifyNoInteractions(calls);
    }

    @Test
    void productionVoiceRoutesDirectlyToGptLiveWithoutLegacyVoice() {
        TwilioCallService calls = mock(TwilioCallService.class);
        OpenAiLiveSipService liveSip = mock(OpenAiLiveSipService.class);
        when(liveSip.isReady()).thenReturn(true);
        when(liveSip.twiml("+14355652512", "+56966939611", INBOUND_SID))
                .thenReturn("<Response><Dial><Sip>sip:proj_test@sip.api.openai.com;secure=true</Sip></Dial></Response>");

        var response = controller(calls, liveSip)
                .incoming(INBOUND_SID, "+56966939611", "+14355652512");

        assertEquals(200, response.getStatusCode().value());
        assertNoLegacyVoice(response.getBody());
        verify(liveSip).twiml("+14355652512", "+56966939611", INBOUND_SID);
        verifyNoInteractions(calls);
    }

    @Test
    void deprecatedTrialIngressStillUsesTheSameLiveOnlyFlow() {
        TwilioCallService calls = mock(TwilioCallService.class);
        OpenAiLiveSipService liveSip = mock(OpenAiLiveSipService.class);
        when(liveSip.isReady()).thenReturn(true);
        when(liveSip.twiml("+14355652512", "+56966939611", OUTBOUND_SID))
                .thenReturn("<Response><Dial><Sip>sip:proj_test@sip.api.openai.com;secure=true</Sip></Dial></Response>");

        var response = controller(calls, liveSip)
                .legacyOutboundTest(OUTBOUND_SID, "+14355652512", "+56966939611");

        assertEquals(200, response.getStatusCode().value());
        assertNoLegacyVoice(response.getBody());
        verify(liveSip).twiml("+14355652512", "+56966939611", OUTBOUND_SID);
        verifyNoInteractions(calls);
    }

    @Test
    void outboundTestFailsClosedWhenLiveIsUnavailable() {
        TwilioCallService calls = mock(TwilioCallService.class);
        OpenAiLiveSipService liveSip = mock(OpenAiLiveSipService.class);
        when(liveSip.isReady()).thenReturn(false);

        var response = controller(calls, liveSip)
                .outboundTest(OUTBOUND_SID, "+14355652512", "+56966939611");

        assertEquals(SILENT_HANGUP, response.getBody());
        assertNoLegacyVoice(response.getBody());
        verifyNoInteractions(calls);
    }

    @Test
    void productionVoiceFailsClosedWhenLiveIsUnavailable() {
        TwilioCallService calls = mock(TwilioCallService.class);
        OpenAiLiveSipService liveSip = mock(OpenAiLiveSipService.class);
        when(liveSip.isReady()).thenReturn(false);

        var response = controller(calls, liveSip)
                .incoming(INBOUND_SID, "+56966939611", "+14355652512");

        assertEquals(SILENT_HANGUP, response.getBody());
        assertNoLegacyVoice(response.getBody());
        verifyNoInteractions(calls);
    }
}
