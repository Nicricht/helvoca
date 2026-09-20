package cl.helvoca.messaging.meta;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class MetaWhatsAppPayloadParser {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MetaWhatsAppPayloadParser() {
    }

    static List<MetaWhatsAppInboundMessage> parseTextMessages(byte[] body) throws IOException {
        JsonNode root = parse(body);
        if (root == null) return List.of();

        List<MetaWhatsAppInboundMessage> messages = new ArrayList<>();
        for (JsonNode value : values(root)) {
            String phoneNumberId = text(value.path("metadata").path("phone_number_id"));
            JsonNode payloadMessages = value.path("messages");
            if (phoneNumberId == null || !payloadMessages.isArray()) continue;

            for (JsonNode message : payloadMessages) {
                if (!"text".equals(text(message.path("type")))) continue;

                String messageId = text(message.path("id"));
                String from = text(message.path("from"));
                String bodyText = text(message.path("text").path("body"));
                if (messageId == null || from == null || bodyText == null) continue;

                messages.add(new MetaWhatsAppInboundMessage(
                        messageId,
                        phoneNumberId,
                        from,
                        bodyText));
            }
        }
        return List.copyOf(messages);
    }

    static List<MetaWhatsAppDeliveryStatus> parseDeliveryStatuses(byte[] body) throws IOException {
        JsonNode root = parse(body);
        if (root == null) return List.of();

        List<MetaWhatsAppDeliveryStatus> statuses = new ArrayList<>();
        for (JsonNode value : values(root)) {
            String phoneNumberId = text(value.path("metadata").path("phone_number_id"));
            JsonNode payloadStatuses = value.path("statuses");
            if (phoneNumberId == null || !payloadStatuses.isArray()) continue;

            for (JsonNode status : payloadStatuses) {
                String messageId = text(status.path("id"));
                String state = text(status.path("status"));
                if (messageId == null || state == null) continue;

                statuses.add(new MetaWhatsAppDeliveryStatus(
                        messageId,
                        phoneNumberId,
                        state,
                        timestamp(status.path("timestamp")),
                        firstErrorCode(status.path("errors"))));
            }
        }
        return List.copyOf(statuses);
    }

    private static JsonNode parse(byte[] body) throws IOException {
        if (body == null || body.length == 0) return null;
        return MAPPER.readTree(body);
    }

    private static List<JsonNode> values(JsonNode root) {
        List<JsonNode> values = new ArrayList<>();
        JsonNode entries = root.path("entry");
        if (!entries.isArray()) return values;

        for (JsonNode entry : entries) {
            JsonNode changes = entry.path("changes");
            if (!changes.isArray()) continue;
            for (JsonNode change : changes) {
                JsonNode value = change.path("value");
                if (value.isObject()) values.add(value);
            }
        }
        return values;
    }

    private static Instant timestamp(JsonNode node) {
        String value = scalar(node);
        if (value == null) return null;
        try {
            return Instant.ofEpochSecond(Long.parseLong(value));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String firstErrorCode(JsonNode errors) {
        if (!errors.isArray() || errors.size() == 0) return null;
        return scalar(errors.get(0).path("code"));
    }

    private static String scalar(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || node.isContainerNode()) return null;
        String value = node.asText();
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String text(JsonNode node) {
        if (node == null || !node.isTextual()) return null;
        String value = node.asText();
        return value.isBlank() ? null : value;
    }
}
