package cl.helvoca.ai.deepgram;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeepgramVoiceProtocolTest {
    @Test
    void streamsTwilioPcmuAtEightKhzAndOptsOutOfModelImprovement() {
        DeepgramVoiceProperties properties = new DeepgramVoiceProperties();
        String uri = DeepgramVoiceProtocol.sttUri(properties).toString();

        assertTrue(uri.startsWith("wss://api.deepgram.com/v1/listen?"));
        assertTrue(uri.contains("model=nova-3"));
        assertTrue(uri.contains("language=multi"));
        assertTrue(uri.contains("language_hint=es"));
        assertTrue(uri.contains("encoding=mulaw"));
        assertTrue(uri.contains("sample_rate=8000"));
        assertTrue(uri.contains("interim_results=true"));
        assertTrue(uri.contains("endpointing=300"));
        assertTrue(uri.contains("mip_opt_out=true"));
    }

    @Test
    void synthesizesSpanishSpeechInTwilioNativePcmuFormatWithoutRetention() {
        DeepgramVoiceProperties properties = new DeepgramVoiceProperties();
        String uri = DeepgramVoiceProtocol.ttsUri(properties).toString();

        assertTrue(uri.startsWith("https://api.deepgram.com/v1/speak?"));
        assertTrue(uri.contains("model=aura-2-celeste-es"));
        assertTrue(uri.contains("encoding=mulaw"));
        assertTrue(uri.contains("sample_rate=8000"));
        assertTrue(uri.contains("container=none"));
        assertTrue(uri.contains("mip_opt_out=true"));
    }
}
