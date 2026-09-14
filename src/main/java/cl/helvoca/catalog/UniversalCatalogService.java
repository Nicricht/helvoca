package cl.helvoca.catalog;

import cl.helvoca.common.ConflictException;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.security.TenantProvider;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class UniversalCatalogService {
    private final CatalogItemRepository repository;
    private final TenantProvider tenantProvider;

    public UniversalCatalogService(CatalogItemRepository repository, TenantProvider tenantProvider) {
        this.repository = repository;
        this.tenantProvider = tenantProvider;
    }

    @Transactional(readOnly = true)
    public List<ItemView> list() {
        UUID businessId = tenantProvider.requireBusinessId();
        return repository.findAllByBusinessIdOrderByNameAsc(businessId).stream().map(ItemView::from).toList();
    }

    @Transactional
    public ItemView create(ItemInput input) {
        UUID businessId = tenantProvider.requireBusinessId();
        validate(input);
        if (repository.existsByBusinessIdAndKindAndNameIgnoreCase(businessId, input.kind(), input.name().trim())) {
            throw new ConflictException("A catalog item with that name and kind already exists");
        }
        CatalogItem item = new CatalogItem();
        item.setBusinessId(businessId);
        apply(item, input);
        return ItemView.from(repository.saveAndFlush(item));
    }

    @Transactional
    public ItemView update(UUID id, ItemInput input) {
        UUID businessId = tenantProvider.requireBusinessId();
        validate(input);
        CatalogItem item = require(id, businessId);
        boolean identityChanged = item.getKind() != input.kind()
                || !item.getName().equalsIgnoreCase(input.name().trim());
        if (identityChanged && repository.existsByBusinessIdAndKindAndNameIgnoreCase(
                businessId, input.kind(), input.name().trim())) {
            throw new ConflictException("A catalog item with that name and kind already exists");
        }
        apply(item, input);
        return ItemView.from(repository.saveAndFlush(item));
    }

    @Transactional
    public void deactivate(UUID id) {
        UUID businessId = tenantProvider.requireBusinessId();
        CatalogItem item = require(id, businessId);
        item.setActive(false);
        repository.save(item);
    }

    private CatalogItem require(UUID id, UUID businessId) {
        return repository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new NotFoundException("Catalog item not found"));
    }

    private static void validate(ItemInput input) {
        if (input == null) throw new IllegalArgumentException("Catalog item is required");
        if (input.kind() == null) throw new IllegalArgumentException("Catalog item kind is required");
        if (input.name() == null || input.name().isBlank()) throw new IllegalArgumentException("Catalog item name is required");
        if (input.price() != null && input.price().signum() < 0) throw new IllegalArgumentException("Price cannot be negative");
        if (input.durationMinutes() != null && input.durationMinutes() <= 0) {
            throw new IllegalArgumentException("Duration must be positive when provided");
        }
        if (input.metadataJson() != null && !input.metadataJson().isBlank()) new JSONObject(input.metadataJson());
    }

    private static void apply(CatalogItem item, ItemInput input) {
        item.setKind(input.kind());
        item.setName(input.name().trim());
        item.setDescription(blankToNull(input.description()));
        item.setPrice(input.price());
        item.setCurrency(normalizeCurrency(input.currency()));
        item.setDurationMinutes(input.durationMinutes());
        item.setMetadataJson(blankToNull(input.metadataJson()));
        if (input.active() != null) item.setActive(input.active());
    }

    private static String normalizeCurrency(String currency) {
        String value = currency == null || currency.isBlank() ? "CLP" : currency.trim().toUpperCase(Locale.ROOT);
        if (!value.matches("^[A-Z]{3}$")) throw new IllegalArgumentException("Currency must be an ISO-4217 style 3-letter code");
        return value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record ItemInput(CatalogItem.Kind kind,
                            String name,
                            String description,
                            BigDecimal price,
                            String currency,
                            Integer durationMinutes,
                            String metadataJson,
                            Boolean active) {}

    public record ItemView(UUID id,
                           CatalogItem.Kind kind,
                           String name,
                           String description,
                           BigDecimal price,
                           String currency,
                           Integer durationMinutes,
                           String metadataJson,
                           boolean active,
                           UUID legacyServiceId) {
        static ItemView from(CatalogItem item) {
            return new ItemView(item.getId(), item.getKind(), item.getName(), item.getDescription(),
                    item.getPrice(), item.getCurrency(), item.getDurationMinutes(), item.getMetadataJson(),
                    item.isActive(), item.getLegacyServiceId());
        }
    }
}
