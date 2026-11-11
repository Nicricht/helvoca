package cl.helvoca.voice;

import cl.helvoca.ai.gemini.GeminiLiveVoiceProvider;
import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.ai.realtime.RealtimeToolService;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Alternative media-stream provider, absent from default provider order and OFF
 * unless a verified tenant/phone, credential, and economic gate all agree.
 */
@Component
public class DeepgramEconomyVoiceProvider implements VoiceAiProvider {
    public static final String ID = "deepgram-economy";
    private final DeepgramEconomyVoiceProperties properties;
    private final RealtimeToolService tools;
    private final GeminiLiveVoiceProvider gemini;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6)).build();

    public DeepgramEconomyVoiceProvider(DeepgramEconomyVoiceProperties properties,
                                       RealtimeToolService tools,
                                       GeminiLiveVoiceProvider gemini) {
        this.properties = properties;
        this.tools = tools;
        this.gemini = gemini;
    }

    @Override public String id() { return ID; }
    @Override public String modelId() { return "nova-3-multi+aura-2-selena-es"; }
    @Override public boolean configured() { return false; } // No unscoped provider readiness.
    public boolean configuredFor(String destination) {
        return properties.configuredFor(destination) && gemini.configuredFor(destination);
    }

    @Override
    public VoiceAiSession createSession(RealtimeCallContext context, VoiceTransportSession transport) {
        if (!properties.permits(context) || !gemini.configuredFor(context.destinationNumber())
                || !tools.agentActive(context)) {
            throw new IllegalStateException("Economy voice requires a verified tenant and Gemini fallback");
        }
        return new DeepgramEconomyVoiceSession(context, transport, properties,
                new DeepgramEconomyPublicResponder(tools), tools, gemini, http);
    }
}
