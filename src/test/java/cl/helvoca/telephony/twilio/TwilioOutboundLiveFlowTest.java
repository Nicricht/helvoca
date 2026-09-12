package cl.helvoca.telephony.twilio;

import cl.helvoca.call.CallSummaryService;
import cl.helvoca.voice.VoiceCallRouter;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class TwilioOutboundLiveFlowTest {

    @Test
    void outboundConsoleTestUsesTwilioNumberAsBusinessTesterAsCallerAndKeepsCallSid() {
        TwilioCallService calls = mock(TwilioCallService.class);
        VoiceCallRouter router = mock(VoiceCallRouter.class);
        CallSummaryService summaries = mock(CallSummaryService.class);
        TwilioVoiceController controller = new TwilioVoiceController(calls, router, summaries);
        String callSid = "CA0123456789abcdef0123456789abcdef";
        String twiml = "<Response><Connect><Stream url=\"wss://example/ws\"/></Connect></Response>";

        when(router.route("+14355652512", "+56911111111", callSid))
                .thenReturn(Optional.of(new VoiceCallRouter.RouteDecision(
                        "gemini", VoiceCallRouter.RouteMode.MEDIA_STREAM, twiml)));

        var response = controller.outboundTest(callSid, "+14355652512", "+56911111111");

        assertEquals(200, response.getStatusCode().value());
        assertEquals(twiml, response.getBody());
        verify(router).route("+14355652512", "+56911111111", callSid);
        verifyNoInteractions(calls, summaries);
    }
}
