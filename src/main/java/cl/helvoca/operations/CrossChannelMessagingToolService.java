package cl.helvoca.operations;

import cl.helvoca.messaging.outbound.MessagingProviderRegistry;
import cl.helvoca.messaging.outbound.OutboundDispatchOutboxService;
import cl.helvoca.messaging.outbound.OutboundMessage;
import cl.helvoca.messaging.outbound.OutboundMessagingProperties;
import cl.helvoca.messaging.outbound.OutboundMessagingService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Bridges a verified customer operation from the current channel to WhatsApp.
 * The model never supplies arbitrary recipients, message bodies or URLs: those
 * are resolved by the outbound messaging engine from tenant-scoped backend data.
 */
@Service
public class CrossChannelMessagingToolService {
    public static final String TOOL_NAME = "send_whatsapp_operation";

    private final OutboundMessagingService outbound;
    private final OutboundMessagingProperties properties;
    private final MessagingProviderRegistry providers;
    private final OutboundDispatchOutboxService outbox;
    private final CatalogShowcaseMessagingService showcase;

    public CrossChannelMessagingToolService(OutboundMessagingService outbound,
                                            OutboundMessagingProperties properties,
                                            MessagingProviderRegistry providers,
                                            OutboundDispatchOutboxService outbox,
                                            CatalogShowcaseMessagingService showcase) {
        this.outbound = outbound;
        this.properties = properties;
        this.providers = providers;
        this.outbox = outbox;
        this.showcase = showcase;
    }

    @Transactional
    public JSONObject execute(UUID businessId, UUID customerId, String rawArguments) {
        if (businessId == null) return error("TENANT_CONTEXT_REQUIRED", "No pude verificar el negocio actual.");
        if (customerId == null) return error("CUSTOMER_CONTEXT_REQUIRED", "Primero necesito identificar al cliente de forma segura.");

        try {
            JSONObject args = rawArguments == null || rawArguments.isBlank()
                    ? new JSONObject()
                    : new JSONObject(rawArguments);
            UUID operationId = UUID.fromString(required(args, "operationId"));
            OutboundMessage.Purpose purpose = purpose(required(args, "purpose"));
            UUID identityId = optionalUuid(args, "recipientIdentityId");

            if (purpose == OutboundMessage.Purpose.PRODUCT_SHOWCASE) {
                List<UUID> catalogItemIds = requiredUuidList(args, "catalogItemIds", 3);
                List<CatalogShowcaseMessagingService.PreparedShowcaseMessage> messages =
                        showcase.prepare(businessId, customerId, operationId, identityId, catalogItemIds);

                JSONArray preparedMessages = new JSONArray();
                for (CatalogShowcaseMessagingService.PreparedShowcaseMessage value : messages) {
                    preparedMessages.put(new JSONObject()
                            .put("messageId", value.message().getId().toString())
                            .put("catalogItemId", value.catalogItemId().toString())
                            .put("catalogItemName", value.catalogItemName())
                            .put("catalogMediaId", value.catalogMediaId().toString())
                            .put("mediaType", value.mediaType().name())
                            .put("status", value.message().getStatus().name()));
                }

                JSONObject prepared = new JSONObject()
                        .put("operationId", operationId.toString())
                        .put("channel", OutboundMessage.Channel.WHATSAPP.name())
                        .put("purpose", purpose.name())
                        .put("messageCount", messages.size())
                        .put("messages", preparedMessages)
                        .put("prepared", true)
                        .put("sent", false);

                if (!properties.isDeliveryEnabled()) {
                    return errorWithData(
                            "WHATSAPP_DELIVERY_DISABLED",
                            "La muestra quedó preparada, pero el envío real por WhatsApp está desactivado.",
                            prepared);
                }

                providers.require(properties.getProvider(), OutboundMessage.Channel.WHATSAPP);
                for (CatalogShowcaseMessagingService.PreparedShowcaseMessage value : messages) {
                    outbox.queue(businessId, value.message().getId());
                }
                prepared.put("status", OutboundMessage.Status.QUEUED.name());
                prepared.put("queued", true);
                return success(prepared);
            }

            OutboundMessage message = outbound.prepare(
                    businessId,
                    customerId,
                    OutboundMessage.Channel.WHATSAPP,
                    purpose,
                    operationId,
                    identityId);

            JSONObject prepared = new JSONObject()
                    .put("messageId", message.getId().toString())
                    .put("operationId", operationId.toString())
                    .put("channel", message.getChannel().name())
                    .put("purpose", message.getPurpose().name())
                    .put("status", message.getStatus().name())
                    .put("prepared", true)
                    .put("sent", false);

            if (!properties.isDeliveryEnabled()) {
                return errorWithData(
                        "WHATSAPP_DELIVERY_DISABLED",
                        "El mensaje quedó preparado, pero el envío real por WhatsApp está desactivado.",
                        prepared);
            }

            // Fail before queueing when the configured provider cannot actually
            // serve WhatsApp. This avoids durable jobs that can never dispatch.
            providers.require(properties.getProvider(), OutboundMessage.Channel.WHATSAPP);
            outbox.queue(businessId, message.getId());
            prepared.put("status", OutboundMessage.Status.QUEUED.name());
            prepared.put("queued", true);
            return success(prepared);
        } catch (IllegalArgumentException e) {
            return error("INVALID_ARGUMENT", safeMessage(e, "Los datos para el envío por WhatsApp no son válidos."));
        } catch (IllegalStateException e) {
            return error("WHATSAPP_NOT_READY", safeMessage(e, "WhatsApp todavía no está listo para enviar este mensaje."));
        } catch (Exception e) {
            return error("WHATSAPP_PREPARATION_FAILED", "No pude preparar el mensaje de WhatsApp de forma segura.");
        }
    }

    private static OutboundMessage.Purpose purpose(String raw) {
        try {
            return OutboundMessage.Purpose.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new IllegalArgumentException("Propósito de WhatsApp no soportado.");
        }
    }

    private static List<UUID> requiredUuidList(JSONObject args, String key, int maxItems) {
        JSONArray raw = args.optJSONArray(key);
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException("Falta el argumento " + key + ".");
        }
        if (raw.length() > maxItems) {
            throw new IllegalArgumentException("Se pueden enviar como máximo " + maxItems + " productos por muestra.");
        }
        List<UUID> values = new ArrayList<>();
        for (int i = 0; i < raw.length(); i++) {
            String value = raw.optString(i, "").trim();
            if (value.isBlank()) throw new IllegalArgumentException("Un identificador de catálogo es inválido.");
            values.add(UUID.fromString(value));
        }
        return List.copyOf(values);
    }

    private static UUID optionalUuid(JSONObject args, String key) {
        if (!args.has(key) || args.isNull(key)) return null;
        String raw = args.optString(key, "").trim();
        return raw.isBlank() ? null : UUID.fromString(raw);
    }

    private static String required(JSONObject args, String key) {
        String value = args.optString(key, null);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Falta el argumento " + key + ".");
        return value.trim();
    }

    private static String safeMessage(RuntimeException e, String fallback) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? fallback : message;
    }

    private static JSONObject success(JSONObject data) {
        return new JSONObject().put("success", true).put("data", data).put("error", JSONObject.NULL);
    }

    private static JSONObject error(String code, String message) {
        return new JSONObject().put("success", false).put("data", JSONObject.NULL)
                .put("error", new JSONObject().put("code", code).put("message", message));
    }

    private static JSONObject errorWithData(String code, String message, JSONObject data) {
        return new JSONObject().put("success", false).put("data", data)
                .put("error", new JSONObject().put("code", code).put("message", message));
    }
}
