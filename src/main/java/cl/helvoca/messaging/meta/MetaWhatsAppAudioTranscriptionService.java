package cl.helvoca.messaging.meta;

import cl.helvoca.ai.gemini.GeminiLiveProperties;
import cl.helvoca.ai.realtime.OpenAiRealtimeProperties;
import cl.helvoca.messaging.audio.AudioInput;
import cl.helvoca.messaging.audio.AudioTranscriber;
import cl.helvoca.messaging.audio.AudioTranscriptionProvider;
import cl.helvoca.messaging.audio.DeepgramAudioTranscriptionProvider;
import cl.helvoca.messaging.audio.GeminiAudioTranscriptionProvider;
import cl.helvoca.messaging.audio.OpenAiAudioTranscriptionProvider;
import cl.helvoca.messaging.audio.RoutedAudioTranscriber;
import cl.helvoca.messaging.audio.TranscriptionResult;
import cl.helvoca.messaging.audio.WhatsAppAudioTranscriptionProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class MetaWhatsAppAudioTranscriptionService {
    private static final Logger log = LoggerFactory.getLogger(MetaWhatsAppAudioTranscriptionService.class);
    private static final String DEFAULT_GEMINI_FALLBACK_MODEL = "gemini-3.6-flash";

    private final MetaWhatsAppAudioMediaService mediaService;
    private final AudioTranscriber transcriber;

    @Autowired
    public MetaWhatsAppAudioTranscriptionService(
            MetaWhatsAppCloudClient meta,
            MetaWhatsAppAccessTokenResolver accessTokens,
            OpenAiRealtimeProperties openAi,
            GeminiLiveProperties gemini,
            WhatsAppAudioTranscriptionProperties audioProperties,
            @Value("${OPENAI_WHATSAPP_TRANSCRIPTION_MODEL:gpt-transcribe}") String model,
            @Value("${OPENAI_AUDIO_TRANSCRIPTIONS_URL:https://api.openai.com/v1/audio/transcriptions}") String endpoint,
            @Value("${GEMINI_AUDIO_TRANSCRIPTION_MODEL:gemini-3.8-flash}") String geminiModel,
            @Value("${GEMINI_AUDIO_TRANSCRIPTION_FALLBACK_MODEL:gemini-3.6-flash}") String geminiFallbackModel,
            @Value("${GEMINI_GENERATE_CONTENT_BASE_URL:https://generativelanguage.googleapis.com/v1beta}") String geminiBaseUrl,
            @Value("${GEMINI_AUDIO_TRANSCRIPTION_PREFERRED:false}") boolean geminiPreferred) {
        this(
                new MetaWhatsAppAudioMediaService(meta, accessTokens),
                buildProductionTranscriber(
                        openAi,
                        gemini,
                        audioProperties,
                        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build(),
                        model,
                        endpoint,
                        geminiModel,
                        geminiFallbackModel,
                        geminiBaseUrl));
    }

    MetaWhatsAppAudioTranscriptionService(
            MetaWhatsAppCloudClient meta,
            MetaWhatsAppAccessTokenResolver accessTokens,
            OpenAiRealtimeProperties openAi,
            HttpClient http,
            String model,
            String endpoint) {
        this(
                meta,
                accessTokens,
                openAi,
                null,
                http,
                model,
                endpoint,
                "",
                DEFAULT_GEMINI_FALLBACK_MODEL,
                "",
                false);
    }

    MetaWhatsAppAudioTranscriptionService(
            MetaWhatsAppCloudClient meta,
            MetaWhatsAppAccessTokenResolver accessTokens,
            OpenAiRealtimeProperties openAi,
            GeminiLiveProperties gemini,
            HttpClient http,
            String model,
            String endpoint,
            String geminiModel,
            String geminiBaseUrl) {
        this(
                meta,
                accessTokens,
                openAi,
                gemini,
                http,
                model,
                endpoint,
                geminiModel,
                DEFAULT_GEMINI_FALLBACK_MODEL,
                geminiBaseUrl,
                false);
    }

    MetaWhatsAppAudioTranscriptionService(
            MetaWhatsAppCloudClient meta,
            MetaWhatsAppAccessTokenResolver accessTokens,
            OpenAiRealtimeProperties openAi,
            GeminiLiveProperties gemini,
            HttpClient http,
            String model,
            String endpoint,
            String geminiModel,
            String geminiBaseUrl,
            boolean geminiPreferred) {
        this(
                meta,
                accessTokens,
                openAi,
                gemini,
                http,
                model,
                endpoint,
                geminiModel,
                DEFAULT_GEMINI_FALLBACK_MODEL,
                geminiBaseUrl,
                geminiPreferred);
    }

    MetaWhatsAppAudioTranscriptionService(
            MetaWhatsAppCloudClient meta,
            MetaWhatsAppAccessTokenResolver accessTokens,
            OpenAiRealtimeProperties openAi,
            GeminiLiveProperties gemini,
            HttpClient http,
            String model,
            String endpoint,
            String geminiModel,
            String geminiFallbackModel,
            String geminiBaseUrl,
            boolean geminiPreferred) {
        this(
                new MetaWhatsAppAudioMediaService(meta, accessTokens),
                buildTranscriber(
                        openAi,
                        gemini,
                        http,
                        model,
                        endpoint,
                        geminiModel,
                        geminiFallbackModel,
                        geminiBaseUrl,
                        geminiPreferred));
    }

    MetaWhatsAppAudioTranscriptionService(
            MetaWhatsAppAudioMediaService mediaService,
            AudioTranscriber transcriber) {
        this.mediaService = mediaService;
        this.transcriber = transcriber;
    }

    public String transcribe(UUID businessId, String mediaId) {
        if (businessId == null) {
            throw new IllegalArgumentException("businessId is required");
        }
        String id = require(mediaId, "mediaId is required");
        MetaWhatsAppAudioMediaService.DownloadedAudio media = mediaService.download(businessId, id);
        String correlationId = Optional.ofNullable(MDC.get("correlationId"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .orElseGet(() -> MetaWhatsAppJobKeys.correlationId(businessId, id).toString());

        TranscriptionResult result = transcriber.transcribe(new AudioInput(
                media.bytes(),
                media.mimeType(),
                "",
                businessId,
                id,
                correlationId));

        log.info(
                "WHATSAPP_AUDIO_TRANSCRIPTION_SUCCESS provider={} model={} chars={} attempts={}",
                result.providerId(),
                result.modelId(),
                result.text().length(),
                result.attemptCount());
        return result.text();
    }

    private static AudioTranscriber buildProductionTranscriber(
            OpenAiRealtimeProperties openAi,
            GeminiLiveProperties gemini,
            WhatsAppAudioTranscriptionProperties audioProperties,
            HttpClient http,
            String model,
            String endpoint,
            String geminiModel,
            String geminiFallbackModel,
            String geminiBaseUrl) {
        AudioTranscriptionProvider deepgramProvider = new DeepgramAudioTranscriptionProvider(
                audioProperties,
                http);
        AudioTranscriptionProvider geminiProvider = new GeminiAudioTranscriptionProvider(
                gemini,
                http,
                geminiModel,
                geminiFallbackModel,
                geminiBaseUrl);
        AudioTranscriptionProvider openAiProvider = new OpenAiAudioTranscriptionProvider(
                openAi,
                http,
                model,
                endpoint);
        return new RoutedAudioTranscriber(
                List.of(deepgramProvider, geminiProvider, openAiProvider),
                audioProperties);
    }

    private static AudioTranscriber buildTranscriber(
            OpenAiRealtimeProperties openAi,
            GeminiLiveProperties gemini,
            HttpClient http,
            String model,
            String endpoint,
            String geminiModel,
            String geminiFallbackModel,
            String geminiBaseUrl,
            boolean geminiPreferred) {
        AudioTranscriptionProvider openAiProvider = new OpenAiAudioTranscriptionProvider(
                openAi,
                http,
                model,
                endpoint);
        AudioTranscriptionProvider geminiProvider = new GeminiAudioTranscriptionProvider(
                gemini,
                http,
                geminiModel,
                geminiFallbackModel,
                geminiBaseUrl);
        return new RoutedAudioTranscriber(
                geminiPreferred
                        ? List.of(geminiProvider, openAiProvider)
                        : List.of(openAiProvider, geminiProvider));
    }

    private static String require(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }
}
