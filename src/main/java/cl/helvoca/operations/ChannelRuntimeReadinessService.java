package cl.helvoca.operations;

import cl.helvoca.messaging.WhatsAppProperties;
import cl.helvoca.telephony.twilio.TwilioProperties;
import cl.helvoca.voice.VoiceCallRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChannelRuntimeReadinessService implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(ChannelRuntimeReadinessService.class);

    private final TwilioProperties twilio;
    private final WhatsAppProperties whatsApp;
    private final VoiceCallRouter voiceRouter;

    public ChannelRuntimeReadinessService(TwilioProperties twilio,
                                          WhatsAppProperties whatsApp,
                                          VoiceCallRouter voiceRouter) {
        this.twilio = twilio;
        this.whatsApp = whatsApp;
        this.voiceRouter = voiceRouter;
    }

    public ChannelRuntimeReadiness snapshot() {
        boolean credentialsConfigured = twilio.hasAccountSid() && twilio.hasAuthToken();
        boolean securePublicBaseUrlConfigured = twilio.hasSecurePublicBaseUrl();
        boolean mediaStreamUrlConfigured = mediaStreamUrlConfigured();
        boolean twilioWebhookReady = credentialsConfigured && securePublicBaseUrlConfigured;

        VoiceCallRouter.VoiceReadiness providerReadiness = voiceRouter.readiness();
        boolean voiceReady = twilioWebhookReady
                && mediaStreamUrlConfigured
                && providerReadiness.ready();

        boolean whatsAppEnabled = whatsApp.isEnabled();
        boolean whatsAppReady = whatsAppEnabled && twilioWebhookReady;

        return new ChannelRuntimeReadiness(
                new TwilioRuntimeReadiness(
                        credentialsConfigured,
                        securePublicBaseUrlConfigured,
                        mediaStreamUrlConfigured,
                        twilioWebhookReady),
                new VoiceRuntimeReadiness(
                        voiceReady,
                        providerReadiness.selectedProvider(),
                        providerReadiness.providers()),
                new WhatsAppRuntimeReadiness(
                        whatsAppEnabled,
                        whatsApp.isWebhookValidationEnabled(),
                        whatsAppReady));
    }

    @Override
    public void run(ApplicationArguments args) {
        ChannelRuntimeReadiness readiness = snapshot();
        log.info(
                "CHANNEL_RUNTIME_READINESS twilioCredentials={} securePublicBaseUrl={} mediaStreamUrl={} voiceReady={} selectedVoiceProvider={} whatsappEnabled={} whatsappWebhookValidation={} whatsappReady={}",
                readiness.twilio().credentialsConfigured(),
                readiness.twilio().securePublicBaseUrlConfigured(),
                readiness.twilio().mediaStreamUrlConfigured(),
                readiness.voice().ready(),
                readiness.voice().selectedProvider(),
                readiness.whatsApp().enabled(),
                readiness.whatsApp().webhookValidationEnabled(),
                readiness.whatsApp().ready());
    }

    private boolean mediaStreamUrlConfigured() {
        try {
            String url = twilio.mediaStreamWebSocketUrl();
            return url != null && url.startsWith("wss://");
        } catch (RuntimeException e) {
            return false;
        }
    }

    public record ChannelRuntimeReadiness(TwilioRuntimeReadiness twilio,
                                          VoiceRuntimeReadiness voice,
                                          WhatsAppRuntimeReadiness whatsApp) {
    }

    public record TwilioRuntimeReadiness(boolean credentialsConfigured,
                                         boolean securePublicBaseUrlConfigured,
                                         boolean mediaStreamUrlConfigured,
                                         boolean webhookReady) {
    }

    public record VoiceRuntimeReadiness(boolean ready,
                                        String selectedProvider,
                                        List<VoiceCallRouter.ProviderStatus> providers) {
    }

    public record WhatsAppRuntimeReadiness(boolean enabled,
                                           boolean webhookValidationEnabled,
                                           boolean ready) {
    }
}
