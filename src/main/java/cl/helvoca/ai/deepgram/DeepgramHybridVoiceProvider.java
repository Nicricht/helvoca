package cl.helvoca.ai.deepgram;

import cl.helvoca.ai.gemini.GeminiLiveVoiceProvider;
import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.ai.realtime.RealtimeToolService;
import cl.helvoca.call.CallSummaryService;
import cl.helvoca.call.CallTranscriptService;
import cl.helvoca.security.TenantDatabaseContext;
import cl.helvoca.telephony.CallLifecycleService;
import cl.helvoca.voice.VoiceAiProvider;
import cl.helvoca.voice.VoiceAiSession;
import cl.helvoca.voice.VoiceProviderHealthRegistry;
import cl.helvoca.voice.VoiceTransportSession;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;
import java.time.Duration;

@Component
public class DeepgramHybridVoiceProvider implements VoiceAiProvider {
    public static final String ID = "deepgram";

    private final DeepgramVoiceProperties properties;
    private final GeminiLiveVoiceProvider gemini;
    private final RealtimeToolService tools;
    private final EconomicVoiceFactsService facts;
    private final CallTranscriptService transcripts;
    private final CallSummaryService summaries;
    private final CallLifecycleService lifecycle;
    private final VoiceProviderHealthRegistry health;
    private final TenantDatabaseContext databaseContext;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    public DeepgramHybridVoiceProvider(DeepgramVoiceProperties properties,
                                       GeminiLiveVoiceProvider gemini,
                                       RealtimeToolService tools,
                                       EconomicVoiceFactsService facts,
                                       CallTranscriptService transcripts,
                                       CallSummaryService summaries,
                                       CallLifecycleService lifecycle,
                                       VoiceProviderHealthRegistry health,
                                       TenantDatabaseContext databaseContext) {
        this.properties = properties;
        this.gemini = gemini;
        this.tools = tools;
        this.facts = facts;
        this.transcripts = transcripts;
        this.summaries = summaries;
        this.lifecycle = lifecycle;
        this.health = health;
        this.databaseContext = databaseContext;
    }

    @Override
    public String id() { return ID; }

    @Override
    public boolean configured() { return properties.ready(); }

    @Override
    public String modelId() {
        return properties.getSttModel() + "+" + properties.getTtsModel();
    }

    @Override
    public VoiceAiSession createSession(RealtimeCallContext context, VoiceTransportSession transport) {
        if (!configured()) throw new IllegalStateException("Deepgram voice is not configured or privacy-approved");
        if (context == null || context.businessId() == null) {
            throw new IllegalArgumentException("Realtime call context requires businessId");
        }
        String configuredAgent = tools.agentName(context, null);
        if (configuredAgent != null && !tools.agentActive(context)) {
            throw new IllegalStateException("AI agent is disabled for this business");
        }
        return new DeepgramHybridVoiceSession(
                context, transport, properties, gemini, tools, facts, transcripts,
                summaries, lifecycle, health, databaseContext, http);
    }
}
