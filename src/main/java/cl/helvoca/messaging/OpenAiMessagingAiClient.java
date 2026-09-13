package cl.helvoca.messaging;

import cl.helvoca.ai.realtime.OpenAiRealtimeProperties;
import cl.helvoca.ai.realtime.RealtimeToolDefinitions;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.JsonValue;
import com.openai.models.responses.EasyInputMessage;
import com.openai.models.responses.FunctionTool;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseInputItem;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class OpenAiMessagingAiClient implements MessagingAiClient {
    private static final int MAX_TOOL_ROUNDS = 4;

    private final OpenAiRealtimeProperties properties;

    public OpenAiMessagingAiClient(OpenAiRealtimeProperties properties) {
        this.properties = properties;
    }

    @Override
    public String respond(String instructions, List<Turn> history, ToolInvoker toolInvoker) {
        if (!properties.hasApiKey()) throw new IllegalStateException("OpenAI is not configured");

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
        addTools(builder);

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

    private static void addTools(ResponseCreateParams.Builder builder) {
        JSONArray definitions = RealtimeToolDefinitions.all();
        for (int i = 0; i < definitions.length(); i++) {
            JSONObject definition = definitions.getJSONObject(i);
            String name = definition.getString("name");
            if ("transfer_to_human".equals(name)) continue;

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
}
