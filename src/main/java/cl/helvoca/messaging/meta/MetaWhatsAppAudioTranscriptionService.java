package cl.helvoca.messaging.meta;

import cl.helvoca.ai.gemini.GeminiLiveProperties;
import cl.helvoca.ai.realtime.OpenAiRealtimeProperties;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

@Service
public class MetaWhatsAppAudioTranscriptionService {
    private static final Logger log =
            LoggerFactory.getLogger(MetaWhatsAppAudioTranscriptionService.class);

    private final MetaWhatsAppCloudClient meta;
    private final MetaWhatsAppAccessTokenResolver accessTokens;
    private final OpenAiRealtimeProperties openAi;
    private final GeminiLiveProperties gemini;
    private final HttpClient http;
    private final String model;
    private final String endpoint;
    private final String geminiModel;
    private final String geminiBaseUrl;

    @Autowired
    public MetaWhatsAppAudioTranscriptionService(
            MetaWhatsAppCloudClient meta,
            MetaWhatsAppAccessTokenResolver accessTokens,
            OpenAiRealtimeProperties openAi,
            GeminiLiveProperties gemini,
            @Value("${OPENAI_WHATSAPP_TRANSCRIPTION_MODEL:gpt-transcribe}") String model,
            @Value("${OPENAI_AUDIO_TRANSCRIPTIONS_URL:https://api.openai.com/v1/audio/transcriptions}") String endpoint,
            @Value("${GEMINI_AUDIO_TRANSCRIPTION_MODEL:gemini-3.8-flash}") String geminiModel,
            @Value("${GEMINI_GENERATE_CONTENT_BASE_URL:https://generativelanguage.googleapis.com/v1beta}") String geminiBaseUrl) {
        this(meta, accessTokens, openAi, gemini,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build(),
                model, endpoint, geminiModel, geminiBaseUrl);
    }

    MetaWhatsAppAudioTranscriptionService(
            MetaWhatsAppCloudClient meta,
            MetaWhatsAppAccessTokenResolver accessTokens,
            OpenAiRealtimeProperties openAi,
            HttpClient http,
            String model,
            String endpoint) {
        this(meta, accessTokens, openAi, null, http, model, endpoint, "", "");
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
        this.meta = meta;
        this.accessTokens = accessTokens;
        this.openAi = openAi;
        this.gemini = gemini;
        this.http = http;
        this.model = require(model, "WhatsApp transcription model is required");
        this.endpoint = require(endpoint, "Audio transcription endpoint is required");
        this.geminiModel = clean(geminiModel);
        this.geminiBaseUrl = clean(geminiBaseUrl).replaceAll("/+$", "");
    }

    public String transcribe(UUID businessId, String mediaId) {
        if (businessId == null) {
            throw new IllegalArgumentException("businessId is required");
        }
        String metaToken = accessTokens.resolve(businessId)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new IllegalStateException(
                        "Meta WhatsApp access token is not configured for tenant"));

        MetaWhatsAppCloudClient.DownloadedMedia media;
        try {
            media = meta.downloadMedia(mediaId, metaToken);
            log.info("WHATSAPP_AUDIO_TRANSCRIPTION_STAGE stage=media_downloaded bytes={} contentType={}",
                    media.bytes().length, normalizeAudioContentType(media.contentType()));
        } catch (MetaWhatsAppApiException e) {
            log.warn("WHATSAPP_AUDIO_TRANSCRIPTION_FAILURE stage=meta_media failureCode={} retryable={}",
                    e.failureCode(), e.retryable());
            throw e;
        } catch (RuntimeException e) {
            log.warn("WHATSAPP_AUDIO_TRANSCRIPTION_FAILURE stage=meta_media type={} reason={}",
                    e.getClass().getSimpleName(), safeReason(e));
            throw e;
        }

        try {
            String transcript = transcribeOpenAi(media);
            log.info("WHATSAPP_AUDIO_TRANSCRIPTION_SUCCESS provider=openai model={} chars={}",
                    model, transcript.length());
            return transcript;
        } catch (RuntimeException openAiFailure) {
            log.warn("WHATSAPP_AUDIO_TRANSCRIPTION_FAILURE stage=openai model={} type={} reason={}",
                    model, openAiFailure.getClass().getSimpleName(), safeReason(openAiFailure));
            if (Thread.currentThread().isInterrupted() || !geminiConfigured()) {
                throw openAiFailure;
            }

            try {
                String transcript = transcribeGemini(media);
                log.info("WHATSAPP_AUDIO_TRANSCRIPTION_SUCCESS provider=gemini model={} chars={}",
                        geminiModel, transcript.length());
                return transcript;
            } catch (RuntimeException geminiFailure) {
                log.warn("WHATSAPP_AUDIO_TRANSCRIPTION_FAILURE stage=gemini model={} type={} reason={}",
                        geminiModel, geminiFailure.getClass().getSimpleName(), safeReason(geminiFailure));
                throw new IllegalStateException(
                        "WhatsApp audio transcription failed in both providers", geminiFailure);
            }
        }
    }

    private String transcribeOpenAi(MetaWhatsAppCloudClient.DownloadedMedia media) {
        String apiKey = openAi.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OpenAI transcription is not configured");
        }

        String boundary = "----helvoca-" + UUID.randomUUID();
        String contentType = normalizeAudioContentType(media.contentType());
        byte[] body = multipart(boundary, model, contentType, extension(contentType), media.bytes());

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(40))
                .header("Authorization", "Bearer " + apiKey.trim())
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "OpenAI audio transcription failed status=" + response.statusCode());
            }
            String text = new JSONObject(response.body()).optString("text", "").trim();
            if (text.isBlank()) {
                throw new IllegalStateException("OpenAI audio transcription returned no text");
            }
            return text;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OpenAI audio transcription was interrupted", e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("OpenAI audio transcription failed", e);
        }
    }

    private String transcribeGemini(MetaWhatsAppCloudClient.DownloadedMedia media) {
        String contentType = normalizeAudioContentType(media.contentType());
        JSONObject body = new JSONObject()
                .put("contents", new JSONArray().put(
                        new JSONObject()
                                .put("role", "user")
                                .put("parts", new JSONArray()
                                        .put(new JSONObject()
                                                .put("inlineData", new JSONObject()
                                                        .put("mimeType", contentType)
                                                        .put("data", Base64.getEncoder().encodeToString(media.bytes()))))
                                        .put(new JSONObject()
                                                .put("text", "Transcribe fielmente este audio. Devuelve solo las palabras habladas, sin markdown, comentarios ni explicaciones. Conserva el idioma original.")))))
                .put("generationConfig", new JSONObject()
                        .put("maxOutputTokens", 1024)
                        .put("temperature", 0));

        URI uri = URI.create(geminiBaseUrl + "/models/" + geminiModel + ":generateContent");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(40))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", gemini.getApiKey().trim())
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "Gemini audio transcription failed status=" + response.statusCode());
            }

            JSONObject json = new JSONObject(response.body());
            JSONArray candidates = json.optJSONArray("candidates");
            if (candidates == null || candidates.isEmpty()) {
                throw new IllegalStateException("Gemini audio transcription returned no candidates");
            }
            JSONObject candidate = candidates.optJSONObject(0);
            JSONObject candidateContent =
                    candidate == null ? null : candidate.optJSONObject("content");
            JSONArray parts = candidateContent == null ? null : candidateContent.optJSONArray("parts");
            if (parts == null || parts.isEmpty()) {
                throw new IllegalStateException("Gemini audio transcription returned no content");
            }
            for (int i = 0; i < parts.length(); i++) {
                JSONObject part = parts.optJSONObject(i);
                if (part == null) continue;
                String text = part.optString("text", "").trim();
                if (!text.isBlank()) return text;
            }
            throw new IllegalStateException("Gemini audio transcription returned no text");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Gemini audio transcription was interrupted", e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Gemini audio transcription failed", e);
        }
    }

    private boolean geminiConfigured() {
        return gemini != null
                && gemini.getApiKey() != null
                && !gemini.getApiKey().isBlank()
                && !geminiModel.isBlank()
                && geminiBaseUrl.startsWith("https://");
    }

    private static byte[] multipart(String boundary,
                                    String model,
                                    String contentType,
                                    String extension,
                                    byte[] audio) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            write(out, "--" + boundary + "\r\n");
            write(out, "Content-Disposition: form-data; name=\"model\"\r\n\r\n");
            write(out, model + "\r\n");
            write(out, "--" + boundary + "\r\n");
            write(out, "Content-Disposition: form-data; name=\"file\"; filename=\"voice." + extension + "\"\r\n");
            write(out, "Content-Type: " + contentType + "\r\n\r\n");
            out.write(audio);
            write(out, "\r\n--" + boundary + "--\r\n");
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Could not build audio transcription request", e);
        }
    }

    private static void write(ByteArrayOutputStream out, String value) {
        out.writeBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String normalizeAudioContentType(String value) {
        String clean = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        int separator = clean.indexOf(';');
        if (separator >= 0) clean = clean.substring(0, separator).trim();
        return clean.startsWith("audio/") ? clean : "audio/ogg";
    }

    private static String extension(String contentType) {
        return switch (contentType) {
            case "audio/mpeg", "audio/mp3" -> "mp3";
            case "audio/mp4", "audio/m4a", "audio/x-m4a" -> "m4a";
            case "audio/wav", "audio/x-wav" -> "wav";
            case "audio/webm" -> "webm";
            default -> "ogg";
        };
    }

    private static String safeReason(RuntimeException e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) return "unspecified";
        String clean = message.replaceAll("[^A-Za-z0-9 ._:=/-]", "_").trim();
        return clean.length() <= 180 ? clean : clean.substring(0, 180);
    }

    private static String require(String value, String message) {
        String clean = value == null ? "" : value.trim();
        if (clean.isBlank()) throw new IllegalArgumentException(message);
        return clean;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
