package cl.helvoca.voice;

/**
 * Provider-neutral output side of an active telephony media session.
 *
 * <p>AI providers use this interface instead of depending directly on Twilio,
 * Spring WebSocket types, or any other carrier-specific protocol.</p>
 */
public interface VoiceTransportSession {
    String id();

    boolean isOpen();

    void sendAudio(String streamId, String base64Audio);

    void clearPlayback(String streamId);

    void closeOnUpstreamFailure();
}
