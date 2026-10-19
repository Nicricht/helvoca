package cl.helvoca.ai.gemini;

import cl.helvoca.agent.AgentVoiceProfile;
import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.ai.realtime.RealtimeToolService;
import cl.helvoca.call.CallCertificationService;
import cl.helvoca.call.CallSummaryService;
import cl.helvoca.call.CallTranscriptService;
import cl.helvoca.telephony.CallLifecycleService;
import cl.helvoca.telephony.twilio.TwilioCertificationDemoBindingGate;
import cl.helvoca.voice.VoiceAiProvider;
import cl.helvoca.voice.VoiceAiSession;
import cl.helvoca.voice.VoiceProviderHealthRegistry;
import cl.helvoca.voice.VoiceTransportSession;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.net.http.HttpClient;
import java.time.Duration;

@Component
public class GeminiLiveVoiceProvider implements VoiceAiProvider {
    public static final String ID = "gemini";

    private final GeminiLiveProperties properties;
    private final RealtimeToolService tools;
    private final CallTranscriptService transcripts;
    private final CallSummaryService summaries;
    private final CallLifecycleService lifecycle;
    private final CallCertificationService certifications;
    private final VoiceProviderHealthRegistry health;
    private final TwilioCertificationDemoBindingGate demoBinding;
    private final boolean demoEnabled;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    @Autowired
    public GeminiLiveVoiceProvider(GeminiLiveProperties properties,
                                   RealtimeToolService tools,
                                   CallTranscriptService transcripts,
                                   CallSummaryService summaries,
                                   CallLifecycleService lifecycle,
                                   CallCertificationService certifications,
                                   VoiceProviderHealthRegistry health,
                                   TwilioCertificationDemoBindingGate demoBinding,
                                   @Value("${TWILIO_CERTIFICATION_DEMO_GEMINI_ENABLED:false}") boolean demoEnabled) {
        this.properties = properties;
        this.tools = tools;
        this.transcripts = transcripts;
        this.summaries = summaries;
        this.lifecycle = lifecycle;
        this.certifications = certifications;
        this.health = health;
        this.demoBinding = demoBinding;
        this.demoEnabled = demoEnabled;
    }

    // Legacy unit fixtures; Spring injects the scoped gate through the annotated constructor.
    public GeminiLiveVoiceProvider(GeminiLiveProperties properties, RealtimeToolService tools,
            CallTranscriptService transcripts, CallSummaryService summaries,
            CallLifecycleService lifecycle, CallCertificationService certifications,
            VoiceProviderHealthRegistry health) {
        this(properties, tools, transcripts, summaries, lifecycle, certifications, health, null, false);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean configured() {
        return properties.ready();
    }

    public boolean configuredFor(String businessPhone) {
        return properties.ready() || (demoEnabled && demoBinding != null
                && properties.configurationReady() && demoBinding.allowsNumber(businessPhone));
    }

    @Override
    public String modelId() {
        return properties.getModel();
    }

    @Override
    public boolean certificationSession(RealtimeCallContext context) {
        return context != null
                && properties.certificationSimulationAllowedFor(context.callerNumber());
    }

    @Override
    public VoiceAiSession createSession(RealtimeCallContext context, VoiceTransportSession transport) {
        String configuredAgent = tools.agentName(context, null);
        if (configuredAgent != null && !tools.agentActive(context)) {
            throw new IllegalStateException("AI agent is disabled for this business");
        }
        return new GeminiLiveVoiceSession(
                context, transport, sessionProperties(context), tools, transcripts, summaries,
                lifecycle, certifications, health, http);
    }

    GeminiLiveProperties sessionProperties(RealtimeCallContext context) {
        GeminiLiveProperties session = new GeminiLiveProperties();
        session.setEnabled(properties.isEnabled() || (demoEnabled && demoBinding != null
                && context != null && context.businessId() != null
                && context.businessId().equals(demoBinding.pinnedBusinessId())
                && demoBinding.allowsNumber(context.destinationNumber())));
        session.setApiKey(properties.getApiKey());
        session.setModel(properties.getModel());
        String bakeOffVoice = context == null ? null : VoiceBakeOffCatalog.normalize(context.voiceOverride());
        String tenantVoice = context == null ? null : tools.agentVoice(context, null);
        session.setVoice(bakeOffVoice != null
                ? bakeOffVoice
                : AgentVoiceProfile.resolveGemini(tenantVoice, properties.getVoice()));
        session.setWebsocketUrl(properties.getWebsocketUrl());
        session.setCertificationCaller(properties.getCertificationCaller());
        session.setCertificationSimulation(
                properties.certificationSimulationAllowedFor(context == null ? null : context.callerNumber()));

        // Snapshot the actual validated conversation settings for this call.
        // Do not accidentally reset VAD, barge-in or latency budgets to Java
        // defaults when resolving the tenant-specific voice. Getters clamp
        // out-of-range operator configuration before it reaches the session.
        session.setStartOfSpeechSensitivity(properties.getStartOfSpeechSensitivity());
        session.setPrefixPaddingMs(properties.getPrefixPaddingMs());
        session.setEndOfSpeechSensitivity(properties.getEndOfSpeechSensitivity());
        session.setSilenceDurationMs(properties.getSilenceDurationMs());
        session.setHybridVadEnabled(properties.isHybridVadEnabled());
        session.setLocalBargeInEnabled(properties.isLocalBargeInEnabled());
        session.setLocalBargeInMeanAmplitudeThreshold(properties.getLocalBargeInMeanAmplitudeThreshold());
        session.setLocalBargeInSpeechFrames(properties.getLocalBargeInSpeechFrames());
        session.setLocalBargeInReleaseFrames(properties.getLocalBargeInReleaseFrames());
        session.setLocalBargeInRecentAssistantAudioMs(properties.getLocalBargeInRecentAssistantAudioMs());
        session.setToolLatencyBudgetMs(properties.getToolLatencyBudgetMs());
        session.setToolBatchLatencyBudgetMs(properties.getToolBatchLatencyBudgetMs());
        session.setResponseLatencyBudgetMs(properties.getResponseLatencyBudgetMs());
        return session;
    }
}
