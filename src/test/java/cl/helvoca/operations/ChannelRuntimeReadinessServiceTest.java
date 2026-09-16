package cl.helvoca.operations;

import cl.helvoca.messaging.WhatsAppProperties;
import cl.helvoca.telephony.twilio.TwilioProperties;
import cl.helvoca.voice.VoiceCallRouter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChannelRuntimeReadinessServiceTest {

    @Test
    void reportsVoiceAndWhatsAppConfiguredWithoutExposingSecrets() {
        TwilioProperties twilio = new TwilioProperties();
        twilio.setAccountSid("AC-secret");
        twilio.setAuthToken("auth-secret");
        twilio.setPublicBaseUrl("https://helvoca.example");

        WhatsAppProperties whatsapp = new WhatsAppProperties();
        whatsapp.setEnabled(true);

        VoiceCallRouter voiceRouter = mock(VoiceCallRouter.class);
        when(voiceRouter.readiness()).thenReturn(new VoiceCallRouter.VoiceReadiness(
                true,
                "gemini",
                List.of(new VoiceCallRouter.ProviderStatus(
                        "gemini", "MEDIA_STREAM", true, true, "CLOSED", "ready"))));

        ChannelRuntimeReadinessService service = new ChannelRuntimeReadinessService(twilio, whatsapp, voiceRouter);
        ChannelRuntimeReadinessService.ChannelRuntimeReadiness readiness = service.snapshot();

        assertTrue(readiness.twilio().credentialsConfigured());
        assertTrue(readiness.twilio().securePublicBaseUrlConfigured());
        assertTrue(readiness.twilio().mediaStreamUrlConfigured());
        assertTrue(readiness.voice().ready());
        assertTrue(readiness.whatsApp().ready());
        assertFalse(readiness.toString().contains("AC-secret"));
        assertFalse(readiness.toString().contains("auth-secret"));
    }

    @Test
    void failsClosedWhenPublicTwilioUrlIsMissing() {
        TwilioProperties twilio = new TwilioProperties();
        twilio.setAccountSid("AC-present");
        twilio.setAuthToken("auth-present");

        WhatsAppProperties whatsapp = new WhatsAppProperties();
        whatsapp.setEnabled(true);

        VoiceCallRouter voiceRouter = mock(VoiceCallRouter.class);
        when(voiceRouter.readiness()).thenReturn(new VoiceCallRouter.VoiceReadiness(
                true,
                "gemini",
                List.of(new VoiceCallRouter.ProviderStatus(
                        "gemini", "MEDIA_STREAM", true, true, "CLOSED", "ready"))));

        ChannelRuntimeReadinessService service = new ChannelRuntimeReadinessService(twilio, whatsapp, voiceRouter);
        ChannelRuntimeReadinessService.ChannelRuntimeReadiness readiness = service.snapshot();

        assertFalse(readiness.twilio().securePublicBaseUrlConfigured());
        assertFalse(readiness.twilio().mediaStreamUrlConfigured());
        assertFalse(readiness.voice().ready());
        assertFalse(readiness.whatsApp().ready());
    }
}
