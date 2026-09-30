package cl.helvoca.onboarding;

import cl.helvoca.catalog.CatalogItem;
import cl.helvoca.catalog.UniversalCatalogService;
import cl.helvoca.common.ConflictException;
import cl.helvoca.inventory.InventoryService;
import cl.helvoca.security.TenantProvider;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class BusinessImportApplyService {
    private static final int MAX_PRODUCTS = 500;

    private final UniversalCatalogService catalog;
    private final InventoryService inventory;
    private final TenantProvider tenantProvider;

    public BusinessImportApplyService(UniversalCatalogService catalog,
                                      InventoryService inventory,
                                      TenantProvider tenantProvider) {
        this.catalog = catalog;
        this.inventory = inventory;
        this.tenantProvider = tenantProvider;
    }

    @Transactional
    public ApplyResult apply(ApplyRequest request) {
        tenantProvider.requireBusinessId();
        if (request == null || request.products() == null || request.products().isEmpty()) {
            throw new IllegalArgumentException("At least one product must be selected");
        }
        if (request.products().size() > MAX_PRODUCTS) {
            throw new IllegalArgumentException("A maximum of 500 products can be applied at once");
        }
        validateRequest(request.products());

        List<UniversalCatalogService.ItemView> currentCatalog = catalog.list().stream()
                .filter(item -> item.kind() == CatalogItem.Kind.PRODUCT)
                .toList();
        List<InventoryService.StockView> currentStock = inventory.list();

        Map<UUID, UniversalCatalogService.ItemView> catalogById = new HashMap<>();
        Map<String, UniversalCatalogService.ItemView> catalogByName = new HashMap<>();
        for (UniversalCatalogService.ItemView item : currentCatalog) {
            catalogById.put(item.id(), item);
            catalogByName.put(normalizeName(item.name()), item);
        }

        Map<UUID, InventoryService.StockView> stockByItem = new HashMap<>();
        Map<String, InventoryService.StockView> stockBySku = new HashMap<>();
        for (InventoryService.StockView stock : currentStock) {
            stockByItem.put(stock.catalogItemId(), stock);
            if (stock.sku() != null && !stock.sku().isBlank()) {
                stockBySku.put(normalizeSku(stock.sku()), stock);
            }
        }

        int created = 0;
        int updated = 0;
        int inventoryConfigured = 0;
        List<ItemResult> results = new ArrayList<>();

        for (ProductInput input : request.products()) {
            String normalizedName = normalizeName(input.name());
            String normalizedSku = input.sku() == null || input.sku().isBlank()
                    ? null : normalizeSku(input.sku());

            UniversalCatalogService.ItemView byName = catalogByName.get(normalizedName);
            InventoryService.StockView skuStock = normalizedSku == null ? null : stockBySku.get(normalizedSku);
            UniversalCatalogService.ItemView bySku = skuStock == null ? null : catalogById.get(skuStock.catalogItemId());

            if (bySku != null && byName != null && !bySku.id().equals(byName.id())) {
                throw new ConflictException("The imported SKU and product name refer to different existing products");
            }

            UniversalCatalogService.ItemView existing = bySku != null ? bySku : byName;
            UniversalCatalogService.ItemView saved;
            boolean wasCreated;

            if (existing == null) {
                saved = catalog.create(new UniversalCatalogService.ItemInput(
                        CatalogItem.Kind.PRODUCT,
                        input.name().trim(),
                        blankToNull(input.description()),
                        input.price(),
                        normalizeCurrency(input.currency(), "CLP"),
                        null,
                        mergeMetadata(null, input.category(), input.sourceName()),
                        true));
                created++;
                wasCreated = true;
                catalogById.put(saved.id(), saved);
                catalogByName.put(normalizeName(saved.name()), saved);
            } else {
                String oldNameKey = normalizeName(existing.name());
                saved = catalog.update(existing.id(), new UniversalCatalogService.ItemInput(
                        CatalogItem.Kind.PRODUCT,
                        input.name().trim(),
                        input.description() == null ? existing.description() : blankToNull(input.description()),
                        input.price() == null ? existing.price() : input.price(),
                        normalizeCurrency(input.currency(), existing.currency()),
                        existing.durationMinutes(),
                        mergeMetadata(existing.metadataJson(), input.category(), input.sourceName()),
                        true));
                updated++;
                wasCreated = false;
                if (!oldNameKey.equals(normalizedName)) catalogByName.remove(oldNameKey);
                catalogById.put(saved.id(), saved);
                catalogByName.put(normalizedName, saved);
            }

            InventoryService.StockView existingStock = stockByItem.get(saved.id());
            boolean configured = configureInventoryIfNeeded(saved.id(), input, existingStock);
            if (configured) {
                inventoryConfigured++;
                String effectiveSku = normalizedSku != null
                        ? normalizedSku
                        : existingStock == null || existingStock.sku() == null
                        ? null : normalizeSku(existingStock.sku());
                int effectiveOnHand = input.onHand() != null
                        ? input.onHand()
                        : existingStock == null ? 0 : existingStock.onHand();
                int threshold = existingStock == null ? 0 : existingStock.reorderThreshold();
                boolean tracking = input.onHand() != null
                        || existingStock != null && existingStock.trackingEnabled();
                InventoryService.StockView projected = new InventoryService.StockView(
                        saved.id(), saved.name(), effectiveSku, tracking, effectiveOnHand,
                        existingStock == null ? 0 : existingStock.reserved(),
                        effectiveOnHand - (existingStock == null ? 0 : existingStock.reserved()),
                        threshold,
                        tracking && effectiveOnHand - (existingStock == null ? 0 : existingStock.reserved()) <= threshold);
                stockByItem.put(saved.id(), projected);
                if (effectiveSku != null) stockBySku.put(effectiveSku, projected);
            }

            results.add(new ItemResult(saved.id(), saved.name(), wasCreated ? "CREATED" : "UPDATED", configured));
        }

        return new ApplyResult(created, updated, inventoryConfigured, List.copyOf(results));
    }

    private boolean configureInventoryIfNeeded(UUID catalogItemId,
                                               ProductInput input,
                                               InventoryService.StockView existing) {
        String importedSku = input.sku() == null || input.sku().isBlank() ? null : input.sku().trim();
        if (input.onHand() != null) {
            inventory.configure(catalogItemId, new InventoryService.ConfigureInput(
                    importedSku != null ? importedSku : existing == null ? null : existing.sku(),
                    true,
                    input.onHand(),
                    existing == null ? 0 : existing.reorderThreshold(),
                    "Imported from " + safeSource(input.sourceName())));
            return true;
        }

        if (importedSku == null) return false;
        if (existing == null) {
            inventory.configure(catalogItemId, new InventoryService.ConfigureInput(
                    importedSku,
                    false,
                    0,
                    0,
                    "SKU imported from " + safeSource(input.sourceName()) + "; stock not supplied"));
            return true;
        }

        if (!normalizeSku(importedSku).equals(normalizeSku(existing.sku()))) {
            inventory.configure(catalogItemId, new InventoryService.ConfigureInput(
                    importedSku,
                    existing.trackingEnabled(),
                    existing.onHand(),
                    existing.reorderThreshold(),
                    "SKU updated from " + safeSource(input.sourceName())));
            return true;
        }
        return false;
    }

    private static void validateRequest(List<ProductInput> products) {
        Set<String> names = new HashSet<>();
        Set<String> skus = new HashSet<>();
        for (ProductInput input : products) {
            if (input == null) throw new IllegalArgumentException("Imported product cannot be null");
            if (input.name() == null || input.name().isBlank()) {
                throw new IllegalArgumentException("Every imported product requires a name");
            }
            if (input.name().trim().length() > 150) {
                throw new IllegalArgumentException("Product name is too long");
            }
            if (input.price() != null && input.price().signum() < 0) {
                throw new IllegalArgumentException("Product price cannot be negative");
            }
            if (input.onHand() != null && input.onHand() < 0) {
                throw new IllegalArgumentException("Imported stock cannot be negative");
            }

            String nameKey = normalizeName(input.name());
            if (!names.add(nameKey)) {
                throw new ConflictException("The import contains duplicate product names");
            }
            if (input.sku() != null && !input.sku().isBlank()) {
                String skuKey = normalizeSku(input.sku());
                if (!skus.add(skuKey)) {
                    throw new ConflictException("The import contains duplicate SKUs");
                }
            }
        }
    }

    private static String mergeMetadata(String existingJson, String category, String sourceName) {
        JSONObject metadata;
        try {
            metadata = existingJson == null || existingJson.isBlank()
                    ? new JSONObject()
                    : new JSONObject(existingJson);
        } catch (Exception ignored) {
            metadata = new JSONObject();
        }
        if (category != null && !category.isBlank()) {
            metadata.put("importCategory", truncate(category.trim(), 120));
        }
        if (sourceName != null && !sourceName.isBlank()) {
            metadata.put("importSource", truncate(sourceName.trim(), 180));
        }
        metadata.put("importMethod", "BUSINESS_IMPORT");
        return metadata.toString();
    }

    private static String normalizeCurrency(String value, String fallback) {
        String currency = value == null || value.isBlank() ? fallback : value.trim();
        if (currency == null || currency.isBlank()) currency = "CLP";
        currency = currency.toUpperCase(Locale.ROOT);
        if (!currency.matches("^[A-Z]{3}$")) {
            throw new IllegalArgumentException("Currency must contain three letters");
        }
        return currency;
    }

    private static String normalizeName(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeSku(String value) {
        if (value == null || value.isBlank()) return "";
        String sku = value.trim().toUpperCase(Locale.ROOT);
        if (!sku.matches("^[A-Z0-9._/-]{1,80}$")) {
            throw new IllegalArgumentException("SKU may contain letters, numbers, dot, underscore, slash and dash");
        }
        return sku;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String safeSource(String value) {
        return value == null || value.isBlank() ? "business import" : truncate(value.trim(), 120);
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    public record ApplyRequest(List<ProductInput> products) {}

    public record ProductInput(
            String name,
            String description,
            BigDecimal price,
            String currency,
            String sku,
            Integer onHand,
            String category,
            String sourceName
    ) {}

    public record ItemResult(UUID catalogItemId, String name, String action, boolean inventoryConfigured) {}

    public record ApplyResult(int created,
                              int updated,
                              int inventoryConfigured,
                              List<ItemResult> items) {}
}
