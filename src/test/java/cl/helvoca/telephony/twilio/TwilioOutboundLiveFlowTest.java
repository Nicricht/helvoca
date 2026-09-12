package cl.helvoca.telephony.twilio;

import cl.helvoca.ai.live.OpenAiLiveSipService;
import cl.helvoca.call.CallSummaryService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class TwilioOutboundLiveFlowTest {

    @Test
    void outboundConsoleTestUsesTwilioNumberAsBusinessTesterAsCallerAndKeepsCallSid() {
        TwilioCallService calls = mock(TwilioCallService.class);
        OpenAiLiveSipService liveSip = mock(OpenAiLiveSipService.class);
        CallSummaryService summaries = mock(CallSummaryService.class);
        TwilioVoiceController controller = new TwilioVoiceController(calls, liveSip, summaries);
        String callSid = "CA0123456789abcdef0123456789abcdef";

        when(liveSip.isReady()).thenReturn(true);
        when(liveSip.twiml("+14355652512", "+56911111111", callSid))
                .thenReturn("<Response><Dial><Sip>sip:proj_test@sip.api.openai.com;secure=true</Sip></Dial></Response>");

        var response = controller.outboundTest(callSid, "+14355652512", "+56911111111");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("<Response><Dial><Sip>sip:proj_test@sip.api.openai.com;secure=true</Sip></Dial></Response>",
                response.getBody());
        verify(liveSip).twiml("+14355652512", "+56911111111", callSid);
        verifyNoInteractions(calls, summaries);
    }
}
