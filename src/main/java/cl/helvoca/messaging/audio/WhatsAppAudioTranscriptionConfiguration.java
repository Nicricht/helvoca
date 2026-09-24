package cl.helvoca.messaging.audio;

import cl.helvoca.ai.gemini.GeminiLiveProperties;
import cl.helvoca.ai.realtime.OpenAiRealtimeProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

@Configuration
public class WhatsAppAudioTranscriptionConfiguration {

    @Bean
    public AudioTranscriber whatsAppAudioTranscriber(
            OpenAiRealtimeProperties openAi,
            GeminiLiveProperties gemini,
            WhatsAppAudioTranscriptionProperties properties,
            @Value("${OPENAI_WHATSAPP_TRANSCRIPTION_MODEL:gpt-transcribe}") String openAiModel,
            @Value("${OPENAI_WHATSAPP_TRANSCRIPTION_FALLBACK_MODEL:whisper-1}") String openAiFallbackModel,
            @Value("${OPENAI_AUDIO_TRANSCRIPTIONS_URL:https://api.openai.com/v1/audio/transcriptions}") String openAiEndpoint,
            @Value("${GEMINI_AUDIO_TRANSCRIPTION_MODEL:gemini-3.8-flash}") String geminiModel,
            @Value("${GEMINI_AUDIO_TRANSCRIPTION_FALLBACK_MODEL:gemini-3.6-flash}") String geminiFallbackModel,
            @Value("${GEMINI_GENERATE_CONTENT_BASE_URL:https://generativelanguage.googleapis.com/v1beta}") String geminiBaseUrl) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();
        return new RoutedAudioTranscriber(
                List.of(
                        new DeepgramAudioTranscriptionProvider(properties, http),
                        new GeminiAudioTranscriptionProvider(
                                gemini,
                                http,
                                geminiModel,
                                geminiFallbackModel,
                                geminiBaseUrl),
                        new OpenAiAudioTranscriptionProvider(
                                openAi,
                                http,
                                openAiModel,
                                openAiFallbackModel,
                                openAiEndpoint)),
                properties);
    }
}
