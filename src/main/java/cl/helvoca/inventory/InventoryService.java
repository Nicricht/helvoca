package cl.helvoca.inventory;

import cl.helvoca.catalog.CatalogItem;
import cl.helvoca.catalog.CatalogItemRepository;
import cl.helvoca.common.ConflictException;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.payment.BusinessPayment;
import cl.helvoca.payment.BusinessPaymentRepository;
import cl.helvoca.security.TenantProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class InventoryService {
    private static final Duration DEFAULT_RESERVATION_TTL = Duration.ofMinutes(15);
    private static final String ORDER_REFERENCE = "ORDER_OPERATION";

    private final InventoryStockRepository stocks;
    private final InventoryReservationRepository reservations;
    private final InventoryMovementRepository movements;
    private final CatalogItemRepository catalog;
    private final TenantProvider tenant;
    private final BusinessPaymentRepository payments;

    @Autowired
    public InventoryService(InventoryStockRepository stocks,
                            InventoryReservationRepository reservations,
                            InventoryMovementRepository movements,
                            CatalogItemRepository catalog,
                            TenantProvider tenant,
                            BusinessPaymentRepository payments) {
        this.stocks = stocks;
        this.reservations = reservations;
        this.movements = movements;
        this.catalog = catalog;
        this.tenant = tenant;
        this.payments = payments;
    }

    InventoryService(InventoryStockRepository stocks,
                     InventoryReservationRepository reservations,
                     InventoryMovementRepository movements,
                     CatalogItemRepository catalog,
                     TenantProvider tenant) {
        this(stocks, reservations, movements, catalog, tenant, null);
    }

    @Transactional(readOnly = true)
    public List<StockView> list() {
        UUID businessId = tenant.requireBusinessId();
        return stocks.findAllByBusinessIdOrderByUpdatedAtDesc(businessId).stream()
                .map(stock -> StockView.from(stock, productName(businessId, stock.getCatalogItemId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public StockView get(UUID catalogItemId) {
        UUID businessId = tenant.requireBusinessId();
        InventoryStock stock = stocks.findByBusinessIdAndCatalogItemId(businessId, catalogItemId)
                .orElseThrow(() -> new NotFoundException("Inventory is not configured for this product"));
        return StockView.from(stock, productName(businessId, catalogItemId));
    }

    @Transactional
    public StockView configure(UUID catalogItemId, ConfigureInput input) {
        UUID businessId = tenant.requireBusinessId();
        CatalogItem product = requireProduct(businessId, catalogItemId);
        if (input == null) throw new IllegalArgumentException("Inventory configuration is required");

        String sku = normalizeSku(input.sku());
        int onHand = nonNegative(input.onHand(), "onHand");
        int reorderThreshold = nonNegative(input.reorderThreshold(), "reorderThreshold");

        if (sku != null) {
            stocks.findByBusinessIdAndSkuIgnoreCase(businessId, sku)
                    .filter(found -> !found.getCatalogItemId().equals(catalogItemId))
                    .ifPresent(found -> {
                        throw new ConflictException("That SKU is already assigned to another product");
                    });
        }

        InventoryStock stock = stocks.lockByBusinessAndCatalogItem(businessId, catalogItemId)
                .orElseGet(() -> {
                    InventoryStock created = new InventoryStock();
                    created.setBusinessId(businessId);
                    created.setCatalogItemId(catalogItemId);
                    return created;
                });

        if (onHand < stock.getReserved()) {
            throw new ConflictException("On-hand stock cannot be lower than currently reserved stock");
        }

        int delta = onHand - stock.getOnHand();
        stock.setSku(sku);
        stock.setTrackingEnabled(Boolean.TRUE.equals(input.trackingEnabled()));
        stock.setOnHand(onHand);
        stock.setReorderThreshold(reorderThreshold);
        stock = stocks.saveAndFlush(stock);

        record(stock, InventoryMovement.Type.CONFIGURE, delta, 0,
                "CONFIGURATION", null, input.note());

        return StockView.from(stock, product.getName());
    }

    @Transactional
    public StockView adjust(UUID catalogItemId, AdjustmentInput input) {
        UUID businessId = tenant.requireBusinessId();
        requireProduct(businessId, catalogItemId);
        if (input == null || input.delta() == 0) {
            throw new IllegalArgumentException("Inventory adjustment delta must be non-zero");
        }

        InventoryStock stock = requireLockedStock(businessId, catalogItemId);
        requireTracking(stock);

        long candidate = (long) stock.getOnHand() + input.delta();
        if (candidate < stock.getReserved()) {
            throw new ConflictException("Adjustment would place on-hand stock below reserved stock");
        }
        if (candidate > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Inventory quantity is too large");
        }

        stock.setOnHand((int) candidate);
        stock = stocks.saveAndFlush(stock);
        record(stock, InventoryMovement.Type.ADJUSTMENT, input.delta(), 0,
                normalizeReferenceType(input.referenceType()), input.referenceId(), input.note());

        return StockView.from(stock, productName(businessId, catalogItemId));
    }

    @Transactional
    public ReservationView reserve(UUID catalogItemId, ReservationInput input) {
        UUID businessId = tenant.requireBusinessId();
        requireProduct(businessId, catalogItemId);
        if (input == null || input.quantity() <= 0) {
            throw new IllegalArgumentException("Reservation quantity must be positive");
        }

        InventoryStock stock = requireLockedStock(businessId, catalogItemId);
        requireTracking(stock);
        if (stock.available() < input.quantity()) {
            throw new ConflictException("Not enough stock is available");
        }

        stock.setReserved(stock.getReserved() + input.quantity());
        stock = stocks.saveAndFlush(stock);

        InventoryReservation reservation = new InventoryReservation();
        reservation.setBusinessId(businessId);
        reservation.setCatalogItemId(catalogItemId);
        reservation.setQuantity(input.quantity());
        reservation.setStatus(InventoryReservation.Status.ACTIVE);
        reservation.setReferenceType(normalizeReferenceType(input.referenceType()));
        reservation.setReferenceId(input.referenceId());
        reservation.setExpiresAt(input.expiresAt() == null
                ? Instant.now().plus(DEFAULT_RESERVATION_TTL)
                : input.expiresAt());
        if (!reservation.getExpiresAt().isAfter(Instant.now())) {
            throw new IllegalArgumentException("Reservation expiry must be in the future");
        }
        reservation = reservations.saveAndFlush(reservation);

        record(stock, InventoryMovement.Type.RESERVATION, 0, input.quantity(),
                reservation.getReferenceType(), reservation.getReferenceId(), input.note());

        return ReservationView.from(reservation, stock);
    }

    @Transactional
    public ReservationView release(UUID reservationId, String note) {
        UUID businessId = tenant.requireBusinessId();
        InventoryReservation reservation = reservations.lockByIdAndBusinessId(reservationId, businessId)
                .orElseThrow(() -> new NotFoundException("Inventory reservation not found"));

        InventoryStock stock = requireLockedStock(businessId, reservation.getCatalogItemId());

        if (reservation.getStatus() == InventoryReservation.Status.RELEASED
                || reservation.getStatus() == InventoryReservation.Status.EXPIRED) {
            return ReservationView.from(reservation, stock);
        }
        if (reservation.getStatus() == InventoryReservation.Status.CONSUMED) {
            throw new ConflictException("Consumed inventory cannot be released");
        }

        stock.setReserved(stock.getReserved() - reservation.getQuantity());
        stock = stocks.saveAndFlush(stock);
        reservation.setStatus(InventoryReservation.Status.RELEASED);
        reservation = reservations.saveAndFlush(reservation);

        record(stock, InventoryMovement.Type.RELEASE, 0, -reservation.getQuantity(),
                reservation.getReferenceType(), reservation.getReferenceId(), note);

        return ReservationView.from(reservation, stock);
    }

    @Transactional
    public ReservationView consume(UUID reservationId, String note) {
        UUID businessId = tenant.requireBusinessId();
        InventoryReservation reservation = reservations.lockByIdAndBusinessId(reservationId, businessId)
                .orElseThrow(() -> new NotFoundException("Inventory reservation not found"));

        InventoryStock stock = requireLockedStock(businessId, reservation.getCatalogItemId());

        if (reservation.getStatus() == InventoryReservation.Status.CONSUMED) {
            return ReservationView.from(reservation, stock);
        }
        if (reservation.getStatus() != InventoryReservation.Status.ACTIVE) {
            throw new ConflictException("Only active inventory reservations can be consumed");
        }

        int quantity = reservation.getQuantity();
        if (stock.getReserved() < quantity || stock.getOnHand() < quantity) {
            throw new ConflictException("Inventory state is inconsistent with the reservation");
        }

        stock.setReserved(stock.getReserved() - quantity);
        stock.setOnHand(stock.getOnHand() - quantity);
        stock = stocks.saveAndFlush(stock);

        reservation.setStatus(InventoryReservation.Status.CONSUMED);
        reservation = reservations.saveAndFlush(reservation);

        record(stock, InventoryMovement.Type.CONSUMPTION, -quantity, -quantity,
                reservation.getReferenceType(), reservation.getReferenceId(), note);

        return ReservationView.from(reservation, stock);
    }

    @Transactional(readOnly = true)
    public List<MovementView> history(UUID catalogItemId) {
        UUID businessId = tenant.requireBusinessId();
        requireProduct(businessId, catalogItemId);
        return movements.findTop100ByBusinessIdAndCatalogItemIdOrderByCreatedAtDesc(businessId, catalogItemId)
                .stream().map(MovementView::from).toList();
    }

    @Transactional(readOnly = true)
    public StockLookupView lookupForBusiness(UUID businessId, UUID catalogItemId, String sku) {
        if (businessId == null) throw new IllegalArgumentException("Business id is required");
        InventoryStock stock = null;
        CatalogItem product = null;

        if (catalogItemId != null) {
            product = requireProduct(businessId, catalogItemId);
            stock = stocks.findByBusinessIdAndCatalogItemId(businessId, catalogItemId).orElse(null);
        } else if (sku != null && !sku.isBlank()) {
            String normalizedSku = normalizeSku(sku);
            stock = stocks.findByBusinessIdAndSkuIgnoreCase(businessId, normalizedSku).orElse(null);
            if (stock != null) product = requireProduct(businessId, stock.getCatalogItemId());
        } else {
            throw new IllegalArgumentException("catalogItemId or sku is required");
        }

        if (stock == null) {
            return new StockLookupView(
                    product == null ? null : product.getId(),
                    product == null ? null : product.getName(),
                    normalizeSku(sku),
                    false,
                    false,
                    null,
                    null,
                    null,
                    null,
                    false);
        }

        boolean known = stock.isTrackingEnabled();
        return new StockLookupView(
                stock.getCatalogItemId(),
                product == null ? productName(businessId, stock.getCatalogItemId()) : product.getName(),
                stock.getSku(),
                true,
                stock.isTrackingEnabled(),
                known ? stock.getOnHand() : null,
                known ? stock.getReserved() : null,
                known ? stock.available() : null,
                stock.getReorderThreshold(),
                known && stock.available() <= stock.getReorderThreshold());
    }

    @Transactional
    public OrderReservationResult reserveOrder(UUID businessId,
                                               UUID orderOperationId,
                                               List<OrderItem> items) {
        if (businessId == null || orderOperationId == null) {
            throw new IllegalArgumentException("Business and order operation are required");
        }
        if (items == null || items.isEmpty()) {
            return new OrderReservationResult(true, null, null, List.of());
        }

        List<InventoryReservation> existing = reservations
                .lockAllByBusinessAndReferenceAndStatus(
                        businessId, ORDER_REFERENCE, orderOperationId, InventoryReservation.Status.ACTIVE);
        if (!existing.isEmpty()) {
            return new OrderReservationResult(
                    true, null, null, existing.stream().map(InventoryReservation::getId).toList());
        }

        Map<UUID, Integer> quantities = new LinkedHashMap<>();
        for (OrderItem item : items) {
            if (item == null || item.catalogItemId() == null || item.quantity() <= 0) {
                throw new IllegalArgumentException("Order inventory items must have a product and positive quantity");
            }
            quantities.merge(item.catalogItemId(), item.quantity(), Math::addExact);
        }

        List<Map.Entry<UUID, Integer>> ordered = new ArrayList<>(quantities.entrySet());
        ordered.sort(Comparator.comparing(entry -> entry.getKey().toString()));

        List<LockedRequest> tracked = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : ordered) {
            InventoryStock stock = stocks.lockByBusinessAndCatalogItem(businessId, entry.getKey()).orElse(null);
            if (stock == null || !stock.isTrackingEnabled()) continue;
            if (stock.available() < entry.getValue()) {
                String name = productName(businessId, entry.getKey());
                return new OrderReservationResult(
                        false,
                        "INSUFFICIENT_STOCK",
                        "No hay stock suficiente para " + name + ". Disponible: " + stock.available() + ".",
                        List.of());
            }
            tracked.add(new LockedRequest(stock, entry.getValue()));
        }

        List<UUID> reservationIds = new ArrayList<>();
        Instant expiresAt = Instant.now().plus(DEFAULT_RESERVATION_TTL);
        for (LockedRequest request : tracked) {
            InventoryStock stock = request.stock();
            stock.setReserved(stock.getReserved() + request.quantity());
            stock = stocks.saveAndFlush(stock);

            InventoryReservation reservation = new InventoryReservation();
            reservation.setBusinessId(businessId);
            reservation.setCatalogItemId(stock.getCatalogItemId());
            reservation.setQuantity(request.quantity());
            reservation.setStatus(InventoryReservation.Status.ACTIVE);
            reservation.setReferenceType(ORDER_REFERENCE);
            reservation.setReferenceId(orderOperationId);
            reservation.setExpiresAt(expiresAt);
            reservation = reservations.saveAndFlush(reservation);
            reservationIds.add(reservation.getId());

            record(stock, InventoryMovement.Type.RESERVATION, 0, request.quantity(),
                    ORDER_REFERENCE, orderOperationId, "Order stock reservation");
        }

        return new OrderReservationResult(true, null, null, List.copyOf(reservationIds));
    }

    @Transactional
    public ExpiryResult expireReservationForBusiness(UUID businessId,
                                                     UUID reservationId,
                                                     Instant now) {
        if (businessId == null || reservationId == null) return ExpiryResult.NOOP;
        Instant cutoff = now == null ? Instant.now() : now;

        InventoryReservation reservation = reservations
                .lockByIdAndBusinessId(reservationId, businessId)
                .orElse(null);
        if (reservation == null
                || reservation.getStatus() != InventoryReservation.Status.ACTIVE
                || reservation.getExpiresAt() == null
                || reservation.getExpiresAt().isAfter(cutoff)) {
            return ExpiryResult.NOOP;
        }

        if (ORDER_REFERENCE.equals(reservation.getReferenceType())
                && reservation.getReferenceId() != null) {
            if (payments == null) return ExpiryResult.PAYMENT_STATE_UNAVAILABLE;

            List<BusinessPayment> related = payments
                    .findAllByBusinessIdAndTargetOperationIdOrderByCreatedAtAsc(
                            businessId, reservation.getReferenceId());

            boolean paid = related.stream()
                    .anyMatch(payment -> payment.getStatus() == BusinessPayment.Status.SUCCEEDED);
            if (paid) {
                settleExpiredReservation(
                        reservation,
                        true,
                        "Recovered paid order while processing expired inventory hold");
                return ExpiryResult.PAID_RECOVERED;
            }

            boolean pending = related.stream().anyMatch(payment ->
                    payment.getStatus() == BusinessPayment.Status.REQUIRES_ACTION
                            || payment.getStatus() == BusinessPayment.Status.PENDING);
            if (pending) return ExpiryResult.PAYMENT_PENDING;
        }

        settleExpiredReservation(reservation, false, "Inventory reservation expired");
        return ExpiryResult.EXPIRED;
    }

    private void settleExpiredReservation(InventoryReservation reservation,
                                          boolean consume,
                                          String note) {
        InventoryStock stock = requireLockedStock(
                reservation.getBusinessId(), reservation.getCatalogItemId());
        int quantity = reservation.getQuantity();
        if (stock.getReserved() < quantity) {
            throw new ConflictException("Inventory state is inconsistent with the expiring reservation");
        }

        if (consume) {
            if (stock.getOnHand() < quantity) {
                throw new ConflictException("Inventory state is inconsistent with the paid reservation");
            }
            stock.setOnHand(stock.getOnHand() - quantity);
            stock.setReserved(stock.getReserved() - quantity);
            reservation.setStatus(InventoryReservation.Status.CONSUMED);
            stocks.saveAndFlush(stock);
            reservations.saveAndFlush(reservation);
            record(stock, InventoryMovement.Type.CONSUMPTION, -quantity, -quantity,
                    reservation.getReferenceType(), reservation.getReferenceId(), note);
            return;
        }

        stock.setReserved(stock.getReserved() - quantity);
        reservation.setStatus(InventoryReservation.Status.EXPIRED);
        stocks.saveAndFlush(stock);
        reservations.saveAndFlush(reservation);
        record(stock, InventoryMovement.Type.RELEASE, 0, -quantity,
                reservation.getReferenceType(), reservation.getReferenceId(), note);
    }

    @Transactional
    public void consumeOrder(UUID businessId, UUID orderOperationId, String note) {
        settleOrder(businessId, orderOperationId, true, note);
    }

    @Transactional
    public void releaseOrder(UUID businessId, UUID orderOperationId, String note) {
        settleOrder(businessId, orderOperationId, false, note);
    }

    private void settleOrder(UUID businessId, UUID orderOperationId, boolean consume, String note) {
        if (businessId == null || orderOperationId == null) return;
        List<InventoryReservation> active = reservations
                .findAllByBusinessIdAndReferenceTypeAndReferenceIdAndStatusOrderByCreatedAtAsc(
                        businessId, ORDER_REFERENCE, orderOperationId, InventoryReservation.Status.ACTIVE);
        for (InventoryReservation candidate : active) {
            InventoryReservation reservation = reservations
                    .lockByIdAndBusinessId(candidate.getId(), businessId)
                    .orElse(null);
            if (reservation == null || reservation.getStatus() != InventoryReservation.Status.ACTIVE) continue;

            InventoryStock stock = requireLockedStock(businessId, reservation.getCatalogItemId());
            int quantity = reservation.getQuantity();

            if (consume) {
                if (stock.getReserved() < quantity || stock.getOnHand() < quantity) {
                    throw new ConflictException("Inventory state is inconsistent with the order reservation");
                }
                stock.setReserved(stock.getReserved() - quantity);
                stock.setOnHand(stock.getOnHand() - quantity);
                reservation.setStatus(InventoryReservation.Status.CONSUMED);
                recordAfterSave(stock, InventoryMovement.Type.CONSUMPTION, -quantity, -quantity,
                        ORDER_REFERENCE, orderOperationId, note);
            } else {
                if (stock.getReserved() < quantity) {
                    throw new ConflictException("Inventory state is inconsistent with the order reservation");
                }
                stock.setReserved(stock.getReserved() - quantity);
                reservation.setStatus(InventoryReservation.Status.RELEASED);
                recordAfterSave(stock, InventoryMovement.Type.RELEASE, 0, -quantity,
                        ORDER_REFERENCE, orderOperationId, note);
            }

            stocks.saveAndFlush(stock);
            reservations.saveAndFlush(reservation);
        }
    }

    private void recordAfterSave(InventoryStock stock,
                                 InventoryMovement.Type type,
                                 int quantityDelta,
                                 int reservedDelta,
                                 String referenceType,
                                 UUID referenceId,
                                 String note) {
        InventoryMovement movement = new InventoryMovement();
        movement.setBusinessId(stock.getBusinessId());
        movement.setCatalogItemId(stock.getCatalogItemId());
        movement.setType(type);
        movement.setQuantityDelta(quantityDelta);
        movement.setReservedDelta(reservedDelta);
        movement.setOnHandAfter(stock.getOnHand());
        movement.setReservedAfter(stock.getReserved());
        movement.setReferenceType(referenceType);
        movement.setReferenceId(referenceId);
        movement.setNote(blankToNull(note));
        movements.save(movement);
    }

    private InventoryStock requireLockedStock(UUID businessId, UUID catalogItemId) {
        return stocks.lockByBusinessAndCatalogItem(businessId, catalogItemId)
                .orElseThrow(() -> new NotFoundException("Inventory is not configured for this product"));
    }

    private CatalogItem requireProduct(UUID businessId, UUID catalogItemId) {
        CatalogItem item = catalog.findByIdAndBusinessId(catalogItemId, businessId)
                .filter(CatalogItem::isActive)
                .orElseThrow(() -> new NotFoundException("Catalog product not found"));
        if (item.getKind() != CatalogItem.Kind.PRODUCT) {
            throw new ConflictException("Inventory can only be configured for products");
        }
        return item;
    }

    private String productName(UUID businessId, UUID catalogItemId) {
        return catalog.findByIdAndBusinessId(catalogItemId, businessId)
                .map(CatalogItem::getName)
                .orElse("Unknown product");
    }

    private static void requireTracking(InventoryStock stock) {
        if (!stock.isTrackingEnabled()) {
            throw new ConflictException("Inventory tracking is disabled for this product");
        }
    }

    private void record(InventoryStock stock,
                        InventoryMovement.Type type,
                        int quantityDelta,
                        int reservedDelta,
                        String referenceType,
                        UUID referenceId,
                        String note) {
        InventoryMovement movement = new InventoryMovement();
        movement.setBusinessId(stock.getBusinessId());
        movement.setCatalogItemId(stock.getCatalogItemId());
        movement.setType(type);
        movement.setQuantityDelta(quantityDelta);
        movement.setReservedDelta(reservedDelta);
        movement.setOnHandAfter(stock.getOnHand());
        movement.setReservedAfter(stock.getReserved());
        movement.setReferenceType(referenceType);
        movement.setReferenceId(referenceId);
        movement.setNote(blankToNull(note));
        movements.save(movement);
    }

    private static int nonNegative(Integer value, String field) {
        int normalized = value == null ? 0 : value;
        if (normalized < 0) throw new IllegalArgumentException(field + " cannot be negative");
        return normalized;
    }

    private static String normalizeSku(String sku) {
        if (sku == null || sku.isBlank()) return null;
        String value = sku.trim().toUpperCase(Locale.ROOT);
        if (!value.matches("^[A-Z0-9._/-]{1,80}$")) {
            throw new IllegalArgumentException("SKU may contain letters, numbers, dot, underscore, slash and dash");
        }
        return value;
    }

    private static String normalizeReferenceType(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("^[A-Z0-9_]{1,40}$")) {
            throw new IllegalArgumentException("referenceType is invalid");
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record ConfigureInput(String sku,
                                 Boolean trackingEnabled,
                                 Integer onHand,
                                 Integer reorderThreshold,
                                 String note) {}

    public record AdjustmentInput(int delta,
                                  String referenceType,
                                  UUID referenceId,
                                  String note) {}

    public record ReservationInput(int quantity,
                                   String referenceType,
                                   UUID referenceId,
                                   Instant expiresAt,
                                   String note) {}

    public record StockView(UUID catalogItemId,
                            String productName,
                            String sku,
                            boolean trackingEnabled,
                            int onHand,
                            int reserved,
                            int available,
                            int reorderThreshold,
                            boolean lowStock) {
        static StockView from(InventoryStock stock, String productName) {
            return new StockView(
                    stock.getCatalogItemId(),
                    productName,
                    stock.getSku(),
                    stock.isTrackingEnabled(),
                    stock.getOnHand(),
                    stock.getReserved(),
                    stock.available(),
                    stock.getReorderThreshold(),
                    stock.isTrackingEnabled() && stock.available() <= stock.getReorderThreshold()
            );
        }
    }

    public record ReservationView(UUID id,
                                  UUID catalogItemId,
                                  int quantity,
                                  InventoryReservation.Status status,
                                  Instant expiresAt,
                                  String referenceType,
                                  UUID referenceId,
                                  StockView stock) {
        static ReservationView from(InventoryReservation reservation, InventoryStock stock) {
            return new ReservationView(
                    reservation.getId(),
                    reservation.getCatalogItemId(),
                    reservation.getQuantity(),
                    reservation.getStatus(),
                    reservation.getExpiresAt(),
                    reservation.getReferenceType(),
                    reservation.getReferenceId(),
                    StockView.from(stock, null)
            );
        }
    }

    public record StockLookupView(UUID catalogItemId,
                                  String productName,
                                  String sku,
                                  boolean configured,
                                  boolean trackingEnabled,
                                  Integer onHand,
                                  Integer reserved,
                                  Integer available,
                                  Integer reorderThreshold,
                                  boolean lowStock) {}

    public record OrderItem(UUID catalogItemId, int quantity) {}

    public record OrderReservationResult(boolean success,
                                         String code,
                                         String message,
                                         List<UUID> reservationIds) {}

    public enum ExpiryResult {
        EXPIRED,
        PAYMENT_PENDING,
        PAID_RECOVERED,
        PAYMENT_STATE_UNAVAILABLE,
        NOOP
    }

    private record LockedRequest(InventoryStock stock, int quantity) {}

    public record MovementView(UUID id,
                               InventoryMovement.Type type,
                               int quantityDelta,
                               int reservedDelta,
                               int onHandAfter,
                               int reservedAfter,
                               String referenceType,
                               UUID referenceId,
                               String note,
                               Instant createdAt) {
        static MovementView from(InventoryMovement movement) {
            return new MovementView(
                    movement.getId(),
                    movement.getType(),
                    movement.getQuantityDelta(),
                    movement.getReservedDelta(),
                    movement.getOnHandAfter(),
                    movement.getReservedAfter(),
                    movement.getReferenceType(),
                    movement.getReferenceId(),
                    movement.getNote(),
                    movement.getCreatedAt()
            );
        }
    }
}
