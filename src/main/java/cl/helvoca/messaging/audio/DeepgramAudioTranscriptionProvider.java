package cl.helvoca.messaging.audio;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

public class DeepgramAudioTranscriptionProvider implements AudioTranscriptionProvider {
    private final WhatsAppAudioTranscriptionProperties properties;
    private final HttpClient http;

    public DeepgramAudioTranscriptionProvider(
            WhatsAppAudioTranscriptionProperties properties,
            HttpClient http) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.http = Objects.requireNonNull(http, "http");
    }

    @Override
    public String id() {
        return "deepgram";
    }

    @Override
    public boolean configured() {
        return properties.isDeepgramEnabled()
                && !properties.getDeepgramApiKey().isBlank()
                && !properties.getDeepgramModel().isBlank()
                && validEndpoint(properties.getDeepgramEndpoint());
    }

    @Override
    public TranscriptionResult transcribe(AudioInput input) {
        if (!configured()) {
            throw new AudioTranscriptionException(
                    "DEEPGRAM_NOT_CONFIGURED", id(), null, false,
                    "Deepgram audio transcription is not configured");
        }
        if (input == null || input.bytes() == null || input.bytes().length == 0) {
            throw new AudioTranscriptionException(
                    "AUDIO_INPUT_EMPTY", id(), null, false,
                    "Audio input is empty");
        }

        URI uri = requestUri();
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(properties.getDeepgramTimeoutSeconds()))
                .header("Authorization", "Token " + properties.getDeepgramApiKey())
                .header("Content-Type", normalizeMime(input.mimeType()))
                .POST(HttpRequest.BodyPublishers.ofByteArray(input.bytes()))
                .build();

        long started = System.nanoTime();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw new AudioTranscriptionException(
                        "DEEPGRAM_HTTP_" + status,
                        id(),
                        status,
                        retryable(status),
                        "Deepgram audio transcription failed status=" + status);
            }

            String transcript = extractTranscript(response.body());
            if (transcript.isBlank()) {
                throw new AudioTranscriptionException(
                        "DEEPGRAM_EMPTY_TRANSCRIPT",
                        id(),
                        status,
                        false,
                        "Deepgram audio transcription returned no text");
            }

            return new TranscriptionResult(
                    transcript,
                    id(),
                    properties.getDeepgramModel(),
                    Duration.ofNanos(System.nanoTime() - started),
                    1);
        } catch (HttpTimeoutException failure) {
            throw new AudioTranscriptionException(
                    "DEEPGRAM_TIMEOUT", id(), null, true,
                    "Deepgram audio transcription timed out", failure);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AudioTranscriptionException(
                    "DEEPGRAM_INTERRUPTED", id(), null, false,
                    "Deepgram audio transcription was interrupted", failure);
        } catch (AudioTranscriptionException failure) {
            throw failure;
        } catch (IOException failure) {
            throw new AudioTranscriptionException(
                    "DEEPGRAM_NETWORK_ERROR", id(), null, true,
                    "Deepgram audio transcription network failure", failure);
        } catch (RuntimeException failure) {
            throw new AudioTranscriptionException(
                    "DEEPGRAM_RESPONSE_ERROR", id(), null, false,
                    "Deepgram audio transcription response was invalid", failure);
        }
    }

    private URI requestUri() {
        String base = properties.getDeepgramEndpoint().replaceAll("[?&]+$", "");
        String separator = base.contains("?") ? "&" : "?";
        return URI.create(base + separator
                + "model=" + encode(properties.getDeepgramModel())
                + "&smart_format=true"
                + "&language=" + encode(properties.getDeepgramLanguage()));
    }

    private static String extractTranscript(String body) {
        JSONObject root = new JSONObject(body);
        JSONObject results = root.optJSONObject("results");
        JSONArray channels = results == null ? null : results.optJSONArray("channels");
        if (channels == null || channels.isEmpty()) return "";
        JSONObject channel = channels.optJSONObject(0);
        JSONArray alternatives = channel == null ? null : channel.optJSONArray("alternatives");
        if (alternatives == null || alternatives.isEmpty()) return "";
        JSONObject first = alternatives.optJSONObject(0);
        return first == null ? "" : first.optString("transcript", "").trim();
    }

    private static boolean retryable(int status) {
        return status == 408 || status == 429 || status >= 500;
    }

    private static String normalizeMime(String value) {
        String mime = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        int separator = mime.indexOf(';');
        if (separator >= 0) mime = mime.substring(0, separator).trim();
        return mime.startsWith("audio/") ? mime : "audio/ogg";
    }

    private static boolean validEndpoint(String value) {
        try {
            URI uri = URI.create(value == null ? "" : value.trim());
            if ("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null) return true;
            return "http".equalsIgnoreCase(uri.getScheme())
                    && ("127.0.0.1".equals(uri.getHost()) || "localhost".equalsIgnoreCase(uri.getHost()));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value.trim(), StandardCharsets.UTF_8);
    }
}
