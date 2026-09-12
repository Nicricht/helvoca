package cl.helvoca.ai.gemini;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;

/**
 * Audio bridge for the formats used by Twilio Media Streams and Gemini Live.
 * Twilio sends/accepts G.711 mu-law at 8 kHz. Gemini Live consumes signed
 * little-endian PCM16 at 16 kHz and emits PCM16 at 24 kHz.
 */
public final class PcmuAudioCodec {
    private static final int MULAW_BIAS = 0x84;
    private static final int MULAW_CLIP = 32635;

    private PcmuAudioCodec() {}

    public static String twilioMulaw8kToGeminiPcm16k(String base64Mulaw) {
        byte[] encoded = Base64.getDecoder().decode(base64Mulaw);
        short[] pcm8 = new short[encoded.length];
        for (int i = 0; i < encoded.length; i++) pcm8[i] = decodeMulaw(encoded[i]);

        short[] pcm16 = new short[pcm8.length * 2];
        for (int i = 0; i < pcm8.length; i++) {
            short current = pcm8[i];
            short next = i + 1 < pcm8.length ? pcm8[i + 1] : current;
            pcm16[i * 2] = current;
            pcm16[i * 2 + 1] = (short) (((int) current + (int) next) / 2);
        }
        return Base64.getEncoder().encodeToString(toLittleEndian(pcm16));
    }

    public static String geminiPcm24kToTwilioMulaw8k(String base64Pcm16) {
        byte[] raw = Base64.getDecoder().decode(base64Pcm16);
        int completeSamples = raw.length / 2;
        if (completeSamples < 3) return "";

        ByteBuffer buffer = ByteBuffer.wrap(raw, 0, completeSamples * 2).order(ByteOrder.LITTLE_ENDIAN);
        short[] pcm24 = new short[completeSamples];
        for (int i = 0; i < completeSamples; i++) pcm24[i] = buffer.getShort();

        int outputSamples = pcm24.length / 3;
        byte[] mulaw = new byte[outputSamples];
        for (int i = 0; i < outputSamples; i++) {
            int offset = i * 3;
            int averaged = ((int) pcm24[offset] + pcm24[offset + 1] + pcm24[offset + 2]) / 3;
            mulaw[i] = encodeMulaw((short) averaged);
        }
        return Base64.getEncoder().encodeToString(mulaw);
    }

    static short decodeMulaw(byte value) {
        int ulaw = (~value) & 0xff;
        int sign = ulaw & 0x80;
        int exponent = (ulaw >>> 4) & 0x07;
        int mantissa = ulaw & 0x0f;
        int sample = ((mantissa << 3) + MULAW_BIAS) << exponent;
        sample -= MULAW_BIAS;
        return (short) (sign != 0 ? -sample : sample);
    }

    static byte encodeMulaw(short input) {
        int sample = input;
        int sign = (sample >> 8) & 0x80;
        if (sign != 0) sample = -sample;
        if (sample > MULAW_CLIP) sample = MULAW_CLIP;
        sample += MULAW_BIAS;

        int exponent = 7;
        for (int mask = 0x4000; exponent > 0 && (sample & mask) == 0; exponent--, mask >>= 1) {
            // Locate the segment containing this sample.
        }
        int mantissa = (sample >> (exponent + 3)) & 0x0f;
        return (byte) ~(sign | (exponent << 4) | mantissa);
    }

    private static byte[] toLittleEndian(short[] samples) {
        ByteBuffer buffer = ByteBuffer.allocate(samples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (short sample : samples) buffer.putShort(sample);
        return buffer.array();
    }
}
