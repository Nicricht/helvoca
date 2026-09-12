package cl.helvoca.ai.gemini;

import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.ai.realtime.RealtimeToolService;
import cl.helvoca.call.CallSummaryService;
import cl.helvoca.call.CallTranscriptService;
import cl.helvoca.telephony.CallLifecycleService;
import cl.helvoca.voice.VoiceAiProvider;
import cl.helvoca.voice.VoiceAiSession;
import cl.helvoca.voice.VoiceProviderHealthRegistry;
import cl.helvoca.voice.VoiceTransportSession;
import org.springframework.stereotype.Component;

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
    private final VoiceProviderHealthRegistry health;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    public GeminiLiveVoiceProvider(GeminiLiveProperties properties,
                                   RealtimeToolService tools,
                                   CallTranscriptService transcripts,
                                   CallSummaryService summaries,
                                   CallLifecycleService lifecycle,
                                   VoiceProviderHealthRegistry health) {
        this.properties = properties;
        this.tools = tools;
        this.transcripts = transcripts;
        this.summaries = summaries;
        this.lifecycle = lifecycle;
        this.health = health;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean configured() {
        return properties.ready();
    }

    @Override
    public VoiceAiSession createSession(RealtimeCallContext context, VoiceTransportSession transport) {
        return new GeminiLiveVoiceSession(
                context, transport, properties, tools, transcripts, summaries, lifecycle, health, http);
    }
}
