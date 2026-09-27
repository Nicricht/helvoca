package cl.helvoca.ai.gemini;

/**
 * Tiny local speech detector used only to cut carrier playback quickly when the
 * caller barges in. Gemini's own VAD still owns semantic turn boundaries.
 */
final class LocalBargeInDetector {
    enum Event {
        NONE,
        SPEECH_STARTED,
        SPEECH_ENDED
    }

    private final int meanAmplitudeThreshold;
    private final int speechFramesRequired;
    private final int releaseFramesRequired;

    private int speechFrames;
    private int quietFrames;
    private boolean speechActive;

    LocalBargeInDetector(int meanAmplitudeThreshold,
                         int speechFramesRequired,
                         int releaseFramesRequired) {
        this.meanAmplitudeThreshold = Math.max(100, meanAmplitudeThreshold);
        this.speechFramesRequired = Math.max(1, speechFramesRequired);
        this.releaseFramesRequired = Math.max(1, releaseFramesRequired);
    }

    synchronized Event accept(String base64Mulaw) {
        int meanAmplitude = PcmuAudioCodec.meanAbsoluteMulawAmplitude(base64Mulaw);
        boolean speech = meanAmplitude >= meanAmplitudeThreshold;

        if (speech) {
            quietFrames = 0;
            if (speechActive) return Event.NONE;
            speechFrames++;
            if (speechFrames >= speechFramesRequired) {
                speechFrames = 0;
                speechActive = true;
                return Event.SPEECH_STARTED;
            }
            return Event.NONE;
        }

        speechFrames = 0;
        if (!speechActive) return Event.NONE;

        quietFrames++;
        if (quietFrames >= releaseFramesRequired) {
            quietFrames = 0;
            speechActive = false;
            return Event.SPEECH_ENDED;
        }
        return Event.NONE;
    }

    synchronized void reset() {
        speechFrames = 0;
        quietFrames = 0;
        speechActive = false;
    }
}
