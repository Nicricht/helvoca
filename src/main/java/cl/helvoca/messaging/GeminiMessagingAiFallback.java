package cl.helvoca.messaging;

import cl.helvoca.ai.gemini.GeminiLiveProperties;
import cl.helvoca.ai.realtime.RealtimeToolDefinitions;
import cl.helvoca.operations.CommercialToolDefinitions;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Set;

@Component
public class GeminiMessagingAiFallback {
    private static final int MAX_TOOL_ROUNDS = 4;

    private final GeminiLiveProperties properties;
    private final String model;
    private final String baseUrl;
    private final HttpClient http;

    @Autowired
    public GeminiMessagingAiFallback(
            GeminiLiveProperties properties,
            @Value("${GEMINI_MESSAGING_MODEL:gemini-3.8-flash}") String model,
            @Value("${GEMINI_GENERATE_CONTENT_BASE_URL:https://generativelanguage.googleapis.com/v1beta}") String baseUrl) {
        this(properties, model, baseUrl,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build());
    }

    GeminiMessagingAiFallback(
            GeminiLiveProperties properties,
            String model,
            String baseUrl,
            HttpClient http) {
        this.properties = properties;
        this.model = clean(model);
        this.baseUrl = clean(baseUrl).replaceAll("/+$", "");
        this.http = http;
    }

    public boolean configured() {
        return properties.getApiKey() != null
                && !properties.getApiKey().isBlank()
                && !model.isBlank()
                && baseUrl.startsWith("https://");
    }

    public String respond(
            String instructions,
            List<MessagingAiClient.Turn> history,
            Set<String> allowedToolNames,
            MessagingAiClient.ToolInvoker toolInvoker) {
        if (!configured()) {
            throw new IllegalStateException("Gemini messaging fallback is not configured");
        }

        JSONArray contents = new JSONArray();
        for (MessagingAiClient.Turn turn : history) {
            String role = "assistant".equalsIgnoreCase(turn.role()) ? "model" : "user";
            contents.put(new JSONObject()
                    .put("role", role)
                    .put("parts", new JSONArray().put(new JSONObject().put("text", turn.content()))));
        }

        JSONArray declarations = functionDeclarations(allowedToolNames);
        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            JSONObject body = new JSONObject()
                    .put("systemInstruction", new JSONObject()
                            .put("parts", new JSONArray().put(new JSONObject().put("text", instructions))))
                    .put("contents", contents)
                    .put("generationConfig", new JSONObject().put("maxOutputTokens", 240));
            if (!declarations.isEmpty()) {
                body.put("tools", new JSONArray().put(
                        new JSONObject().put("functionDeclarations", declarations)));
            }

            JSONObject response = execute(body);
            JSONObject content = firstContent(response);
            JSONArray parts = content.optJSONArray("parts");
            if (parts == null || parts.isEmpty()) break;

            JSONArray functionResponses = new JSONArray();
            boolean calledTool = false;
            for (int i = 0; i < parts.length(); i++) {
                JSONObject part = parts.optJSONObject(i);
                if (part == null) continue;

                JSONObject call = part.optJSONObject("functionCall");
                if (call != null) {
                    calledTool = true;
                    String name = call.optString("name", "");
                    JSONObject args = call.optJSONObject("args");
                    String result = toolInvoker.execute(name, args == null ? "{}" : args.toString());
                    JSONObject functionResponse = new JSONObject()
                            .put("name", name)
                            .put("response", responseObject(result));
                    String callId = call.optString("id", "").trim();
                    if (!callId.isBlank()) {
                        functionResponse.put("id", callId);
                    }
                    functionResponses.put(new JSONObject()
                            .put("functionResponse", functionResponse));
                }
            }

            if (calledTool) {
                contents.put(content);
                contents.put(new JSONObject()
                        .put("role", "user")
                        .put("parts", functionResponses));
                continue;
            }

            for (int i = 0; i < parts.length(); i++) {
                JSONObject part = parts.optJSONObject(i);
                if (part == null) continue;
                String text = part.optString("text", "");
                if (!text.isBlank()) return sanitize(text);
            }
            break;
        }

        return "No pude completar esa solicitud. ¿Quieres intentarlo de otra forma?";
    }

    private JSONObject execute(JSONObject body) {
        try {
            URI uri = URI.create(baseUrl + "/models/" + model + ":generateContent");
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", properties.getApiKey().trim())
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "Gemini messaging fallback failed with HTTP " + response.statusCode());
            }
            return new JSONObject(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Gemini messaging fallback was interrupted", e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Gemini messaging fallback request failed", e);
        }
    }

    private static JSONObject firstContent(JSONObject response) {
        JSONArray candidates = response.optJSONArray("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalStateException("Gemini messaging fallback returned no candidates");
        }
        JSONObject candidate = candidates.optJSONObject(0);
        JSONObject content = candidate == null ? null : candidate.optJSONObject("content");
        if (content == null) {
            throw new IllegalStateException("Gemini messaging fallback returned no content");
        }
        return content;
    }

    private static JSONArray functionDeclarations(Set<String> allowedToolNames) {
        Set<String> allowed = allowedToolNames == null ? Set.of() : allowedToolNames;
        JSONArray combined = new JSONArray();
        appendDefinitions(combined, RealtimeToolDefinitions.all());
        appendDefinitions(combined, CommercialToolDefinitions.all());

        JSONArray out = new JSONArray();
        for (int i = 0; i < combined.length(); i++) {
            JSONObject definition = combined.getJSONObject(i);
            String name = definition.optString("name", "");
            if ("transfer_to_human".equals(name)
                    || "end_call".equals(name)
                    || !allowed.contains(name)) {
                continue;
            }
            out.put(new JSONObject()
                    .put("name", name)
                    .put("description", adaptDescription(definition.optString("description", "")))
                    .put("parameters", definition.getJSONObject("parameters")));
        }
        return out;
    }

    private static void appendDefinitions(JSONArray target, JSONArray source) {
        for (int i = 0; i < source.length(); i++) {
            target.put(source.getJSONObject(i));
        }
    }

    private static JSONObject responseObject(String raw) {
        if (raw == null || raw.isBlank()) {
            return new JSONObject().put("result", JSONObject.NULL);
        }
        try {
            return new JSONObject(raw);
        } catch (Exception ignored) {
            try {
                return new JSONObject().put("result", new JSONArray(raw));
            } catch (Exception ignoredAgain) {
                return new JSONObject().put("result", raw);
            }
        }
    }

    private static String adaptDescription(String value) {
        return value.replace("esta llamada", "este chat")
                .replace("de esta llamada", "de este chat")
                .replace("en esta misma llamada", "en este mismo chat")
                .replace("llamada", "conversación por WhatsApp");
    }

    private static String sanitize(String text) {
        String cleaned = text.replaceAll("[`*_#>]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (cleaned.length() > 700) cleaned = cleaned.substring(0, 700);
        return cleaned.isBlank() ? "No pude responder en este momento." : cleaned;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
