package cl.helvoca.telephony.twilio;

import cl.helvoca.call.CallSummaryService;
import cl.helvoca.voice.VoiceCallRouter;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

class MultiProviderVoiceRoutingTest {
    private static final String ACCOUNT_SID = "ACaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String SILENT_HANGUP =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response><Hangup/></Response>";
    private static final String INBOUND_SID = "CA11111111111111111111111111111111";
    private static final String OUTBOUND_SID = "CA22222222222222222222222222222222";

    private static TwilioVoiceController controller(TwilioCallService calls,
                                                    VoiceCallRouter router) {
        TwilioVoiceController controller = new TwilioVoiceController(
                calls, router, mock(CallSummaryService.class), new TwilioProperties());
        TwilioCallBindingGuard guard = mock(TwilioCallBindingGuard.class);
        when(guard.allowsIncoming(anyString(), anyString(), anyString(), anyString())).thenReturn(true);
        when(guard.allowsCallback(anyString(), anyString())).thenReturn(true);
        when(guard.allowsStreamCallback(anyString(), anyString(), anyString())).thenReturn(true);
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "bindingGuard", guard);
        return controller;
    }

    private static void assertNoLegacyVoice(String xml) {
        assertFalse(xml.contains("<Say"));
        assertFalse(xml.contains("<Gather"));
        assertFalse(xml.toLowerCase().contains("polly"));
    }

    @Test
    void outboundTestCanRouteToGeminiNativeAudioMediaStream() {
        TwilioCallService calls = mock(TwilioCallService.class);
        VoiceCallRouter router = mock(VoiceCallRouter.class);
        String twiml = "<Response><Connect><Stream url=\"wss://example/ws\"/></Connect></Response>";
        when(router.route("+14355652512", "+56966939611", OUTBOUND_SID))
                .thenReturn(Optional.of(new VoiceCallRouter.RouteDecision(
                        "gemini", VoiceCallRouter.RouteMode.MEDIA_STREAM, twiml)));

        var response = controller(calls, router)
                .outboundTest(OUTBOUND_SID, "+14355652512", "+56966939611", ACCOUNT_SID);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(twiml, response.getBody());
        assertNoLegacyVoice(response.getBody());
        verify(calls).startInboundCall(OUTBOUND_SID, "+56966939611", "+14355652512");
    }

    @Test
    void productionVoiceAdmitsThenRoutesToOpenAiLiveSip() {
        TwilioCallService calls = mock(TwilioCallService.class);
        VoiceCallRouter router = mock(VoiceCallRouter.class);
        String twiml = "<Response><Dial><Sip>sip:proj_test@sip.api.openai.com;secure=true</Sip></Dial></Response>";
        when(router.route("+14355652512", "+56966939611", INBOUND_SID))
                .thenReturn(Optional.of(new VoiceCallRouter.RouteDecision(
                        "openai-live", VoiceCallRouter.RouteMode.SIP, twiml)));

        var response = controller(calls, router)
                .incoming(INBOUND_SID, "+56966939611", "+14355652512", ACCOUNT_SID);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(twiml, response.getBody());
        assertNoLegacyVoice(response.getBody());
        verify(calls).startInboundCall(INBOUND_SID, "+56966939611", "+14355652512");
        verify(router).route("+14355652512", "+56966939611", INBOUND_SID);
    }

    @Test
    void callFailsClosedWhenEveryProviderIsUnavailable() {
        TwilioCallService calls = mock(TwilioCallService.class);
        VoiceCallRouter router = mock(VoiceCallRouter.class);
        when(router.route("+14355652512", "+56966939611", OUTBOUND_SID)).thenReturn(Optional.empty());

        var response = controller(calls, router)
                .outboundTest(OUTBOUND_SID, "+14355652512", "+56966939611", ACCOUNT_SID);

        assertEquals(SILENT_HANGUP, response.getBody());
        assertNoLegacyVoice(response.getBody());
        verify(calls).startInboundCall(OUTBOUND_SID, "+56966939611", "+14355652512");
    }
}
