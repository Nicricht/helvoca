package cl.helvoca.operations;

import cl.helvoca.catalog.CatalogItem;
import cl.helvoca.catalog.CatalogItemRepository;
import cl.helvoca.messaging.MessagingConversation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ShowcaseSelectionContextService {
    private static final int MAX_SHOWCASE_ITEMS = 3;

    private final ConversationStateService conversationState;
    private final BusinessOperationRepository operations;
    private final CatalogItemRepository catalog;

    public ShowcaseSelectionContextService(ConversationStateService conversationState,
                                           BusinessOperationRepository operations,
                                           CatalogItemRepository catalog) {
        this.conversationState = conversationState;
        this.operations = operations;
        this.catalog = catalog;
    }

    /**
     * Produces backend-authoritative hidden instructions for ordinal references
     * such as "el segundo". No media URL or customer-facing identifier is exposed.
     */
    @Transactional
    public String instructions(MessagingConversation conversation) {
        if (conversation == null
                || conversation.getBusinessId() == null
                || conversation.getId() == null
                || conversation.getCustomerId() == null) {
            return "";
        }

        ConversationOperationState state = conversationState.find(
                conversation.getBusinessId(),
                conversation.getId(),
                BusinessOrder.Source.WHATSAPP);
        if (state == null || state.getActiveOperationId() == null) return "";

        BusinessOperation operation = operations
                .findByIdAndBusinessId(state.getActiveOperationId(), conversation.getBusinessId())
                .orElse(null);
        if (operation == null
                || operation.getCustomerId() == null
                || !operation.getCustomerId().equals(conversation.getCustomerId())) {
            return "";
        }

        Map<String, Object> metadata = operation.getMetadata();
        if (metadata == null || !"WHATSAPP".equals(String.valueOf(metadata.get("handoffChannel")))) {
            return "";
        }

        List<UUID> orderedIds = orderedShowcaseIds(metadata.get("showcaseCatalogItemIds"));
        if (orderedIds.isEmpty()) return "";

        List<ResolvedItem> items = new ArrayList<>();
        for (UUID itemId : orderedIds) {
            CatalogItem item = catalog.findByIdAndBusinessId(itemId, conversation.getBusinessId())
                    .filter(CatalogItem::isActive)
                    .orElse(null);
            if (item != null) items.add(new ResolvedItem(item.getId(), item.getName()));
        }
        if (items.isEmpty()) return "";

        StringBuilder out = new StringBuilder(
                "\nCONTEXTO VISUAL BACKEND-AUTORITATIVO DE LA OPERACIÓN ACTIVA. ");
        out.append("operationId=").append(operation.getId()).append(". ");
        out.append("El último escaparate enviado por WhatsApp, en el orden exacto en que el cliente lo recibió, es: ");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) out.append("; ");
            ResolvedItem item = items.get(i);
            out.append(i + 1)
                    .append(" = ")
                    .append(item.name())
                    .append(" [catalogItemId=")
                    .append(item.id())
                    .append("]");
        }
        out.append(". Si el cliente dice primero/segundo/tercero, 1/2/3, este/ese producto o una referencia ordinal equivalente inequívoca, resuélvela SOLO contra esta lista y conserva este mismo operationId. ");
        out.append("Nunca inventes una posición ni cambies el orden. Si la posición pedida no existe o la referencia es ambigua, pregunta cuál producto quiere. ");
        out.append("No muestres UUID ni detalles internos al cliente.");
        return out.toString();
    }

    private static List<UUID> orderedShowcaseIds(Object raw) {
        if (!(raw instanceof Collection<?> collection)) return List.of();

        List<UUID> ids = new ArrayList<>();
        for (Object value : collection) {
            if (ids.size() >= MAX_SHOWCASE_ITEMS) break;
            try {
                UUID id = UUID.fromString(String.valueOf(value));
                if (!ids.contains(id)) ids.add(id);
            } catch (Exception ignored) {
                // Corrupt or stale metadata is ignored and never guessed.
            }
        }
        return List.copyOf(ids);
    }

    private record ResolvedItem(UUID id, String name) {}
}
