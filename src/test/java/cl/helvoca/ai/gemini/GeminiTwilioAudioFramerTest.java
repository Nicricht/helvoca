package cl.helvoca.ai.gemini;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GeminiTwilioAudioFramerTest {
    @Test
    void preservesSampleAndResamplingBoundariesAcrossArbitraryGeminiChunks() {
        short[] samples = new short[480];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (short) (i % 2 == 0 ? 8000 : -8000);
        }
        byte[] raw = pcm(samples);
        GeminiTwilioAudioFramer framer = new GeminiTwilioAudioFramer();
        List<String> actual = new ArrayList<>();

        // Start/end in the middle of PCM16 samples and 3:1 groups.
        int[] sizes = {1, 7, 13, 159, 2, 121, 9, 648};
        int pos = 0;
        for (int size : sizes) {
            int end = Math.min(raw.length, pos + size);
            actual.addAll(framer.accept(b64(Arrays.copyOfRange(raw, pos, end))));
            pos = end;
        }
        actual.addAll(framer.finishTurn());

        assertEquals(1, actual.size());
        assertEquals(160, Base64.getDecoder().decode(actual.getFirst()).length);
        assertEquals(PcmuAudioCodec.geminiPcm24kToTwilioMulaw8k(b64(raw)),
                actual.getFirst());
        assertTrue(framer.finishTurn().isEmpty());
    }

    @Test
    void emitsTwentyMillisecondFramesAndFlushesLastPartialAtGenerationEnd() {
        short[] samples = new short[1200];
        Arrays.fill(samples, (short) 6500);
        GeminiTwilioAudioFramer framer = new GeminiTwilioAudioFramer();
        List<String> first = framer.accept(b64(pcm(Arrays.copyOfRange(samples, 0, 960))));
        List<String> second = framer.accept(b64(pcm(Arrays.copyOfRange(samples, 960, 1200))));
        List<String> tail = framer.finishTurn();

        assertEquals(2, first.size());
        assertTrue(second.isEmpty());
        assertEquals(1, tail.size());
        assertEquals(160, Base64.getDecoder().decode(first.get(0)).length);
        assertEquals(160, Base64.getDecoder().decode(first.get(1)).length);
        assertEquals(80, Base64.getDecoder().decode(tail.getFirst()).length);
        assertTrue(framer.finishTurn().isEmpty());
    }

    @Test
    void finishesIncompleteResamplingGroupAndDiscardsUnpairedByteBetweenTurns() {
        GeminiTwilioAudioFramer framer = new GeminiTwilioAudioFramer();
        byte[] fiveSamples = pcm(new short[]{3000, 6000, 9000, -3000, -6000});
        byte[] withUnpairedByte = Arrays.copyOf(fiveSamples, fiveSamples.length + 1);
        withUnpairedByte[withUnpairedByte.length - 1] = 0x7f;

        assertTrue(framer.accept(b64(withUnpairedByte)).isEmpty());
        List<String> tail = framer.finishTurn();
        assertEquals(1, tail.size());
        assertArrayEquals(new byte[]{
                        PcmuAudioCodec.encodeMulaw((short) 6000),
                        PcmuAudioCodec.encodeMulaw((short) -4500)},
                Base64.getDecoder().decode(tail.getFirst()));

        byte[] nextTurn = pcm(new short[]{7000, 7000, 7000});
        assertTrue(framer.accept(b64(nextTurn)).isEmpty());
        assertArrayEquals(new byte[]{PcmuAudioCodec.encodeMulaw((short) 7000)},
                Base64.getDecoder().decode(framer.finishTurn().getFirst()));
    }

    @Test
    void interruptionDiscardsQueuedAudioAndIncompleteByteWithoutLeakingOldVoice() {
        GeminiTwilioAudioFramer framer = new GeminiTwilioAudioFramer();
        assertTrue(framer.accept(b64(pcm(new short[]{15000, 15000}))).isEmpty());
        assertTrue(framer.accept(b64(new byte[]{0x44})).isEmpty());
        framer.clear();
        assertTrue(framer.finishTurn().isEmpty());

        byte[] clean = pcm(new short[]{-8000, -8000, -8000});
        assertTrue(framer.accept(b64(clean)).isEmpty());
        assertArrayEquals(new byte[]{PcmuAudioCodec.encodeMulaw((short) -8000)},
                Base64.getDecoder().decode(framer.finishTurn().getFirst()));
    }

    @Test
    void ignoresAbsentAudioAndRejectsInvalidBase64WithoutEmittingSamples() {
        GeminiTwilioAudioFramer framer = new GeminiTwilioAudioFramer();
        assertTrue(framer.accept(null).isEmpty());
        assertTrue(framer.accept("  ").isEmpty());
        assertTrue(framer.accept("").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> framer.accept("not-base64!"));
        assertTrue(framer.finishTurn().isEmpty());
    }

    private static byte[] pcm(short[] samples) {
        ByteBuffer bytes = ByteBuffer.allocate(samples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (short sample : samples) bytes.putShort(sample);
        return bytes.array();
    }

    private static String b64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }
}
