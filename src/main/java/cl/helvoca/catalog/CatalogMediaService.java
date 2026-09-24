package cl.helvoca.catalog;

import cl.helvoca.audit.AuditService;
import cl.helvoca.common.ConflictException;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.security.TenantProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class CatalogMediaService {
    private final CatalogMediaRepository media;
    private final CatalogItemRepository catalog;
    private final TenantProvider tenantProvider;
    private final AuditService audit;

    public CatalogMediaService(CatalogMediaRepository media,
                               CatalogItemRepository catalog,
                               TenantProvider tenantProvider,
                               AuditService audit) {
        this.media = media;
        this.catalog = catalog;
        this.tenantProvider = tenantProvider;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<MediaView> list(UUID catalogItemId) {
        UUID businessId = tenantProvider.requireBusinessId();
        requireCatalogItem(businessId, catalogItemId);
        return media.findAllByBusinessIdAndCatalogItemIdOrderBySortOrderAscCreatedAtAsc(businessId, catalogItemId)
                .stream().map(MediaView::from).toList();
    }

    @Transactional(readOnly = true)
    public List<CatalogMedia> activeForItem(UUID businessId, UUID catalogItemId) {
        requireCatalogItem(businessId, catalogItemId);
        return media.findAllByBusinessIdAndCatalogItemIdAndActiveTrueOrderBySortOrderAscCreatedAtAsc(
                businessId, catalogItemId);
    }

    @Transactional(readOnly = true)
    public CatalogMedia requireActive(UUID businessId, UUID mediaId) {
        CatalogMedia found = media.findByIdAndBusinessId(mediaId, businessId)
                .orElseThrow(() -> new NotFoundException("Catalog media not found"));
        if (!found.isActive()) throw new ConflictException("Catalog media is inactive");
        CatalogItem item = requireCatalogItem(businessId, found.getCatalogItemId());
        if (!item.isActive()) throw new ConflictException("Catalog item is inactive");
        return found;
    }

    @Transactional
    public MediaView create(UUID catalogItemId, MediaInput input) {
        UUID businessId = tenantProvider.requireBusinessId();
        requireCatalogItem(businessId, catalogItemId);
        Validated value = validate(input);
        if (media.existsByBusinessIdAndCatalogItemIdAndMediaUrl(businessId, catalogItemId, value.url())) {
            throw new ConflictException("That media URL is already attached to this catalog item");
        }

        CatalogMedia item = new CatalogMedia();
        item.setBusinessId(businessId);
        item.setCatalogItemId(catalogItemId);
        apply(item, input, value);
        CatalogMedia saved = media.saveAndFlush(item);
        audit.humanSuccess(
                businessId,
                "CATALOG_MEDIA_CREATE",
                "CATALOG_MEDIA",
                saved.getId(),
                null,
                snapshot(saved));
        return MediaView.from(saved);
    }

    @Transactional
    public MediaView update(UUID catalogItemId, UUID mediaId, MediaInput input) {
        UUID businessId = tenantProvider.requireBusinessId();
        requireCatalogItem(businessId, catalogItemId);
        CatalogMedia existing = requireForItem(businessId, catalogItemId, mediaId);
        Validated value = validate(input);
        if (!existing.getMediaUrl().equals(value.url())
                && media.existsByBusinessIdAndCatalogItemIdAndMediaUrl(businessId, catalogItemId, value.url())) {
            throw new ConflictException("That media URL is already attached to this catalog item");
        }

        Map<String, Object> before = snapshot(existing);
        apply(existing, input, value);
        CatalogMedia saved = media.saveAndFlush(existing);
        audit.humanSuccess(
                businessId,
                "CATALOG_MEDIA_UPDATE",
                "CATALOG_MEDIA",
                saved.getId(),
                before,
                snapshot(saved));
        return MediaView.from(saved);
    }

    @Transactional
    public void deactivate(UUID catalogItemId, UUID mediaId) {
        UUID businessId = tenantProvider.requireBusinessId();
        requireCatalogItem(businessId, catalogItemId);
        CatalogMedia existing = requireForItem(businessId, catalogItemId, mediaId);
        if (!existing.isActive()) return;
        Map<String, Object> before = snapshot(existing);
        existing.setActive(false);
        CatalogMedia saved = media.saveAndFlush(existing);
        audit.humanSuccess(
                businessId,
                "CATALOG_MEDIA_DEACTIVATE",
                "CATALOG_MEDIA",
                saved.getId(),
                before,
                snapshot(saved));
    }

    CatalogItem requireCatalogItem(UUID businessId, UUID catalogItemId) {
        if (businessId == null || catalogItemId == null) {
            throw new IllegalArgumentException("businessId and catalogItemId are required");
        }
        return catalog.findByIdAndBusinessId(catalogItemId, businessId)
                .orElseThrow(() -> new NotFoundException("Catalog item not found"));
    }

    private CatalogMedia requireForItem(UUID businessId, UUID catalogItemId, UUID mediaId) {
        CatalogMedia found = media.findByIdAndBusinessId(mediaId, businessId)
                .orElseThrow(() -> new NotFoundException("Catalog media not found"));
        if (!catalogItemId.equals(found.getCatalogItemId())) {
            throw new NotFoundException("Catalog media not found for item");
        }
        return found;
    }

    private static Validated validate(MediaInput input) {
        if (input == null || input.mediaType() == null) {
            throw new IllegalArgumentException("Media type is required");
        }
        String url = secureUrl(input.mediaUrl());
        String mime = normalizeMime(input.mimeType(), input.mediaType());
        String caption = trimToNull(input.caption());
        if (caption != null && caption.length() > 1024) {
            throw new IllegalArgumentException("Media caption cannot exceed 1024 characters");
        }
        int order = input.sortOrder() == null ? 0 : input.sortOrder();
        if (order < 0) throw new IllegalArgumentException("Media sort order cannot be negative");
        return new Validated(url, mime, caption, order);
    }

    private static String secureUrl(String raw) {
        try {
            String value = raw == null ? "" : raw.trim();
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getHost().isBlank()
                    || uri.getUserInfo() != null) {
                throw new IllegalArgumentException("Media URL must be a public HTTPS URL");
            }
            return uri.toString();
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("Media URL must be a public HTTPS URL");
        }
    }

    private static String normalizeMime(String raw, CatalogMedia.Type type) {
        String mime = trimToNull(raw);
        if (mime == null) return null;
        mime = mime.toLowerCase(Locale.ROOT);
        boolean valid = switch (type) {
            case IMAGE -> mime.startsWith("image/");
            case VIDEO -> mime.startsWith("video/");
            case DOCUMENT -> !mime.startsWith("image/") && !mime.startsWith("video/");
        };
        if (!valid) throw new IllegalArgumentException("MIME type does not match catalog media type");
        return mime;
    }

    private static void apply(CatalogMedia target, MediaInput input, Validated value) {
        target.setMediaType(input.mediaType());
        target.setMediaUrl(value.url());
        target.setMimeType(value.mime());
        target.setCaption(value.caption());
        target.setSortOrder(value.sortOrder());
        if (input.active() != null) target.setActive(input.active());
    }

    private static Map<String, Object> snapshot(CatalogMedia value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("catalogItemId", value.getCatalogItemId());
        result.put("mediaType", value.getMediaType() == null ? null : value.getMediaType().name());
        result.put("mediaUrl", value.getMediaUrl());
        result.put("mimeType", value.getMimeType());
        result.put("sortOrder", value.getSortOrder());
        result.put("active", value.isActive());
        return result;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record Validated(String url, String mime, String caption, int sortOrder) {}

    public record MediaInput(CatalogMedia.Type mediaType,
                             String mediaUrl,
                             String mimeType,
                             String caption,
                             Integer sortOrder,
                             Boolean active) {}

    public record MediaView(UUID id,
                            UUID catalogItemId,
                            CatalogMedia.Type mediaType,
                            String mediaUrl,
                            String mimeType,
                            String caption,
                            int sortOrder,
                            boolean active) {
        static MediaView from(CatalogMedia value) {
            return new MediaView(
                    value.getId(),
                    value.getCatalogItemId(),
                    value.getMediaType(),
                    value.getMediaUrl(),
                    value.getMimeType(),
                    value.getCaption(),
                    value.getSortOrder(),
                    value.isActive());
        }
    }
}
