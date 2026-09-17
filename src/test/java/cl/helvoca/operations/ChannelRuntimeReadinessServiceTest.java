package cl.helvoca.operations;

import cl.helvoca.messaging.WhatsAppProperties;
import cl.helvoca.telephony.twilio.TwilioProperties;
import cl.helvoca.voice.VoiceCallRouter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void stripsFreeFormProviderDetailFromReadinessSnapshot() {
        TwilioProperties twilio = configuredTwilio();
        WhatsAppProperties whatsapp = enabledWhatsApp(true);
        VoiceCallRouter voiceRouter = mock(VoiceCallRouter.class);
        when(voiceRouter.readiness()).thenReturn(new VoiceCallRouter.VoiceReadiness(
                false,
                null,
                List.of(new VoiceCallRouter.ProviderStatus(
                        "gemini", "MEDIA_STREAM", true, false, "OPEN",
                        "provider failed with secret-token customer-123"))));

        var readiness = new ChannelRuntimeReadinessService(twilio, whatsapp, voiceRouter).snapshot();

        assertFalse(readiness.toString().contains("secret-token"));
        assertFalse(readiness.toString().contains("customer-123"));
        assertEquals("OPEN", readiness.voice().providers().getFirst().state());
    }

    @Test
    void rejectsMalformedSecurePublicUrls() {
        for (String malformed : List.of(
                "https://",
                "https://bad host",
                "https:///missing-host",
                "https://helvoca.example?token=secret",
                "https://helvoca.example#fragment")) {
            TwilioProperties twilio = configuredTwilio();
            twilio.setPublicBaseUrl(malformed);
            VoiceCallRouter voiceRouter = readyVoiceRouter();

            var readiness = new ChannelRuntimeReadinessService(
                    twilio, enabledWhatsApp(true), voiceRouter).snapshot();

            assertFalse(readiness.twilio().securePublicBaseUrlConfigured(), malformed);
            assertFalse(readiness.twilio().mediaStreamUrlConfigured(), malformed);
            assertFalse(readiness.voice().ready(), malformed);
        }
    }

    @Test
    void rejectsInvalidMediaStreamPaths() {
        for (String malformed : List.of("//other.example/media", "/media?token=secret", "/media#fragment")) {
            TwilioProperties twilio = configuredTwilio();
            twilio.setMediaStreamPath(malformed);

            var readiness = new ChannelRuntimeReadinessService(
                    twilio, enabledWhatsApp(true), readyVoiceRouter()).snapshot();

            assertFalse(readiness.twilio().mediaStreamUrlConfigured(), malformed);
            assertFalse(readiness.voice().ready(), malformed);
        }
    }

    @Test
    void whatsappIsNotReadyWhenWebhookValidationIsDisabled() {
        var readiness = new ChannelRuntimeReadinessService(
                configuredTwilio(), enabledWhatsApp(false), readyVoiceRouter()).snapshot();

        assertTrue(readiness.whatsApp().enabled());
        assertFalse(readiness.whatsApp().webhookValidationEnabled());
        assertFalse(readiness.whatsApp().ready());
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

    private static TwilioProperties configuredTwilio() {
        TwilioProperties twilio = new TwilioProperties();
        twilio.setAccountSid("AC-present");
        twilio.setAuthToken("auth-present");
        twilio.setPublicBaseUrl("https://helvoca.example");
        return twilio;
    }

    private static WhatsAppProperties enabledWhatsApp(boolean webhookValidationEnabled) {
        WhatsAppProperties whatsapp = new WhatsAppProperties();
        whatsapp.setEnabled(true);
        whatsapp.setWebhookValidationEnabled(webhookValidationEnabled);
        return whatsapp;
    }

    private static VoiceCallRouter readyVoiceRouter() {
        VoiceCallRouter voiceRouter = mock(VoiceCallRouter.class);
        when(voiceRouter.readiness()).thenReturn(new VoiceCallRouter.VoiceReadiness(
                true,
                "gemini",
                List.of(new VoiceCallRouter.ProviderStatus(
                        "gemini", "MEDIA_STREAM", true, true, "CLOSED", "ready"))));
        return voiceRouter;
    }
}
