package cl.helvoca.telephony.twilio;

import cl.helvoca.call.CallSummaryService;
import cl.helvoca.voice.VoiceCallRouter;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class TwilioVoiceControllerProviderOverrideTest {
    private static final String ACCOUNT_SID = "ACaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String CALL_SID = "CA0123456789abcdef0123456789abcdef";
    private static final String SILENT_HANGUP =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response><Hangup/></Response>";

    private static TwilioVoiceController controller(VoiceCallRouter router) {
        TwilioProperties properties = new TwilioProperties();
        TwilioVoiceController controller = new TwilioVoiceController(
                mock(TwilioCallService.class),
                router,
                mock(CallSummaryService.class),
                properties);
        TwilioCallBindingGuard guard = mock(TwilioCallBindingGuard.class);
        when(guard.allowsIncoming(anyString(), anyString(), anyString(), anyString())).thenReturn(true);
        when(guard.allowsCallback(anyString(), anyString())).thenReturn(true);
        when(guard.allowsStreamCallback(anyString(), anyString(), anyString())).thenReturn(true);
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "bindingGuard", guard);
        return controller;
    }

    @Test
    void outboundCertificationForwardsValidProviderPin() {
        VoiceCallRouter router = mock(VoiceCallRouter.class);
        TwilioVoiceController controller = controller(router);
        ReflectionTestUtils.setField(controller, "certificationProviderOverride", " openai-live ");
        ReflectionTestUtils.setField(controller, "certificationVoiceOverride", "");
        String twiml = "<Response><Dial><Sip>sip:test</Sip></Dial></Response>";

        when(router.route("+14355652512", "+56966939611", CALL_SID, "openai-live", null))
                .thenReturn(Optional.of(new VoiceCallRouter.RouteDecision(
                        "openai-live", VoiceCallRouter.RouteMode.SIP, twiml)));

        var response = controller.outboundTest(
                CALL_SID, "+14355652512", "+56966939611", ACCOUNT_SID);

        assertEquals(twiml, response.getBody());
        verify(router).route("+14355652512", "+56966939611", CALL_SID, "openai-live", null);
    }

    @Test
    void invalidProviderPinFailsClosedBeforeRouting() {
        VoiceCallRouter router = mock(VoiceCallRouter.class);
        TwilioVoiceController controller = controller(router);
        ReflectionTestUtils.setField(controller, "certificationProviderOverride", "elevenlabs");
        ReflectionTestUtils.setField(controller, "certificationVoiceOverride", "");

        var response = controller.outboundTest(
                CALL_SID, "+14355652512", "+56966939611", ACCOUNT_SID);

        assertEquals(SILENT_HANGUP, response.getBody());
        verifyNoInteractions(router);
    }
}
