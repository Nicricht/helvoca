package cl.helvoca.inventory;

import cl.helvoca.catalog.CatalogItem;
import cl.helvoca.catalog.CatalogItemRepository;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.omnichannel.CustomerIdentityService;
import cl.helvoca.security.TenantProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class InventoryRestockSubscriptionService {
    private final InventoryRestockSubscriptionRepository subscriptions;
    private final InventoryRestockNotificationRepository notifications;
    private final CatalogItemRepository catalog;
    private final InventoryProductVariantRepository variants;
    private final InventoryStockRepository stocks;
    private final CustomerRepository customers;
    private final TenantProvider tenant;
    private final JdbcTemplate jdbc;

    public InventoryRestockSubscriptionService(
            InventoryRestockSubscriptionRepository subscriptions,
            InventoryRestockNotificationRepository notifications,
            CatalogItemRepository catalog,
            InventoryProductVariantRepository variants,
            InventoryStockRepository stocks,
            CustomerRepository customers,
            TenantProvider tenant,
            JdbcTemplate jdbc) {
        this.subscriptions = subscriptions;
        this.notifications = notifications;
        this.catalog = catalog;
        this.variants = variants;
        this.stocks = stocks;
        this.customers = customers;
        this.tenant = tenant;
        this.jdbc = jdbc;
    }

    @Transactional
    public SubscriptionView subscribe(SubscribeRequest request) {
        return subscribeForBusiness(tenant.requireBusinessId(), request);
    }

    @Transactional
    public SubscriptionView subscribeForBusiness(UUID businessId, SubscribeRequest request) {
        if (businessId == null) throw new IllegalArgumentException("businessId is required");
        if (request == null) throw new IllegalArgumentException("Subscription request is required");
        if (!Boolean.TRUE.equals(request.consent())) {
            throw new IllegalArgumentException("Explicit consent is required for restock notifications");
        }
        if (request.catalogItemId() == null) {
            throw new IllegalArgumentException("catalogItemId is required");
        }
        if (request.preferredChannel() == null) {
            throw new IllegalArgumentException("preferredChannel is required");
        }

        advisorySubjectLock(businessId, request.catalogItemId(), request.variantId());

        CatalogItem product = catalog.findByIdAndBusinessId(request.catalogItemId(), businessId)
                .orElseThrow(() -> new NotFoundException("Catalog product not found"));
        if (product.getKind() != CatalogItem.Kind.PRODUCT || !product.isActive()) {
            throw new IllegalArgumentException("Restock subscriptions require an active product");
        }

        if (request.variantId() != null) {
            InventoryProductVariant variant = variants.findByIdAndBusinessId(request.variantId(), businessId)
                    .orElseThrow(() -> new NotFoundException("Inventory variant not found"));
            if (!request.catalogItemId().equals(variant.getCatalogItemId()) || !variant.isActive()) {
                throw new IllegalArgumentException("Variant does not belong to the active product");
            }
            if (!variant.isTrackingEnabled()) {
                throw new IllegalArgumentException("Variant stock is not authoritative");
            }
            if (variant.available() > 0) {
                throw new IllegalArgumentException("Variant is already available");
            }
        } else {
            InventoryStock stock = stocks.findByBusinessIdAndCatalogItemId(
                            businessId, request.catalogItemId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Inventory tracking is not configured for this product"));
            if (!stock.isTrackingEnabled()) {
                throw new IllegalArgumentException("Product stock is not authoritative");
            }
            if (stock.available() > 0) {
                throw new IllegalArgumentException("Product is already available");
            }
        }

        if (request.customerId() != null
                && customers.findByIdAndBusinessId(request.customerId(), businessId).isEmpty()) {
            throw new NotFoundException("Customer not found");
        }

        String normalizedContact = normalizeContact(request.preferredChannel(), request.contact());
        String displayContact = request.contact() == null ? normalizedContact : request.contact().trim();
        String consentSource = normalizeConsentSource(request.consentSource());

        InventoryRestockSubscription existing = subscriptions.lockActiveDuplicate(
                        businessId,
                        request.catalogItemId(),
                        request.variantId(),
                        request.preferredChannel(),
                        normalizedContact)
                .orElse(null);
        if (existing != null) {
            return SubscriptionView.from(existing);
        }

        InventoryRestockSubscription subscription = new InventoryRestockSubscription();
        subscription.setBusinessId(businessId);
        subscription.setCustomerId(request.customerId());
        subscription.setCatalogItemId(request.catalogItemId());
        subscription.setVariantId(request.variantId());
        subscription.setPreferredChannel(request.preferredChannel());
        subscription.setContact(displayContact);
        subscription.setNormalizedContact(normalizedContact);
        subscription.setConsentGranted(true);
        subscription.setConsentGrantedAt(Instant.now());
        subscription.setConsentSource(consentSource);
        subscription.setStatus(InventoryRestockSubscription.Status.ACTIVE);

        return SubscriptionView.from(subscriptions.saveAndFlush(subscription));
    }

    @Transactional(readOnly = true)
    public List<SubscriptionView> list(InventoryRestockSubscription.Status status) {
        UUID businessId = tenant.requireBusinessId();
        InventoryRestockSubscription.Status safeStatus =
                status == null ? InventoryRestockSubscription.Status.ACTIVE : status;
        return subscriptions.findTop100ByBusinessIdAndStatusOrderByCreatedAtDesc(businessId, safeStatus)
                .stream()
                .map(SubscriptionView::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NotificationView> pendingNotifications() {
        UUID businessId = tenant.requireBusinessId();
        return notifications.findTop100ByBusinessIdAndStatusOrderByCreatedAtDesc(
                        businessId, InventoryRestockNotification.Status.PENDING)
                .stream()
                .map(NotificationView::from)
                .toList();
    }

    @Transactional
    public SubscriptionView cancel(UUID subscriptionId) {
        UUID businessId = tenant.requireBusinessId();
        InventoryRestockSubscription subscription = subscriptions
                .lockByIdAndBusinessId(subscriptionId, businessId)
                .orElseThrow(() -> new NotFoundException("Restock subscription not found"));

        if (subscription.getStatus() == InventoryRestockSubscription.Status.CANCELLED) {
            return SubscriptionView.from(subscription);
        }

        Instant now = Instant.now();
        notifications.findByBusinessIdAndSubscriptionId(businessId, subscriptionId)
                .filter(value -> value.getStatus() == InventoryRestockNotification.Status.PENDING)
                .ifPresent(value -> {
                    value.setStatus(InventoryRestockNotification.Status.CANCELLED);
                    value.setCancelledAt(now);
                    notifications.saveAndFlush(value);
                });

        subscription.setStatus(InventoryRestockSubscription.Status.CANCELLED);
        subscription.setCancelledAt(now);
        return SubscriptionView.from(subscriptions.saveAndFlush(subscription));
    }

    @Transactional
    public int onRestocked(UUID businessId,
                           UUID catalogItemId,
                           UUID variantId,
                           String subjectName,
                           String sku,
                           int available) {
        if (businessId == null || catalogItemId == null || available <= 0) return 0;

        advisorySubjectLock(businessId, catalogItemId, variantId);
        List<InventoryRestockSubscription> active =
                subscriptions.lockActiveForSubject(businessId, catalogItemId, variantId);
        if (active.isEmpty()) return 0;

        Instant now = Instant.now();
        int queued = 0;
        for (InventoryRestockSubscription subscription : active) {
            if (subscription.getStatus() != InventoryRestockSubscription.Status.ACTIVE) continue;

            InventoryRestockNotification notification =
                    notifications.findByBusinessIdAndSubscriptionId(businessId, subscription.getId())
                            .orElse(null);
            if (notification == null) {
                notification = new InventoryRestockNotification();
                notification.setBusinessId(businessId);
                notification.setSubscriptionId(subscription.getId());
                notification.setCustomerId(subscription.getCustomerId());
                notification.setCatalogItemId(catalogItemId);
                notification.setVariantId(variantId);
                notification.setPreferredChannel(subscription.getPreferredChannel());
                notification.setContact(subscription.getNormalizedContact());
                notification.setSubjectName(
                        subjectName == null || subjectName.isBlank() ? "Producto" : subjectName.trim());
                notification.setSku(sku == null || sku.isBlank() ? null : sku.trim());
                notification.setAvailable(available);
                notification.setStatus(InventoryRestockNotification.Status.PENDING);
                notification.setIdempotencyKey("inventory-restock:" + subscription.getId());
                notifications.saveAndFlush(notification);
                queued++;
            }

            subscription.setStatus(InventoryRestockSubscription.Status.NOTIFIED);
            subscription.setNotifiedAt(now);
            subscriptions.saveAndFlush(subscription);
        }
        return queued;
    }

    private void advisorySubjectLock(UUID businessId, UUID catalogItemId, UUID variantId) {
        String key = "RESTOCK:"
                + catalogItemId
                + ":" + (variantId == null ? "BASE" : variantId);
        jdbc.execute("SELECT pg_advisory_xact_lock("
                + businessId.hashCode() + "," + key.hashCode() + ")");
    }

    private static String normalizeContact(
            InventoryRestockSubscription.PreferredChannel channel,
            String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("contact is required");
        }
        if (channel == InventoryRestockSubscription.PreferredChannel.EMAIL) {
            String email = value.trim().toLowerCase(Locale.ROOT);
            if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") || email.length() > 180) {
                throw new IllegalArgumentException("Email contact is invalid");
            }
            return email;
        }

        String phone = CustomerIdentityService.normalizePhone(value);
        if (phone == null) {
            throw new IllegalArgumentException("Phone contact must use international format");
        }
        return phone;
    }

    private static String normalizeConsentSource(String value) {
        if (value == null || value.isBlank()) return "UNKNOWN";
        String normalized = value.trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9_:-]", "_");
        return normalized.length() <= 40 ? normalized : normalized.substring(0, 40);
    }

    public record SubscribeRequest(
            UUID customerId,
            UUID catalogItemId,
            UUID variantId,
            InventoryRestockSubscription.PreferredChannel preferredChannel,
            String contact,
            Boolean consent,
            String consentSource) {}

    public record SubscriptionView(
            UUID id,
            UUID customerId,
            UUID catalogItemId,
            UUID variantId,
            InventoryRestockSubscription.PreferredChannel preferredChannel,
            String contact,
            boolean consentGranted,
            Instant consentGrantedAt,
            String consentSource,
            InventoryRestockSubscription.Status status,
            Instant notifiedAt,
            Instant cancelledAt,
            Instant createdAt) {
        static SubscriptionView from(InventoryRestockSubscription value) {
            return new SubscriptionView(
                    value.getId(),
                    value.getCustomerId(),
                    value.getCatalogItemId(),
                    value.getVariantId(),
                    value.getPreferredChannel(),
                    value.getContact(),
                    value.isConsentGranted(),
                    value.getConsentGrantedAt(),
                    value.getConsentSource(),
                    value.getStatus(),
                    value.getNotifiedAt(),
                    value.getCancelledAt(),
                    value.getCreatedAt());
        }
    }

    public record NotificationView(
            UUID id,
            UUID subscriptionId,
            UUID customerId,
            UUID catalogItemId,
            UUID variantId,
            InventoryRestockSubscription.PreferredChannel preferredChannel,
            String contact,
            String subjectName,
            String sku,
            int available,
            InventoryRestockNotification.Status status,
            String idempotencyKey,
            Instant createdAt) {
        static NotificationView from(InventoryRestockNotification value) {
            return new NotificationView(
                    value.getId(),
                    value.getSubscriptionId(),
                    value.getCustomerId(),
                    value.getCatalogItemId(),
                    value.getVariantId(),
                    value.getPreferredChannel(),
                    value.getContact(),
                    value.getSubjectName(),
                    value.getSku(),
                    value.getAvailable(),
                    value.getStatus(),
                    value.getIdempotencyKey(),
                    value.getCreatedAt());
        }
    }
}
