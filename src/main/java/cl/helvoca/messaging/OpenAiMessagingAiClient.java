package cl.helvoca.messaging;

import cl.helvoca.ai.realtime.OpenAiRealtimeProperties;
import cl.helvoca.ai.realtime.RealtimeToolDefinitions;
import cl.helvoca.operations.CommercialToolDefinitions;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.JsonValue;
import com.openai.models.responses.EasyInputMessage;
import com.openai.models.responses.FunctionTool;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseInputItem;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class OpenAiMessagingAiClient implements MessagingAiClient {
    private static final int MAX_TOOL_ROUNDS = 4;
    private static final Logger log = LoggerFactory.getLogger(OpenAiMessagingAiClient.class);

    private final OpenAiRealtimeProperties properties;
    private final GeminiMessagingAiFallback geminiFallback;
    private final boolean geminiPreferred;

    @Autowired
    public OpenAiMessagingAiClient(
            OpenAiRealtimeProperties properties,
            GeminiMessagingAiFallback geminiFallback,
            @Value("${GEMINI_MESSAGING_PREFERRED:false}") boolean geminiPreferred) {
        this.properties = properties;
        this.geminiFallback = geminiFallback;
        this.geminiPreferred = geminiPreferred;
    }

    public OpenAiMessagingAiClient(
            OpenAiRealtimeProperties properties,
            GeminiMessagingAiFallback geminiFallback) {
        this(properties, geminiFallback, false);
    }

    // Retained for focused unit tests that do not bootstrap the fallback component.
    public OpenAiMessagingAiClient(OpenAiRealtimeProperties properties) {
        this(properties, null, false);
    }

    @Override
    public String respond(String instructions, List<Turn> history, Set<String> allowedToolNames, ToolInvoker toolInvoker) {
        if (geminiPreferred && geminiFallback != null && geminiFallback.configured()) {
            log.info("WhatsApp messaging provider primary=gemini reason=configuration");
            try {
                return geminiFallback.respond(instructions, history, allowedToolNames, toolInvoker);
            } catch (GeminiMessagingAiFallback.ProviderUnavailableException e) {
                if (!properties.hasApiKey()) throw e;
                log.warn("Gemini messaging primary unavailable; switching provider fallback=openai");
                return respondWithOpenAi(instructions, history, allowedToolNames, toolInvoker);
            }
        }

        if (!properties.hasApiKey()) {
            if (geminiFallback != null && geminiFallback.configured()) {
                return geminiFallback.respond(instructions, history, allowedToolNames, toolInvoker);
            }
            throw new IllegalStateException("OpenAI is not configured");
        }

        try {
            return respondWithOpenAi(instructions, history, allowedToolNames, toolInvoker);
        } catch (RuntimeException e) {
            if (isRateLimit(e) && geminiFallback != null && geminiFallback.configured()) {
                log.warn("OpenAI messaging rate limited; switching provider fallback=gemini");
                return geminiFallback.respond(instructions, history, allowedToolNames, toolInvoker);
            }
            throw e;
        }
    }

    String respondWithOpenAi(
            String instructions,
            List<Turn> history,
            Set<String> allowedToolNames,
            ToolInvoker toolInvoker) {
        OpenAIClient client = OpenAIOkHttpClient.fromEnv();
        List<ResponseInputItem> inputs = new ArrayList<>();
        for (Turn turn : history) {
            EasyInputMessage.Role role = "assistant".equalsIgnoreCase(turn.role())
                    ? EasyInputMessage.Role.ASSISTANT
                    : EasyInputMessage.Role.USER;
            inputs.add(ResponseInputItem.ofEasyInputMessage(EasyInputMessage.builder()
                    .role(role)
                    .content(turn.content())
                    .build()));
        }

        ResponseCreateParams.Builder builder = ResponseCreateParams.builder()
                .model(properties.getSimulatorModel())
                .instructions(instructions)
                .maxOutputTokens(240);
        addTools(builder, allowedToolNames);

        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            builder.input(ResponseCreateParams.Input.ofResponse(inputs));
            var response = client.responses().create(builder.build());
            boolean calledTool = false;

            for (var item : response.output()) {
                if (!item.isFunctionCall()) continue;
                calledTool = true;
                var call = item.asFunctionCall();
                inputs.add(ResponseInputItem.ofFunctionCall(call));
                String result = toolInvoker.execute(call.name(), call.arguments());
                inputs.add(ResponseInputItem.ofFunctionCallOutput(
                        ResponseInputItem.FunctionCallOutput.builder()
                                .callId(call.callId())
                                .output(result)
                                .build()));
            }

            if (!calledTool) {
                String text = response.output().stream()
                        .flatMap(item -> item.message().stream())
                        .flatMap(message -> message.content().stream())
                        .flatMap(content -> content.outputText().stream())
                        .map(output -> output.text())
                        .filter(value -> value != null && !value.isBlank())
                        .findFirst().orElse("");
                if (!text.isBlank()) return sanitize(text);
                break;
            }
        }
        return "No pude completar esa solicitud. ¿Quieres intentarlo de otra forma?";
    }

    private static void addTools(ResponseCreateParams.Builder builder, Set<String> allowedToolNames) {
        JSONArray definitions = allMessagingDefinitions();
        Set<String> allowed = allowedToolNames == null ? Set.of() : allowedToolNames;
        for (int i = 0; i < definitions.length(); i++) {
            JSONObject definition = definitions.getJSONObject(i);
            String name = definition.getString("name");
            if ("transfer_to_human".equals(name) || "end_call".equals(name) || !allowed.contains(name)) continue;

            FunctionTool.Parameters.Builder parameters = FunctionTool.Parameters.builder();
            Map<String, Object> schema = definition.getJSONObject("parameters").toMap();
            for (Map.Entry<String, Object> entry : schema.entrySet()) {
                parameters.putAdditionalProperty(entry.getKey(), JsonValue.from(entry.getValue()));
            }
            builder.addTool(FunctionTool.builder()
                    .name(name)
                    .description(adaptDescription(definition.optString("description", "")))
                    .parameters(parameters.build())
                    .strict(false)
                    .build());
        }
    }

    private static JSONArray allMessagingDefinitions() {
        JSONArray combined = new JSONArray();
        append(combined, RealtimeToolDefinitions.all());
        append(combined, CommercialToolDefinitions.all());
        return combined;
    }

    private static void append(JSONArray target, JSONArray source) {
        for (int i = 0; i < source.length(); i++) target.put(source.getJSONObject(i));
    }

    private static String adaptDescription(String value) {
        return value.replace("esta llamada", "este chat")
                .replace("de esta llamada", "de este chat")
                .replace("en esta misma llamada", "en este mismo chat")
                .replace("llamada", "conversación por WhatsApp");
    }

    static boolean isRateLimit(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if ("RateLimitException".equals(current.getClass().getSimpleName())) return true;
            current = current.getCause();
        }
        return false;
    }

    private static String sanitize(String text) {
        String cleaned = text.replaceAll("[`*_#>]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (cleaned.length() > 700) cleaned = cleaned.substring(0, 700);
        return cleaned.isBlank() ? "No pude responder en este momento." : cleaned;
    }
}
