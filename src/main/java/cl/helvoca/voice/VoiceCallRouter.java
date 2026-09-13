package cl.helvoca.voice;

import cl.helvoca.ai.gemini.GeminiLiveVoiceProvider;
import cl.helvoca.ai.live.OpenAiLiveSipService;
import cl.helvoca.telephony.twilio.TwilioMediaStreamTwimlFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Component
public class VoiceCallRouter {
    private static final Logger log = LoggerFactory.getLogger(VoiceCallRouter.class);
    private static final String OPENAI_LIVE = "openai-live";

    private final VoiceProviderProperties properties;
    private final VoiceAiProviderRegistry mediaProviders;
    private final VoiceProviderHealthRegistry health;
    private final TwilioMediaStreamTwimlFactory mediaTwiml;
    private final OpenAiLiveSipService openAiLive;

    public VoiceCallRouter(VoiceProviderProperties properties,
                           VoiceAiProviderRegistry mediaProviders,
                           VoiceProviderHealthRegistry health,
                           TwilioMediaStreamTwimlFactory mediaTwiml,
                           OpenAiLiveSipService openAiLive) {
        this.properties = properties;
        this.mediaProviders = mediaProviders;
        this.health = health;
        this.mediaTwiml = mediaTwiml;
        this.openAiLive = openAiLive;
    }

    public Optional<RouteDecision> route(String businessPhone,
                                         String callerPhone,
                                         String twilioCallSid) {
        for (String configuredId : providerOrder()) {
            String id = canonical(configuredId);
            try {
                if (OPENAI_LIVE.equals(id)) {
                    boolean configured = openAiLive.isReady();
                    if (!health.allow(id, configured)) continue;
                    String twiml = openAiLive.twiml(businessPhone, callerPhone, twilioCallSid);
                    log.info("Voice router selected provider={} mode=SIP call={}", id, twilioCallSid);
                    return Optional.of(new RouteDecision(id, RouteMode.SIP, twiml));
                }

                String mediaId = mediaProviderId(id);
                VoiceAiProvider provider = mediaProviders.require(mediaId);
                if (!health.allow(provider.id(), provider.configured())) continue;
                String twiml = mediaTwiml.twiml(businessPhone, callerPhone, twilioCallSid, provider.id());
                log.info("Voice router selected provider={} mode=MEDIA_STREAM call={}", provider.id(), twilioCallSid);
                return Optional.of(new RouteDecision(provider.id(), RouteMode.MEDIA_STREAM, twiml));
            } catch (RuntimeException e) {
                log.warn("Voice route candidate unavailable provider={} call={} reason={}", id, twilioCallSid, e.getMessage());
            }
        }
        log.error("No healthy voice provider available call={} providers={}", twilioCallSid, providerOrder());
        return Optional.empty();
    }

    public VoiceReadiness readiness() {
        List<ProviderStatus> providers = new ArrayList<>();
        for (String configuredId : providerOrder()) {
            String id = canonical(configuredId);
            if (OPENAI_LIVE.equals(id)) {
                boolean configured = openAiLive.isReady();
                var snapshot = health.snapshot(id, configured);
                providers.add(new ProviderStatus(id, "SIP", snapshot.configured(), snapshot.available(),
                        snapshot.state(), snapshot.detail()));
                continue;
            }
            try {
                VoiceAiProvider provider = mediaProviders.require(mediaProviderId(id));
                var snapshot = health.snapshot(provider.id(), provider.configured());
                providers.add(new ProviderStatus(provider.id(), "MEDIA_STREAM", snapshot.configured(), snapshot.available(),
                        snapshot.state(), snapshot.detail()));
            } catch (RuntimeException e) {
                providers.add(new ProviderStatus(id, "UNKNOWN", false, false, "UNKNOWN_PROVIDER", e.getMessage()));
            }
        }
        boolean ready = providers.stream().anyMatch(ProviderStatus::available);
        String selected = providers.stream().filter(ProviderStatus::available)
                .map(ProviderStatus::providerId).findFirst().orElse(null);
        return new VoiceReadiness(ready, selected, providers);
    }

    private List<String> providerOrder() {
        Set<String> ordered = new LinkedHashSet<>();
        if (properties.getProviderOrder() != null) {
            properties.getProviderOrder().stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(VoiceCallRouter::canonical)
                    .forEach(ordered::add);
        }
        if (ordered.isEmpty()) {
            String legacy = canonical(properties.getAiProvider());
            ordered.add("openai".equals(legacy) ? OPENAI_LIVE : legacy);
        }
        return List.copyOf(ordered);
    }

    private static String mediaProviderId(String id) {
        if ("openai-realtime".equals(id)) return "openai";
        return id;
    }

    private static String canonical(String value) {
        if (value == null) return "";
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if ("gpt-live".equals(normalized) || "openai-gpt-live".equals(normalized)) return OPENAI_LIVE;
        if (GeminiLiveVoiceProvider.ID.equals(normalized)) return GeminiLiveVoiceProvider.ID;
        return normalized;
    }

    public enum RouteMode {
        SIP,
        MEDIA_STREAM
    }

    public record RouteDecision(String providerId, RouteMode mode, String twiml) {
    }

    public record ProviderStatus(String providerId,
                                 String mode,
                                 boolean configured,
                                 boolean available,
                                 String state,
                                 String detail) {
    }

    public record VoiceReadiness(boolean ready,
                                 String selectedProvider,
                                 List<ProviderStatus> providers) {
    }
}
