package cl.helvoca.ai.gemini;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PcmuAudioCodecTest {

    @Test
    void twentyMillisecondTwilioFrameBecomesPcm16At16Khz() {
        byte[] mulaw = new byte[160];
        Arrays.fill(mulaw, (byte) 0xff);

        String pcmBase64 = PcmuAudioCodec.twilioMulaw8kToGeminiPcm16k(
                Base64.getEncoder().encodeToString(mulaw));
        byte[] pcm = Base64.getDecoder().decode(pcmBase64);

        assertEquals(640, pcm.length, "320 PCM16 samples = 20ms at 16kHz");
        ByteBuffer buffer = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);
        while (buffer.remaining() >= 2) assertEquals(0, buffer.getShort());
    }

    @Test
    void twentyMillisecondGeminiFrameBecomesTwilioMulawAt8Khz() {
        ByteBuffer pcm24 = ByteBuffer.allocate(480 * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < 480; i++) pcm24.putShort((short) 0);

        String mulawBase64 = PcmuAudioCodec.geminiPcm24kToTwilioMulaw8k(
                Base64.getEncoder().encodeToString(pcm24.array()));
        byte[] mulaw = Base64.getDecoder().decode(mulawBase64);

        assertEquals(160, mulaw.length, "160 PCMU samples = 20ms at 8kHz");
        for (byte value : mulaw) assertEquals((byte) 0xff, value);
    }

    @Test
    void mulawEncodeDecodePreservesSpeechPolarity() {
        short positive = 8_000;
        short negative = -8_000;

        short decodedPositive = PcmuAudioCodec.decodeMulaw(PcmuAudioCodec.encodeMulaw(positive));
        short decodedNegative = PcmuAudioCodec.decodeMulaw(PcmuAudioCodec.encodeMulaw(negative));

        assertTrue(decodedPositive > 0);
        assertTrue(decodedNegative < 0);
    }
}
