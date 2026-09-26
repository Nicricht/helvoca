package cl.helvoca.inventory;

import cl.helvoca.catalog.CatalogItem;
import cl.helvoca.catalog.CatalogItemRepository;
import cl.helvoca.common.ConflictException;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.security.TenantProvider;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class InventoryVariantService {
    private final InventoryProductVariantRepository variants;
    private final InventoryStockRepository baseStocks;
    private final InventoryMovementRepository movements;
    private final CatalogItemRepository catalog;
    private final TenantProvider tenant;

    public InventoryVariantService(InventoryProductVariantRepository variants,
                                   InventoryStockRepository baseStocks,
                                   InventoryMovementRepository movements,
                                   CatalogItemRepository catalog,
                                   TenantProvider tenant) {
        this.variants = variants;
        this.baseStocks = baseStocks;
        this.movements = movements;
        this.catalog = catalog;
        this.tenant = tenant;
    }

    @Transactional(readOnly = true)
    public List<VariantView> list(UUID catalogItemId) {
        UUID businessId = tenant.requireBusinessId();
        requireProduct(businessId, catalogItemId);
        return variants.findAllByBusinessIdAndCatalogItemIdOrderByNameAsc(businessId, catalogItemId)
                .stream().map(VariantView::from).toList();
    }

    @Transactional
    public VariantView create(UUID catalogItemId, VariantInput input) {
        UUID businessId = tenant.requireBusinessId();
        requireProduct(businessId, catalogItemId);
        validate(input);

        String name = input.name().trim();
        String sku = normalizeSku(input.sku());
        if (variants.existsByBusinessIdAndCatalogItemIdAndNameIgnoreCase(
                businessId, catalogItemId, name)) {
            throw new ConflictException("That product variant already exists");
        }
        requireSkuAvailable(businessId, sku, null);

        InventoryProductVariant variant = new InventoryProductVariant();
        variant.setBusinessId(businessId);
        variant.setCatalogItemId(catalogItemId);
        apply(variant, input, sku);
        variant = variants.saveAndFlush(variant);

        record(variant, InventoryMovement.Type.CONFIGURE, variant.getOnHand(), 0,
                "VARIANT_CREATE", null, input.note());
        return VariantView.from(variant);
    }

    @Transactional
    public VariantView update(UUID catalogItemId, UUID variantId, VariantInput input) {
        UUID businessId = tenant.requireBusinessId();
        requireProduct(businessId, catalogItemId);
        validate(input);

        InventoryProductVariant variant = requireLockedVariant(businessId, catalogItemId, variantId);
        String sku = normalizeSku(input.sku());
        requireSkuAvailable(businessId, sku, variantId);

        if (input.onHand() < variant.getReserved()) {
            throw new ConflictException("On-hand stock cannot be lower than reserved variant stock");
        }

        int delta = input.onHand() - variant.getOnHand();
        apply(variant, input, sku);
        variant = variants.saveAndFlush(variant);

        record(variant, InventoryMovement.Type.CONFIGURE, delta, 0,
                "VARIANT_UPDATE", null, input.note());
        return VariantView.from(variant);
    }

    @Transactional
    public VariantView adjust(UUID catalogItemId, UUID variantId, AdjustmentInput input) {
        UUID businessId = tenant.requireBusinessId();
        requireProduct(businessId, catalogItemId);
        if (input == null || input.delta() == 0) {
            throw new IllegalArgumentException("Variant adjustment delta must be non-zero");
        }

        InventoryProductVariant variant = requireLockedVariant(businessId, catalogItemId, variantId);
        if (!variant.isTrackingEnabled()) {
            throw new ConflictException("Inventory tracking is disabled for this variant");
        }

        long candidate = (long) variant.getOnHand() + input.delta();
        if (candidate < variant.getReserved()) {
            throw new ConflictException("Adjustment would place variant stock below reserved stock");
        }
        if (candidate > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Inventory quantity is too large");
        }

        variant.setOnHand((int) candidate);
        variant = variants.saveAndFlush(variant);
        record(variant, InventoryMovement.Type.ADJUSTMENT, input.delta(), 0,
                "VARIANT_MANUAL", null, input.note());
        return VariantView.from(variant);
    }

    @Transactional
    public VariantView deactivate(UUID catalogItemId, UUID variantId) {
        UUID businessId = tenant.requireBusinessId();
        requireProduct(businessId, catalogItemId);
        InventoryProductVariant variant = requireLockedVariant(businessId, catalogItemId, variantId);
        if (variant.getReserved() > 0) {
            throw new ConflictException("A variant with reserved stock cannot be deactivated");
        }
        variant.setActive(false);
        variant = variants.saveAndFlush(variant);
        record(variant, InventoryMovement.Type.CONFIGURE, 0, 0,
                "VARIANT_DEACTIVATE", null, "Variant deactivated");
        return VariantView.from(variant);
    }

    @Transactional(readOnly = true)
    public List<VariantMovementView> history(UUID catalogItemId, UUID variantId) {
        UUID businessId = tenant.requireBusinessId();
        requireProduct(businessId, catalogItemId);
        InventoryProductVariant variant = variants.findByIdAndBusinessId(variantId, businessId)
                .filter(value -> value.getCatalogItemId().equals(catalogItemId))
                .orElseThrow(() -> new NotFoundException("Inventory variant not found"));
        return movements.findTop100ByBusinessIdAndVariantIdOrderByCreatedAtDesc(
                        businessId, variant.getId())
                .stream().map(VariantMovementView::from).toList();
    }

    private InventoryProductVariant requireLockedVariant(
            UUID businessId, UUID catalogItemId, UUID variantId) {
        return variants.lockByIdAndBusinessId(variantId, businessId)
                .filter(value -> value.getCatalogItemId().equals(catalogItemId))
                .orElseThrow(() -> new NotFoundException("Inventory variant not found"));
    }

    private CatalogItem requireProduct(UUID businessId, UUID catalogItemId) {
        CatalogItem item = catalog.findByIdAndBusinessId(catalogItemId, businessId)
                .filter(CatalogItem::isActive)
                .orElseThrow(() -> new NotFoundException("Catalog product not found"));
        if (item.getKind() != CatalogItem.Kind.PRODUCT) {
            throw new ConflictException("Variants can only be configured for products");
        }
        return item;
    }

    private void requireSkuAvailable(UUID businessId, String sku, UUID currentVariantId) {
        baseStocks.findByBusinessIdAndSkuIgnoreCase(businessId, sku).ifPresent(value -> {
            throw new ConflictException("That SKU is already assigned to base product inventory");
        });
        variants.findByBusinessIdAndSkuIgnoreCase(businessId, sku)
                .filter(value -> currentVariantId == null || !value.getId().equals(currentVariantId))
                .ifPresent(value -> {
                    throw new ConflictException("That SKU is already assigned to another variant");
                });
    }

    private static void validate(VariantInput input) {
        if (input == null) throw new IllegalArgumentException("Variant is required");
        if (input.name() == null || input.name().isBlank()) {
            throw new IllegalArgumentException("Variant name is required");
        }
        if (input.name().trim().length() > 150) {
            throw new IllegalArgumentException("Variant name is too long");
        }
        if (input.onHand() < 0 || input.reorderThreshold() < 0) {
            throw new IllegalArgumentException("Variant stock values cannot be negative");
        }
        normalizeOptions(input.optionValuesJson());
    }

    private static void apply(InventoryProductVariant variant, VariantInput input, String sku) {
        variant.setName(input.name().trim());
        variant.setSku(sku);
        variant.setOptionValuesJson(normalizeOptions(input.optionValuesJson()));
        variant.setTrackingEnabled(input.trackingEnabled());
        variant.setOnHand(input.onHand());
        variant.setReorderThreshold(input.reorderThreshold());
        variant.setActive(input.active());
    }

    private void record(InventoryProductVariant variant,
                        InventoryMovement.Type type,
                        int quantityDelta,
                        int reservedDelta,
                        String referenceType,
                        UUID referenceId,
                        String note) {
        InventoryMovement movement = new InventoryMovement();
        movement.setBusinessId(variant.getBusinessId());
        movement.setCatalogItemId(variant.getCatalogItemId());
        movement.setVariantId(variant.getId());
        movement.setType(type);
        movement.setQuantityDelta(quantityDelta);
        movement.setReservedDelta(reservedDelta);
        movement.setOnHandAfter(variant.getOnHand());
        movement.setReservedAfter(variant.getReserved());
        movement.setReferenceType(referenceType);
        movement.setReferenceId(referenceId);
        movement.setNote(blankToNull(note));
        movements.save(movement);
    }

    private static String normalizeSku(String sku) {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("Variant SKU is required");
        }
        String value = sku.trim().toUpperCase(Locale.ROOT);
        if (!value.matches("^[A-Z0-9._/-]{1,80}$")) {
            throw new IllegalArgumentException(
                    "SKU may contain letters, numbers, dot, underscore, slash and dash");
        }
        return value;
    }

    private static String normalizeOptions(String json) {
        if (json == null || json.isBlank()) return "{}";
        JSONObject value = new JSONObject(json);
        return value.toString();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record VariantInput(String name,
                               String optionValuesJson,
                               String sku,
                               boolean trackingEnabled,
                               int onHand,
                               int reorderThreshold,
                               boolean active,
                               String note) {}

    public record AdjustmentInput(int delta, String note) {}

    public record VariantView(UUID id,
                              UUID catalogItemId,
                              String name,
                              String optionValuesJson,
                              String sku,
                              boolean trackingEnabled,
                              int onHand,
                              int reserved,
                              int available,
                              int reorderThreshold,
                              boolean lowStock,
                              boolean active) {
        static VariantView from(InventoryProductVariant variant) {
            return new VariantView(
                    variant.getId(),
                    variant.getCatalogItemId(),
                    variant.getName(),
                    variant.getOptionValuesJson(),
                    variant.getSku(),
                    variant.isTrackingEnabled(),
                    variant.getOnHand(),
                    variant.getReserved(),
                    variant.available(),
                    variant.getReorderThreshold(),
                    variant.isTrackingEnabled()
                            && variant.available() <= variant.getReorderThreshold(),
                    variant.isActive());
        }
    }

    public record VariantMovementView(UUID id,
                                      InventoryMovement.Type type,
                                      int quantityDelta,
                                      int reservedDelta,
                                      int onHandAfter,
                                      int reservedAfter,
                                      String note,
                                      Instant createdAt) {
        static VariantMovementView from(InventoryMovement movement) {
            return new VariantMovementView(
                    movement.getId(),
                    movement.getType(),
                    movement.getQuantityDelta(),
                    movement.getReservedDelta(),
                    movement.getOnHandAfter(),
                    movement.getReservedAfter(),
                    movement.getNote(),
                    movement.getCreatedAt());
        }
    }
}
