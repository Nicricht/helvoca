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

    /**
     * Requests that the carrier end the call only after all audio already queued
     * for playback has finished. Returns false when the transport cannot provide
     * a playback-completion signal.
     */
    default boolean endAfterPlayback() { return false; }

    /**
     * Receives a carrier playback marker acknowledgement.
     */
    default void onPlaybackMark(String name) { }

    /**
     * Moves the active carrier call away from the AI session and to the
     * tenant-configured human destination. Returns true only after the carrier
     * accepted the live call update.
     */
    boolean transferToHuman(String targetPhone);

    /**
     * Ends an unusable AI media session through the safest carrier-specific
     * fallback available. Implementations should avoid leaving the caller in
     * dead air.
     */
    void closeOnUpstreamFailure();
}
