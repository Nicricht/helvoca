package cl.helvoca.inventory;

import cl.helvoca.catalog.CatalogItemRepository;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.security.TenantProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class InventoryAlertService {
    private final InventoryAlertRepository alerts;
    private final CatalogItemRepository catalog;
    private final TenantProvider tenant;
    private final JdbcTemplate jdbc;
    private final InventoryRestockSubscriptionService restockSubscriptions;

    public InventoryAlertService(InventoryAlertRepository alerts,
                                 CatalogItemRepository catalog,
                                 TenantProvider tenant,
                                 JdbcTemplate jdbc,
                                 InventoryRestockSubscriptionService restockSubscriptions) {
        this.alerts = alerts;
        this.catalog = catalog;
        this.tenant = tenant;
        this.jdbc = jdbc;
        this.restockSubscriptions = restockSubscriptions;
    }

    @Transactional(readOnly = true)
    public List<AlertView> listOpen() {
        UUID businessId = tenant.requireBusinessId();
        return alerts.findTop100ByBusinessIdAndStatusOrderByCreatedAtDesc(
                        businessId, InventoryAlert.Status.OPEN)
                .stream()
                .map(AlertView::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AlertView> history() {
        UUID businessId = tenant.requireBusinessId();
        return alerts.findTop100ByBusinessIdOrderByCreatedAtDesc(businessId)
                .stream()
                .map(AlertView::from)
                .toList();
    }

    @Transactional
    public AlertView acknowledge(UUID alertId) {
        UUID businessId = tenant.requireBusinessId();
        InventoryAlert alert = alerts.findByIdAndBusinessId(alertId, businessId)
                .orElseThrow(() -> new NotFoundException("Inventory alert not found"));
        if (alert.getAcknowledgedAt() == null) {
            alert.setAcknowledgedAt(Instant.now());
            alert = alerts.saveAndFlush(alert);
        }
        return AlertView.from(alert);
    }

    @Transactional
    public void evaluateBase(InventoryStock stock) {
        if (stock == null || stock.getBusinessId() == null || stock.getCatalogItemId() == null) return;
        evaluate(
                stock.getBusinessId(),
                stock.getCatalogItemId(),
                null,
                stock.isTrackingEnabled(),
                true,
                stock.available(),
                stock.getReorderThreshold(),
                productName(stock.getBusinessId(), stock.getCatalogItemId()),
                stock.getSku());
    }

    @Transactional
    public void evaluateVariant(InventoryProductVariant variant) {
        if (variant == null || variant.getBusinessId() == null
                || variant.getCatalogItemId() == null || variant.getId() == null) return;
        String product = productName(variant.getBusinessId(), variant.getCatalogItemId());
        evaluate(
                variant.getBusinessId(),
                variant.getCatalogItemId(),
                variant.getId(),
                variant.isTrackingEnabled(),
                variant.isActive(),
                variant.available(),
                variant.getReorderThreshold(),
                product + " · " + variant.getName(),
                variant.getSku());
    }

    private void evaluate(UUID businessId,
                          UUID catalogItemId,
                          UUID variantId,
                          boolean trackingEnabled,
                          boolean active,
                          int available,
                          int threshold,
                          String subjectName,
                          String sku) {
        advisoryLock(businessId, catalogItemId, variantId);

        InventoryAlert current = variantId == null
                ? alerts.lockOpenBase(businessId, catalogItemId).orElse(null)
                : alerts.lockOpenVariant(businessId, catalogItemId, variantId).orElse(null);

        if (!trackingEnabled || !active) {
            resolve(current);
            return;
        }

        InventoryAlert.Type desired = available <= 0
                ? InventoryAlert.Type.OUT_OF_STOCK
                : available <= threshold
                    ? InventoryAlert.Type.LOW_STOCK
                    : null;

        if (desired == null) {
            if (current == null || current.getType() == InventoryAlert.Type.RESTOCKED) return;
            resolve(current);
            create(
                    businessId, catalogItemId, variantId,
                    InventoryAlert.Type.RESTOCKED,
                    subjectName, sku, available, threshold);
            restockSubscriptions.onRestocked(
                    businessId, catalogItemId, variantId, subjectName, sku, available);
            return;
        }

        if (current != null && current.getType() == desired) {
            if (current.getAvailable() != available
                    || current.getReorderThreshold() != threshold
                    || !same(current.getSubjectName(), subjectName)
                    || !same(current.getSku(), sku)) {
                current.setAvailable(available);
                current.setReorderThreshold(threshold);
                current.setSubjectName(subjectName);
                current.setSku(sku);
                alerts.saveAndFlush(current);
            }
            return;
        }

        resolve(current);
        create(
                businessId, catalogItemId, variantId,
                desired, subjectName, sku, available, threshold);
    }

    private void create(UUID businessId,
                        UUID catalogItemId,
                        UUID variantId,
                        InventoryAlert.Type type,
                        String subjectName,
                        String sku,
                        int available,
                        int threshold) {
        InventoryAlert alert = new InventoryAlert();
        alert.setBusinessId(businessId);
        alert.setCatalogItemId(catalogItemId);
        alert.setVariantId(variantId);
        alert.setType(type);
        alert.setStatus(InventoryAlert.Status.OPEN);
        alert.setSubjectName(subjectName);
        alert.setSku(sku);
        alert.setAvailable(Math.max(0, available));
        alert.setReorderThreshold(Math.max(0, threshold));
        alerts.saveAndFlush(alert);
    }

    private void resolve(InventoryAlert current) {
        if (current == null || current.getStatus() != InventoryAlert.Status.OPEN) return;
        current.setStatus(InventoryAlert.Status.RESOLVED);
        current.setResolvedAt(Instant.now());
        alerts.saveAndFlush(current);
    }

    private String productName(UUID businessId, UUID catalogItemId) {
        return catalog.findByIdAndBusinessId(catalogItemId, businessId)
                .map(value -> value.getName())
                .orElse("Producto");
    }

    private void advisoryLock(UUID businessId, UUID catalogItemId, UUID variantId) {
        String key = catalogItemId + ":" + (variantId == null ? "BASE" : variantId);
        jdbc.execute("SELECT pg_advisory_xact_lock("
                + businessId.hashCode() + "," + key.hashCode() + ")");
    }

    private static boolean same(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    public record AlertView(UUID id,
                            UUID catalogItemId,
                            UUID variantId,
                            InventoryAlert.Type type,
                            String subjectName,
                            String sku,
                            int available,
                            int reorderThreshold,
                            boolean acknowledged,
                            Instant acknowledgedAt,
                            Instant createdAt) {
        static AlertView from(InventoryAlert alert) {
            return new AlertView(
                    alert.getId(),
                    alert.getCatalogItemId(),
                    alert.getVariantId(),
                    alert.getType(),
                    alert.getSubjectName(),
                    alert.getSku(),
                    alert.getAvailable(),
                    alert.getReorderThreshold(),
                    alert.getAcknowledgedAt() != null,
                    alert.getAcknowledgedAt(),
                    alert.getCreatedAt());
        }
    }
}
