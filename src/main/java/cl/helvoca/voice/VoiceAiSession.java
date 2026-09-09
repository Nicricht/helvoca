package cl.helvoca.voice;

/**
 * Provider-neutral realtime voice AI session.
 *
 * <p>The telephony layer feeds encoded inbound audio into this port and the
 * selected AI provider owns the upstream conversation session.</p>
 */
public interface VoiceAiSession extends AutoCloseable {
    void start();

    void acceptInboundAudio(String base64Audio);

    @Override
    void close();
}
