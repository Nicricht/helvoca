package cl.helvoca.messaging.audio;

import cl.helvoca.ai.gemini.GeminiLiveProperties;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;

public class GeminiAudioTranscriptionProvider implements AudioTranscriptionProvider {
    private static final int PRIMARY_MAX_ATTEMPTS = 2;
    private static final long RETRY_DELAY_MILLIS = 200L;

    private final GeminiLiveProperties gemini;
    private final HttpClient http;
    private final String model;
    private final String fallbackModel;
    private final String baseUrl;

    public GeminiAudioTranscriptionProvider(
            GeminiLiveProperties gemini,
            HttpClient http,
            String model,
            String fallbackModel,
            String baseUrl) {
        this.gemini = gemini;
        this.http = http;
        this.model = clean(model);
        this.fallbackModel = clean(fallbackModel);
        this.baseUrl = clean(baseUrl).replaceAll("/+$", "");
    }

    @Override
    public String id() {
        return "gemini";
    }

    @Override
    public boolean configured() {
        return gemini != null
                && notBlank(gemini.getApiKey())
                && notBlank(model)
                && baseUrl.startsWith("https://");
    }

    @Override
    public TranscriptionResult transcribe(AudioInput input) {
        if (!configured()) {
            throw new AudioTranscriptionException(
                    "GEMINI_NOT_CONFIGURED",
                    id(),
                    null,
                    false,
                    "Gemini audio transcription is not configured");
        }
        if (input == null || input.bytes() == null || input.bytes().length == 0) {
            throw new AudioTranscriptionException(
                    "AUDIO_INPUT_EMPTY",
                    id(),
                    null,
                    false,
                    "Audio input is empty");
        }

        AudioTranscriptionException primaryFailure = null;
        for (int attempt = 1; attempt <= PRIMARY_MAX_ATTEMPTS; attempt++) {
            try {
                return transcribeModel(input, model, attempt);
            } catch (AudioTranscriptionException failure) {
                primaryFailure = failure;
                if (failure.httpStatus() != null && failure.httpStatus() == 429) {
                    break;
                }
                if (!failure.retryable() || attempt >= PRIMARY_MAX_ATTEMPTS) {
                    break;
                }
                sleepBeforeRetry();
            }
        }

        if (fallbackConfigured()) {
            return transcribeModel(input, fallbackModel, PRIMARY_MAX_ATTEMPTS + 1);
        }
        if (primaryFailure != null) {
            throw primaryFailure;
        }
        throw new AudioTranscriptionException(
                "GEMINI_TRANSCRIPTION_FAILED",
                id(),
                null,
                true,
                "Gemini audio transcription failed");
    }

    private TranscriptionResult transcribeModel(AudioInput input, String modelName, int attemptCount) {
        long started = System.nanoTime();
        String mimeType = normalizeMime(input.mimeType());
        JSONObject body = new JSONObject()
                .put("contents", new JSONArray().put(
                        new JSONObject()
                                .put("role", "user")
                                .put("parts", new JSONArray()
                                        .put(new JSONObject()
                                                .put("inlineData", new JSONObject()
                                                        .put("mimeType", mimeType)
                                                        .put("data", Base64.getEncoder()
                                                                .encodeToString(input.bytes()))))
                                        .put(new JSONObject()
                                                .put("text", "Transcribe fielmente este audio. Devuelve solo las palabras habladas, sin markdown, comentarios ni explicaciones. Conserva el idioma original.")))))
                .put("generationConfig", new JSONObject()
                        .put("maxOutputTokens", 1024)
                        .put("temperature", 0));

        URI uri = URI.create(baseUrl + "/models/" + modelName + ":generateContent");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(40))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", gemini.getApiKey().trim())
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw httpFailure(status);
            }

            String text = extractText(response.body());
            if (text.isBlank()) {
                throw new AudioTranscriptionException(
                        "GEMINI_EMPTY_TRANSCRIPT",
                        id(),
                        status,
                        false,
                        "Gemini audio transcription returned no text");
            }
            return new TranscriptionResult(
                    text,
                    id(),
                    modelName,
                    Duration.ofNanos(System.nanoTime() - started),
                    attemptCount);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AudioTranscriptionException(
                    "GEMINI_INTERRUPTED",
                    id(),
                    null,
                    false,
                    "Gemini audio transcription was interrupted",
                    failure);
        } catch (AudioTranscriptionException failure) {
            throw failure;
        } catch (IOException failure) {
            throw new AudioTranscriptionException(
                    "GEMINI_NETWORK_ERROR",
                    id(),
                    null,
                    true,
                    "Gemini audio transcription network failure",
                    failure);
        } catch (RuntimeException failure) {
            throw new AudioTranscriptionException(
                    "GEMINI_RESPONSE_ERROR",
                    id(),
                    null,
                    false,
                    "Gemini audio transcription response was invalid",
                    failure);
        }
    }

    private String extractText(String responseBody) {
        JSONObject json = new JSONObject(responseBody);
        JSONArray candidates = json.optJSONArray("candidates");
        if (candidates == null || candidates.isEmpty()) return "";
        JSONObject candidate = candidates.optJSONObject(0);
        JSONObject content = candidate == null ? null : candidate.optJSONObject("content");
        JSONArray parts = content == null ? null : content.optJSONArray("parts");
        if (parts == null) return "";
        for (int i = 0; i < parts.length(); i++) {
            JSONObject part = parts.optJSONObject(i);
            if (part == null) continue;
            String text = part.optString("text", "").trim();
            if (!text.isBlank()) return text;
        }
        return "";
    }

    private AudioTranscriptionException httpFailure(int status) {
        return new AudioTranscriptionException(
                "GEMINI_HTTP_" + status,
                id(),
                status,
                retryable(status),
                "Gemini audio transcription failed status=" + status);
    }

    private boolean fallbackConfigured() {
        return configured()
                && !fallbackModel.isBlank()
                && !fallbackModel.equals(model);
    }

    private static boolean retryable(int status) {
        return status == 408 || status == 429 || status >= 500;
    }

    private static void sleepBeforeRetry() {
        try {
            Thread.sleep(RETRY_DELAY_MILLIS);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AudioTranscriptionException(
                    "GEMINI_RETRY_INTERRUPTED",
                    "gemini",
                    null,
                    false,
                    "Gemini audio transcription retry was interrupted",
                    failure);
        }
    }

    private static String normalizeMime(String value) {
        String mime = clean(value).toLowerCase(Locale.ROOT);
        int separator = mime.indexOf(';');
        if (separator >= 0) mime = mime.substring(0, separator).trim();
        return mime.startsWith("audio/") ? mime : "audio/ogg";
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
