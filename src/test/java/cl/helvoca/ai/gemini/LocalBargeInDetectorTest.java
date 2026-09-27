package cl.helvoca.ai.gemini;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocalBargeInDetectorTest {

    @Test
    void requiresConsecutiveSpeechFramesAndReleasesAfterQuietFrames() {
        LocalBargeInDetector detector = new LocalBargeInDetector(900, 2, 5);
        String speech = mulawFrame((short) 8_000);
        String silence = mulawSilence();

        assertEquals(LocalBargeInDetector.Event.NONE, detector.accept(speech));
        assertEquals(LocalBargeInDetector.Event.SPEECH_STARTED, detector.accept(speech));
        assertEquals(LocalBargeInDetector.Event.NONE, detector.accept(speech));

        for (int i = 0; i < 4; i++) {
            assertEquals(LocalBargeInDetector.Event.NONE, detector.accept(silence));
        }
        assertEquals(LocalBargeInDetector.Event.SPEECH_ENDED, detector.accept(silence));

        assertEquals(LocalBargeInDetector.Event.NONE, detector.accept(speech));
        assertEquals(LocalBargeInDetector.Event.SPEECH_STARTED, detector.accept(speech));
    }

    @Test
    void phoneSilenceNeverTriggersBargeIn() {
        LocalBargeInDetector detector = new LocalBargeInDetector(900, 2, 5);
        String silence = mulawSilence();

        for (int i = 0; i < 20; i++) {
            assertEquals(LocalBargeInDetector.Event.NONE, detector.accept(silence));
        }
    }

    private static String mulawFrame(short sample) {
        byte[] frame = new byte[160];
        Arrays.fill(frame, PcmuAudioCodec.encodeMulaw(sample));
        return Base64.getEncoder().encodeToString(frame);
    }

    private static String mulawSilence() {
        byte[] frame = new byte[160];
        Arrays.fill(frame, (byte) 0xff);
        return Base64.getEncoder().encodeToString(frame);
    }
}
