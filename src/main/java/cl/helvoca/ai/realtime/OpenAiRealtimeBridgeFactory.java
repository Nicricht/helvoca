package cl.helvoca.ai.realtime;

import cl.helvoca.call.CallSummaryService;
import cl.helvoca.call.CallTranscriptService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.net.http.HttpClient;
import java.time.Duration;

@Component
public class OpenAiRealtimeBridgeFactory {
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

    public OpenAiRealtimeBridge create(RealtimeCallContext context, WebSocketSession twilioSession) {
        return new OpenAiRealtimeBridge(context, twilioSession, properties, tools, transcripts, summaries, httpClient);
    }

    public boolean configured() { return properties.hasApiKey(); }
}
