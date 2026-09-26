package cl.helvoca.inventory;

import cl.helvoca.catalog.CatalogItem;
import cl.helvoca.catalog.CatalogItemRepository;
import cl.helvoca.common.ConflictException;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.security.TenantProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class InventoryService {
    private static final Duration DEFAULT_RESERVATION_TTL = Duration.ofMinutes(15);

    private final InventoryStockRepository stocks;
    private final InventoryReservationRepository reservations;
    private final InventoryMovementRepository movements;
    private final CatalogItemRepository catalog;
    private final TenantProvider tenant;

    public InventoryService(InventoryStockRepository stocks,
                            InventoryReservationRepository reservations,
                            InventoryMovementRepository movements,
                            CatalogItemRepository catalog,
                            TenantProvider tenant) {
        this.stocks = stocks;
        this.reservations = reservations;
        this.movements = movements;
        this.catalog = catalog;
        this.tenant = tenant;
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
