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
        boolean webhookValidationEnabled = whatsApp.isWebhookValidationEnabled();
        boolean whatsAppReady = whatsAppEnabled && webhookValidationEnabled && twilioWebhookReady;
        List<ProviderRuntimeStatus> providers = providerReadiness.providers().stream()
                .map(provider -> new ProviderRuntimeStatus(
                        provider.providerId(),
                        provider.mode(),
                        provider.configured(),
                        provider.available(),
                        provider.state()))
                .toList();

        return new ChannelRuntimeReadiness(
                new TwilioRuntimeReadiness(
                        credentialsConfigured,
                        securePublicBaseUrlConfigured,
                        mediaStreamUrlConfigured,
                        twilioWebhookReady,
                        twilioCode(credentialsConfigured, securePublicBaseUrlConfigured)),
                new VoiceRuntimeReadiness(
                        voiceReady,
                        providerReadiness.selectedProvider(),
                        voiceCode(voiceReady, twilioWebhookReady, mediaStreamUrlConfigured),
                        providers),
                new WhatsAppRuntimeReadiness(
                        whatsAppEnabled,
                        webhookValidationEnabled,
                        whatsAppReady,
                        whatsAppCode(whatsAppEnabled, webhookValidationEnabled, twilioWebhookReady)));
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

    private static String twilioCode(boolean credentialsConfigured, boolean securePublicBaseUrlConfigured) {
        if (!credentialsConfigured) return "MISSING_CREDENTIALS";
        if (!securePublicBaseUrlConfigured) return "INVALID_PUBLIC_URL";
        return "READY";
    }

    private static String voiceCode(boolean ready, boolean twilioWebhookReady, boolean mediaStreamUrlConfigured) {
        if (!twilioWebhookReady) return "TWILIO_WEBHOOK_NOT_READY";
        if (!mediaStreamUrlConfigured) return "INVALID_MEDIA_STREAM_URL";
        return ready ? "READY" : "NO_AVAILABLE_PROVIDER";
    }

    private static String whatsAppCode(boolean enabled,
                                       boolean webhookValidationEnabled,
                                       boolean twilioWebhookReady) {
        if (!enabled) return "DISABLED";
        if (!webhookValidationEnabled) return "VALIDATION_DISABLED";
        if (!twilioWebhookReady) return "TWILIO_WEBHOOK_NOT_READY";
        return "READY";
    }

    public record ChannelRuntimeReadiness(TwilioRuntimeReadiness twilio,
                                          VoiceRuntimeReadiness voice,
                                          WhatsAppRuntimeReadiness whatsApp) {
    }

    public record TwilioRuntimeReadiness(boolean credentialsConfigured,
                                         boolean securePublicBaseUrlConfigured,
                                         boolean mediaStreamUrlConfigured,
                                         boolean webhookReady,
                                         String code) {
    }

    public record VoiceRuntimeReadiness(boolean ready,
                                        String selectedProvider,
                                        String code,
                                        List<ProviderRuntimeStatus> providers) {
    }

    public record ProviderRuntimeStatus(String providerId,
                                        String mode,
                                        boolean configured,
                                        boolean available,
                                        String state) {
    }

    public record WhatsAppRuntimeReadiness(boolean enabled,
                                           boolean webhookValidationEnabled,
                                           boolean ready,
                                           String code) {
    }
}
