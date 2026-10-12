package cl.helvoca.ai.gemini;

import cl.helvoca.voice.VoiceCallRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Metadata-only startup certification. Never performs network I/O, tests API
 * entitlements or exposes secrets, tenant IDs, account IDs or arbitrary URLs.
 */
@Component
public class GeminiLiveRuntimeReadiness implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(GeminiLiveRuntimeReadiness.class);
    private final GeminiLiveProperties properties;
    private final VoiceCallRouter router;

    public GeminiLiveRuntimeReadiness(GeminiLiveProperties properties, VoiceCallRouter router) {
        this.properties = properties;
        this.router = router;
    }

    public Snapshot snapshot() {
        String tier = GeminiLiveModelPolicy.tier(properties.getModel());
        String publicModel = GeminiLiveModelPolicy.supported(properties.getModel())
                ? properties.getModel() : null;
        String selected = router.readiness().selectedProvider();
        String selection = "gemini".equals(selected) ? "GEMINI"
                : selected == null ? "NONE" : "OTHER";
        String key = properties.getApiKey();
        return new Snapshot(publicModel, tier,
                properties.isEnabled(), key != null && !key.isBlank(),
                GeminiLiveModelPolicy.approvedEndpoint(properties.getWebsocketUrl()),
                properties.ready(), selection);
    }

    @Override
    public void run(ApplicationArguments args) {
        Snapshot status = snapshot();
        log.info("GEMINI_LIVE_RUNTIME_READINESS model={} tier={} enabled={} apiKeyConfigured={} "
                        + "endpointApproved={} localConfigurationReady={} routerSelection={}",
                status.configuredModel(), status.modelTier(), status.enabled(),
                status.apiKeyConfigured(), status.endpointApproved(),
                status.localConfigurationReady(), status.routerSelection());
    }

    /** Local configuration only. Never interpreted as provider connectivity or commercial GO. */
    public record Snapshot(String configuredModel, String modelTier, boolean enabled,
                           boolean apiKeyConfigured, boolean endpointApproved,
                           boolean localConfigurationReady, String routerSelection) { }
}
