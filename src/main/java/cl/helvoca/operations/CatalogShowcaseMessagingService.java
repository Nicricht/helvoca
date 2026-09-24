package cl.helvoca.operations;

import cl.helvoca.catalog.CatalogItem;
import cl.helvoca.catalog.CatalogItemRepository;
import cl.helvoca.catalog.CatalogMedia;
import cl.helvoca.catalog.CatalogMediaRepository;
import cl.helvoca.messaging.outbound.OutboundMessage;
import cl.helvoca.messaging.outbound.OutboundMessagingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class CatalogShowcaseMessagingService {
    private static final int MAX_PRODUCTS = 3;

    private final CatalogItemRepository catalog;
    private final CatalogMediaRepository media;
    private final BusinessOperationRepository operations;
    private final OutboundMessagingService outbound;

    public CatalogShowcaseMessagingService(CatalogItemRepository catalog,
                                           CatalogMediaRepository media,
                                           BusinessOperationRepository operations,
                                           OutboundMessagingService outbound) {
        this.catalog = catalog;
        this.media = media;
        this.operations = operations;
        this.outbound = outbound;
    }

    @Transactional
    public List<PreparedShowcaseMessage> prepare(UUID businessId,
                                                 UUID customerId,
                                                 UUID operationId,
                                                 UUID recipientIdentityId,
                                                 List<UUID> catalogItemIds) {
        if (businessId == null || customerId == null || operationId == null) {
            throw new IllegalArgumentException("businessId, customerId and operationId are required");
        }
        if (catalogItemIds == null || catalogItemIds.isEmpty()) {
            throw new IllegalArgumentException("At least one catalog item is required");
        }

        BusinessOperation operation = operations.findByIdAndBusinessId(operationId, businessId)
                .orElseThrow(() -> new IllegalArgumentException("Operation not found"));
        if (operation.getCustomerId() == null || !operation.getCustomerId().equals(customerId)) {
            throw new IllegalArgumentException("Operation does not belong to customer");
        }

        Set<UUID> unique = new LinkedHashSet<>(catalogItemIds);
        if (unique.size() != catalogItemIds.size()) {
            throw new IllegalArgumentException("Catalog items cannot be repeated");
        }
        if (unique.size() > MAX_PRODUCTS) {
            throw new IllegalArgumentException("A product showcase can contain at most 3 catalog items");
        }

        List<Selection> selections = new ArrayList<>();
        for (UUID catalogItemId : unique) {
            if (catalogItemId == null) throw new IllegalArgumentException("Catalog item id is required");
            CatalogItem item = catalog.findByIdAndBusinessId(catalogItemId, businessId)
                    .filter(CatalogItem::isActive)
                    .orElseThrow(() -> new IllegalArgumentException("Catalog item is missing or inactive"));

            CatalogMedia primary = media
                    .findAllByBusinessIdAndCatalogItemIdAndActiveTrueOrderBySortOrderAscCreatedAtAsc(
                            businessId, item.getId())
                    .stream()
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Catalog item has no active media configured: " + item.getName()));

            selections.add(new Selection(item, primary));
        }

        List<PreparedShowcaseMessage> prepared = new ArrayList<>();
        for (Selection selection : selections) {
            OutboundMessage message = outbound.prepareCatalogMedia(
                    businessId,
                    customerId,
                    operationId,
                    recipientIdentityId,
                    selection.media().getId());
            prepared.add(new PreparedShowcaseMessage(
                    message,
                    selection.item().getId(),
                    selection.item().getName(),
                    selection.media().getId(),
                    selection.media().getMediaType()));
        }

        Map<String, Object> metadata = operation.getMetadata() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(operation.getMetadata());
        metadata.put("handoffChannel", "WHATSAPP");
        metadata.put("commercialStage", "MEDIA_PREPARED");
        metadata.put("showcaseCatalogItemIds", unique.stream().map(UUID::toString).toList());
        metadata.put("showcaseMessageCount", prepared.size());
        metadata.put("lastAction", "PRODUCT_SHOWCASE_PREPARED");
        operation.setMetadata(metadata);
        operations.saveAndFlush(operation);

        return List.copyOf(prepared);
    }

    @Transactional
    public void markQueued(UUID businessId, UUID operationId, int messageCount) {
        BusinessOperation operation = operations.findByIdAndBusinessId(operationId, businessId)
                .orElseThrow(() -> new IllegalArgumentException("Operation not found"));
        Map<String, Object> metadata = operation.getMetadata() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(operation.getMetadata());
        metadata.put("handoffChannel", "WHATSAPP");
        metadata.put("commercialStage", "MEDIA_QUEUED");
        metadata.put("showcaseMessageCount", messageCount);
        metadata.put("lastAction", "PRODUCT_SHOWCASE_QUEUED");
        operation.setMetadata(metadata);
        operations.saveAndFlush(operation);
    }

    public record PreparedShowcaseMessage(OutboundMessage message,
                                          UUID catalogItemId,
                                          String catalogItemName,
                                          UUID catalogMediaId,
                                          CatalogMedia.Type mediaType) {}

    private record Selection(CatalogItem item, CatalogMedia media) {}
}
