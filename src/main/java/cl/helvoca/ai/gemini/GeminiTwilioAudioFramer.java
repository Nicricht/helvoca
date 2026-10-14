package cl.helvoca.ai.gemini;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * Streaming 24 kHz little-endian PCM16 to 8 kHz PCMU bridge for Twilio.
 *
 * <p>Gemini Live may split audio at arbitrary byte or sample boundaries.
 * Preserve incomplete PCM samples and 3:1 resampling groups across provider
 * messages, then emit carrier-friendly 20 ms (160-sample) audio frames.
 * The final partial frame is emitted at the model's generation boundary.
 * Clearing discards unsent speech when the caller interrupts.</p>
 */
final class GeminiTwilioAudioFramer {
    private static final int TWILIO_FRAME_SAMPLES = 160;

    private final byte[] frame = new byte[TWILIO_FRAME_SAMPLES];
    private int frameSamples;
    private int lowByte = -1;
    private int groupSamples;
    private int groupSum;

    synchronized List<String> accept(String base64Pcm24k) {
        if (base64Pcm24k == null || base64Pcm24k.isBlank()) return List.of();

        byte[] bytes = Base64.getDecoder().decode(base64Pcm24k);
        List<String> frames = new ArrayList<>();
        for (byte value : bytes) {
            if (lowByte < 0) {
                lowByte = value & 0xff;
            } else {
                short pcm = (short) (((value & 0xff) << 8) | lowByte);
                lowByte = -1;
                appendSample(pcm, frames);
            }
        }
        return frames;
    }

    synchronized List<String> finishTurn() {
        List<String> frames = new ArrayList<>();
        if (groupSamples > 0) {
            appendMulaw((short) (groupSum / groupSamples), frames);
            groupSamples = 0;
            groupSum = 0;
        }
        if (frameSamples > 0) {
            frames.add(encodeFrame(frameSamples));
            frameSamples = 0;
        }
        // A trailing unmatched PCM byte is invalid at an end-of-turn boundary.
        // Never splice it into the next response.
        lowByte = -1;
        return frames;
    }

    synchronized void clear() {
        frameSamples = 0;
        lowByte = -1;
        groupSamples = 0;
        groupSum = 0;
    }

    private void appendSample(short pcm, List<String> frames) {
        groupSum += pcm;
        groupSamples++;
        if (groupSamples == 3) {
            appendMulaw((short) (groupSum / 3), frames);
            groupSum = 0;
            groupSamples = 0;
        }
    }

    private void appendMulaw(short pcm, List<String> frames) {
        frame[frameSamples++] = PcmuAudioCodec.encodeMulaw(pcm);
        if (frameSamples == TWILIO_FRAME_SAMPLES) {
            frames.add(encodeFrame(frameSamples));
            frameSamples = 0;
        }
    }

    private String encodeFrame(int length) {
        return Base64.getEncoder().encodeToString(Arrays.copyOf(frame, length));
    }
}
