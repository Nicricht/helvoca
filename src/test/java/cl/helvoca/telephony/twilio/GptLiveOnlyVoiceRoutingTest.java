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

    private static TwilioVoiceController controller(TwilioCallService calls,
                                                    OpenAiLiveSipService liveSip) {
        return new TwilioVoiceController(calls, liveSip, mock(CallSummaryService.class));
    }

    private static void assertNoLegacyTts(String xml) {
        assertFalse(xml.contains("<Say"));
        assertFalse(xml.contains("<Gather"));
        assertFalse(xml.toLowerCase().contains("polly"));
    }

    @Test
    void outboundTestRoutesDirectlyToGptLiveWithoutLegacyTts() {
        TwilioCallService calls = mock(TwilioCallService.class);
        OpenAiLiveSipService liveSip = mock(OpenAiLiveSipService.class);
        when(liveSip.isReady()).thenReturn(true);
        when(liveSip.twiml("+14355652512", "+56966939611"))
                .thenReturn("<Response><Dial><Sip>sip:proj_test@sip.api.openai.com;secure=true</Sip></Dial></Response>");

        var response = controller(calls, liveSip)
                .outboundTest("CA-OUT-LIVE", "+14355652512", "+56966939611");

        assertEquals(200, response.getStatusCode().value());
        assertNoLegacyTts(response.getBody());
        verify(liveSip).twiml("+14355652512", "+56966939611");
        verifyNoInteractions(calls);
    }

    @Test
    void productionVoiceRoutesDirectlyToGptLiveWithoutLegacyTts() {
        TwilioCallService calls = mock(TwilioCallService.class);
        OpenAiLiveSipService liveSip = mock(OpenAiLiveSipService.class);
        when(liveSip.isReady()).thenReturn(true);
        when(liveSip.twiml("+14355652512", "+56966939611"))
                .thenReturn("<Response><Dial><Sip>sip:proj_test@sip.api.openai.com;secure=true</Sip></Dial></Response>");

        var response = controller(calls, liveSip)
                .incoming("CA-IN-LIVE", "+56966939611", "+14355652512");

        assertEquals(200, response.getStatusCode().value());
        assertNoLegacyTts(response.getBody());
        verify(liveSip).twiml("+14355652512", "+56966939611");
        verifyNoInteractions(calls);
    }

    @Test
    void outboundTestFailsClosedWhenLiveIsUnavailable() {
        TwilioCallService calls = mock(TwilioCallService.class);
        OpenAiLiveSipService liveSip = mock(OpenAiLiveSipService.class);
        when(liveSip.isReady()).thenReturn(false);

        var response = controller(calls, liveSip)
                .outboundTest("CA-NO-LIVE", "+14355652512", "+56966939611");

        assertEquals(SILENT_HANGUP, response.getBody());
        assertNoLegacyTts(response.getBody());
        verifyNoInteractions(calls);
    }

    @Test
    void productionVoiceFailsClosedWhenLiveIsUnavailable() {
        TwilioCallService calls = mock(TwilioCallService.class);
        OpenAiLiveSipService liveSip = mock(OpenAiLiveSipService.class);
        when(liveSip.isReady()).thenReturn(false);

        var response = controller(calls, liveSip)
                .incoming("CA-IN-NO-LIVE", "+56966939611", "+14355652512");

        assertEquals(SILENT_HANGUP, response.getBody());
        assertNoLegacyTts(response.getBody());
        verifyNoInteractions(calls);
    }
}
