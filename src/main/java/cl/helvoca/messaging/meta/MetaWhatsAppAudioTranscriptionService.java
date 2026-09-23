package cl.helvoca.messaging.meta;

import cl.helvoca.ai.realtime.OpenAiRealtimeProperties;
import org.json.JSONObject;
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
import java.util.Locale;
import java.util.UUID;

@Service
public class MetaWhatsAppAudioTranscriptionService {
    private final MetaWhatsAppCloudClient meta;
    private final MetaWhatsAppAccessTokenResolver accessTokens;
    private final OpenAiRealtimeProperties openAi;
    private final HttpClient http;
    private final String model;
    private final String endpoint;

    @Autowired
    public MetaWhatsAppAudioTranscriptionService(
            MetaWhatsAppCloudClient meta,
            MetaWhatsAppAccessTokenResolver accessTokens,
            OpenAiRealtimeProperties openAi,
            @Value("${OPENAI_WHATSAPP_TRANSCRIPTION_MODEL:gpt-transcribe}") String model,
            @Value("${OPENAI_AUDIO_TRANSCRIPTIONS_URL:https://api.openai.com/v1/audio/transcriptions}") String endpoint) {
        this(meta, accessTokens, openAi,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build(),
                model, endpoint);
    }

    MetaWhatsAppAudioTranscriptionService(
            MetaWhatsAppCloudClient meta,
            MetaWhatsAppAccessTokenResolver accessTokens,
            OpenAiRealtimeProperties openAi,
            HttpClient http,
            String model,
            String endpoint) {
        this.meta = meta;
        this.accessTokens = accessTokens;
        this.openAi = openAi;
        this.http = http;
        this.model = require(model, "WhatsApp transcription model is required");
        this.endpoint = require(endpoint, "Audio transcription endpoint is required");
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

        MetaWhatsAppCloudClient.DownloadedMedia media = meta.downloadMedia(mediaId, metaToken);
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

    private static String require(String value, String message) {
        String clean = value == null ? "" : value.trim();
        if (clean.isBlank()) throw new IllegalArgumentException(message);
        return clean;
    }
}
