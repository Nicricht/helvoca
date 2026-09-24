package cl.helvoca.messaging.audio;

import cl.helvoca.ai.realtime.OpenAiRealtimeProperties;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

public class OpenAiAudioTranscriptionProvider implements AudioTranscriptionProvider {
    private final OpenAiRealtimeProperties openAi;
    private final HttpClient http;
    private final String model;
    private final String endpoint;

    public OpenAiAudioTranscriptionProvider(
            OpenAiRealtimeProperties openAi,
            HttpClient http,
            String model,
            String endpoint) {
        this.openAi = openAi;
        this.http = http;
        this.model = clean(model);
        this.endpoint = clean(endpoint);
    }

    @Override
    public String id() {
        return "openai";
    }

    @Override
    public boolean configured() {
        return openAi != null
                && notBlank(openAi.getApiKey())
                && notBlank(model)
                && endpoint.startsWith("https://");
    }

    @Override
    public TranscriptionResult transcribe(AudioInput input) {
        if (!configured()) {
            throw new AudioTranscriptionException(
                    "OPENAI_NOT_CONFIGURED",
                    id(),
                    null,
                    false,
                    "OpenAI audio transcription is not configured");
        }
        if (input == null || input.bytes() == null || input.bytes().length == 0) {
            throw new AudioTranscriptionException(
                    "AUDIO_INPUT_EMPTY",
                    id(),
                    null,
                    false,
                    "Audio input is empty");
        }

        long started = System.nanoTime();
        String boundary = "----helvoca-" + UUID.randomUUID();
        String mimeType = normalizeMime(input.mimeType());
        byte[] body = multipart(
                boundary,
                model,
                mimeType,
                extension(mimeType),
                input.bytes());

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(40))
                .header("Authorization", "Bearer " + openAi.getApiKey().trim())
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw httpFailure(status);
            }
            String text = new JSONObject(response.body()).optString("text", "").trim();
            if (text.isBlank()) {
                throw new AudioTranscriptionException(
                        "OPENAI_EMPTY_TRANSCRIPT",
                        id(),
                        status,
                        false,
                        "OpenAI audio transcription returned no text");
            }
            return new TranscriptionResult(
                    text,
                    id(),
                    model,
                    Duration.ofNanos(System.nanoTime() - started),
                    1);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AudioTranscriptionException(
                    "OPENAI_INTERRUPTED",
                    id(),
                    null,
                    false,
                    "OpenAI audio transcription was interrupted",
                    failure);
        } catch (AudioTranscriptionException failure) {
            throw failure;
        } catch (IOException failure) {
            throw new AudioTranscriptionException(
                    "OPENAI_NETWORK_ERROR",
                    id(),
                    null,
                    true,
                    "OpenAI audio transcription network failure",
                    failure);
        } catch (RuntimeException failure) {
            throw new AudioTranscriptionException(
                    "OPENAI_RESPONSE_ERROR",
                    id(),
                    null,
                    false,
                    "OpenAI audio transcription response was invalid",
                    failure);
        }
    }

    private AudioTranscriptionException httpFailure(int status) {
        return new AudioTranscriptionException(
                "OPENAI_HTTP_" + status,
                id(),
                status,
                retryable(status),
                "OpenAI audio transcription failed status=" + status);
    }

    private static boolean retryable(int status) {
        return status == 408 || status == 429 || status >= 500;
    }

    private static byte[] multipart(
            String boundary,
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
            write(out, "Content-Disposition: form-data; name=\"file\"; filename=\"voice."
                    + extension + "\"\r\n");
            write(out, "Content-Type: " + contentType + "\r\n\r\n");
            out.write(audio);
            write(out, "\r\n--" + boundary + "--\r\n");
            return out.toByteArray();
        } catch (IOException failure) {
            throw new IllegalStateException("Could not build audio transcription request", failure);
        }
    }

    private static void write(ByteArrayOutputStream out, String value) throws IOException {
        out.write(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String normalizeMime(String value) {
        String mime = clean(value).toLowerCase(Locale.ROOT);
        int separator = mime.indexOf(';');
        if (separator >= 0) mime = mime.substring(0, separator).trim();
        return mime.startsWith("audio/") ? mime : "audio/ogg";
    }

    private static String extension(String mimeType) {
        if (mimeType.contains("mpeg")) return "mp3";
        if (mimeType.contains("mp4")) return "m4a";
        if (mimeType.contains("webm")) return "webm";
        if (mimeType.contains("aac")) return "aac";
        if (mimeType.contains("wav")) return "wav";
        return "ogg";
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
