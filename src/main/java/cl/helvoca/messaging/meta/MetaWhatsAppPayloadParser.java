package cl.helvoca.messaging.meta;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class MetaWhatsAppPayloadParser {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MetaWhatsAppPayloadParser() {
    }

    static List<MetaWhatsAppInboundMessage> parseTextMessages(byte[] body) throws IOException {
        if (body == null || body.length == 0) {
            return List.of();
        }

        JsonNode root = MAPPER.readTree(body);
        List<MetaWhatsAppInboundMessage> messages = new ArrayList<>();

        JsonNode entries = root.path("entry");
        if (!entries.isArray()) {
            return List.of();
        }

        for (JsonNode entry : entries) {
            JsonNode changes = entry.path("changes");
            if (!changes.isArray()) {
                continue;
            }

            for (JsonNode change : changes) {
                JsonNode value = change.path("value");
                String phoneNumberId = text(value.path("metadata").path("phone_number_id"));
                JsonNode payloadMessages = value.path("messages");
                if (phoneNumberId == null || !payloadMessages.isArray()) {
                    continue;
                }

                for (JsonNode message : payloadMessages) {
                    if (!"text".equals(text(message.path("type")))) {
                        continue;
                    }

                    String messageId = text(message.path("id"));
                    String from = text(message.path("from"));
                    String bodyText = text(message.path("text").path("body"));
                    if (messageId == null || from == null || bodyText == null) {
                        continue;
                    }

                    messages.add(new MetaWhatsAppInboundMessage(
                            messageId,
                            phoneNumberId,
                            from,
                            bodyText));
                }
            }
        }

        return List.copyOf(messages);
    }

    private static String text(JsonNode node) {
        if (node == null || !node.isTextual()) {
            return null;
        }
        String value = node.asText();
        return value.isBlank() ? null : value;
    }
}
