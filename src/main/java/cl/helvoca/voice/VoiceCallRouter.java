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
    private static final Set<String> CERTIFICATION_PROVIDERS =
            Set.of(GeminiLiveVoiceProvider.ID, OPENAI_LIVE);

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
        return route(businessPhone, callerPhone, twilioCallSid, null, null);
    }

    public Optional<RouteDecision> route(String businessPhone,
                                         String callerPhone,
                                         String twilioCallSid,
                                         String voiceOverride) {
        return route(businessPhone, callerPhone, twilioCallSid, null, voiceOverride);
    }

    public Optional<RouteDecision> route(String businessPhone,
                                         String callerPhone,
                                         String twilioCallSid,
                                         String providerOverride,
                                         String voiceOverride) {
        if (!validCertificationProviderOverride(providerOverride)) {
            log.error("Blocked unsupported certification voice provider call={} provider_override={}",
                    twilioCallSid, providerOverride);
            return Optional.empty();
        }

        String pinnedProvider = normalizeCertificationProviderOverride(providerOverride);
        boolean bakeOff = voiceOverride != null && !voiceOverride.isBlank();
        if (OPENAI_LIVE.equals(pinnedProvider) && bakeOff) {
            log.error("Blocked incompatible certification overrides call={} provider_override={} voice_override={}",
                    twilioCallSid, pinnedProvider, voiceOverride);
            return Optional.empty();
        }

        List<String> configuredOrder = providerOrder();
        List<String> routeOrder = configuredOrder;
        if (pinnedProvider != null) {
            if (!configuredOrder.contains(pinnedProvider)) {
                log.error("Blocked certification provider not present in configured order call={} provider_override={} providers={}",
                        twilioCallSid, pinnedProvider, configuredOrder);
                return Optional.empty();
            }
            routeOrder = List.of(pinnedProvider);
        }

        for (String configuredId : routeOrder) {
            String id = canonical(configuredId);
            try {
                if (OPENAI_LIVE.equals(id)) {
                    if (bakeOff) continue;
                    boolean configured = openAiLive.isReady();
                    if (!health.allow(id, configured)) continue;
                    String twiml = openAiLive.twiml(businessPhone, callerPhone, twilioCallSid);
                    log.info("Voice router selected provider={} mode=SIP call={} provider_override={}",
                            id, twilioCallSid, pinnedProvider == null ? "none" : pinnedProvider);
                    return Optional.of(new RouteDecision(id, RouteMode.SIP, twiml));
                }

                String mediaId = mediaProviderId(id);
                if (bakeOff && !GeminiLiveVoiceProvider.ID.equals(mediaId)) continue;
                VoiceAiProvider provider = mediaProviders.require(mediaId);
                if (!health.allow(provider.id(), provider.configured())) continue;
                String twiml = bakeOff
                        ? mediaTwiml.twiml(
                                businessPhone, callerPhone, twilioCallSid, provider.id(), voiceOverride)
                        : mediaTwiml.twiml(
                                businessPhone, callerPhone, twilioCallSid, provider.id());
                log.info(
                        "Voice router selected provider={} mode=MEDIA_STREAM call={} provider_override={} voice_override={}",
                        provider.id(),
                        twilioCallSid,
                        pinnedProvider == null ? "none" : pinnedProvider,
                        bakeOff ? voiceOverride : "none");
                return Optional.of(new RouteDecision(provider.id(), RouteMode.MEDIA_STREAM, twiml));
            } catch (RuntimeException e) {
                log.warn("Voice route candidate unavailable provider={} call={} reason={}",
                        id, twilioCallSid, e.getMessage());
            }
        }
        log.error("No healthy voice provider available call={} providers={} provider_override={} voice_override={}",
                twilioCallSid,
                routeOrder,
                pinnedProvider == null ? "none" : pinnedProvider,
                bakeOff ? voiceOverride : "none");
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

    public static boolean validCertificationProviderOverride(String value) {
        if (value == null || value.isBlank()) return true;
        return CERTIFICATION_PROVIDERS.contains(canonical(value));
    }

    public static String normalizeCertificationProviderOverride(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = canonical(value);
        return CERTIFICATION_PROVIDERS.contains(normalized) ? normalized : null;
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
