package cl.helvoca.ai.realtime;

import cl.helvoca.call.CallSummaryService;
import cl.helvoca.call.CallTranscriptService;
import cl.helvoca.voice.VoiceAiProvider;
import cl.helvoca.voice.VoiceAiSession;
import cl.helvoca.voice.VoiceTransportSession;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;
import java.time.Duration;

@Component
public class OpenAiRealtimeBridgeFactory implements VoiceAiProvider {
    private final OpenAiRealtimeProperties properties;
    private final RealtimeToolService tools;
    private final CallTranscriptService transcripts;
    private final CallSummaryService summaries;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public OpenAiRealtimeBridgeFactory(OpenAiRealtimeProperties properties,
                                       RealtimeToolService tools,
                                       CallTranscriptService transcripts,
                                       CallSummaryService summaries) {
        this.properties = properties;
        this.tools = tools;
        this.transcripts = transcripts;
        this.summaries = summaries;
    }

    @Override
    public String id() {
        return "openai";
    }

    @Override
    public VoiceAiSession createSession(RealtimeCallContext context, VoiceTransportSession transport) {
        return new OpenAiRealtimeBridge(context, transport, properties, tools, transcripts, summaries, httpClient);
    }

    @Override
    public boolean configured() {
        return properties.hasApiKey();
    }
}
