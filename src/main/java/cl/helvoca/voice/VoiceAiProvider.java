package cl.helvoca.voice;

import cl.helvoca.ai.realtime.RealtimeCallContext;

/**
 * Pluggable voice AI provider.
 *
 * <p>Implementations may use OpenAI Realtime, a composed STT+LLM+TTS pipeline,
 * or another provider without changing the telephony adapter.</p>
 */
public interface VoiceAiProvider {
    String id();

    boolean configured();

    default boolean certificationSession(RealtimeCallContext context) {
        return false;
    }

    VoiceAiSession createSession(RealtimeCallContext context, VoiceTransportSession transport);
}
