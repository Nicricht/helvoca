package cl.helvoca.operations;

import cl.helvoca.customer.Customer;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.inventory.InventoryProductVariantRepository;
import cl.helvoca.inventory.InventoryReservation;
import cl.helvoca.inventory.InventoryReservationRepository;
import cl.helvoca.messaging.outbound.OutboundMessage;
import cl.helvoca.messaging.outbound.OutboundMessageRepository;
import cl.helvoca.payment.BusinessPayment;
import cl.helvoca.payment.BusinessPaymentRepository;
import cl.helvoca.security.TenantProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class CommercialPipelineService {
    private final BusinessOperationRepository operations;
    private final BusinessOperationItemRepository items;
    private final BusinessOrderRepository orders;
    private final BusinessPaymentRepository payments;
    private final InventoryReservationRepository reservations;
    private final InventoryProductVariantRepository variants;
    private final OutboundMessageRepository outbound;
    private final CustomerRepository customers;
    private final TenantProvider tenant;

    public CommercialPipelineService(
            BusinessOperationRepository operations,
            BusinessOperationItemRepository items,
            BusinessOrderRepository orders,
            BusinessPaymentRepository payments,
            InventoryReservationRepository reservations,
            InventoryProductVariantRepository variants,
            OutboundMessageRepository outbound,
            CustomerRepository customers,
            TenantProvider tenant) {
        this.operations = operations;
        this.items = items;
        this.orders = orders;
        this.payments = payments;
        this.reservations = reservations;
        this.variants = variants;
        this.outbound = outbound;
        this.customers = customers;
        this.tenant = tenant;
    }

    @Transactional(readOnly = true)
    public PipelineResponse get() {
        UUID businessId = tenant.requireBusinessId();
        List<PipelineItem> rows = operations
                .findTop100ByBusinessIdAndTypeOrderByUpdatedAtDesc(businessId, BusinessOperation.Type.REQUEST)
                .stream()
                .filter(this::isCommercialJourney)
                .map(operation -> toItem(businessId, operation))
                .toList();

        long paid = rows.stream().filter(row -> "PAID".equals(row.commercialStage())).count();
        long needsAction = rows.stream().filter(row ->
                "REQUIRES_ACTION".equals(row.paymentStatus())
                        || "FAILED".equals(row.paymentStatus())
                        || "PAYMENT_FAILED".equals(row.commercialStage())).count();
        long active = rows.size() - paid;

        return new PipelineResponse(rows.size(), active, paid, needsAction, rows);
    }

    private boolean isCommercialJourney(BusinessOperation operation) {
        Map<String, Object> metadata = operation.getMetadata();
        return metadata != null && (metadata.containsKey("commercialStage")
                || metadata.containsKey("selectedCatalogItemId")
                || metadata.containsKey("orderOperationId")
                || metadata.containsKey("paymentOperationId"));
    }

    private PipelineItem toItem(UUID businessId, BusinessOperation journey) {
        Map<String, Object> metadata = journey.getMetadata() == null ? Map.of() : journey.getMetadata();
        UUID customerId = journey.getCustomerId();
        Customer customer = customerId == null ? null
                : customers.findByIdAndBusinessId(customerId, businessId).orElse(null);

        List<BusinessOperationItem> journeyItems =
                items.findAllByOperationIdOrderByCreatedAtAsc(journey.getId());
        BusinessOperationItem selected = journeyItems.isEmpty() ? null : journeyItems.get(0);

        UUID variantId = uuid(metadata.get("selectedVariantId"));
        if (variantId == null && selected != null) variantId = selected.getVariantId();
        String variantName = variantId == null ? null
                : variants.findByIdAndBusinessId(variantId, businessId)
                        .map(value -> value.getName())
                        .orElse(null);

        UUID orderOperationId = uuid(metadata.get("orderOperationId"));
        BusinessOrder order = orderOperationId == null ? null
                : orders.findByOperationIdAndBusinessId(orderOperationId, businessId).orElse(null);

        UUID paymentOperationId = uuid(metadata.get("paymentOperationId"));
        BusinessPayment payment = paymentOperationId == null ? null
                : payments.findByOperationIdAndBusinessId(paymentOperationId, businessId).orElse(null);

        InventoryReservation latestInventory = orderOperationId == null ? null
                : reservations.findAllByBusinessIdAndReferenceTypeAndReferenceIdOrderByCreatedAtAsc(
                                businessId, "ORDER_OPERATION", orderOperationId)
                        .stream()
                        .max(Comparator.comparing(
                                InventoryReservation::getUpdatedAt,
                                Comparator.nullsFirst(Comparator.naturalOrder())))
                        .orElse(null);

        OutboundMessage latestOutbound = outbound
                .findTop50ByBusinessIdAndCustomerIdOrderByCreatedAtDesc(businessId, customerId)
                .stream()
                .filter(message -> message.getOperationId() != null
                        && (message.getOperationId().equals(journey.getId())
                        || message.getOperationId().equals(paymentOperationId)
                        || message.getOperationId().equals(orderOperationId)))
                .findFirst()
                .orElse(null);

        String channel = string(metadata.get("handoffChannel"));
        if (channel == null) channel = journey.getSource() == null ? null : journey.getSource().name();

        return new PipelineItem(
                journey.getId(),
                customerId,
                customer == null ? journey.getContactName() : customer.getName(),
                customer == null ? journey.getContactPhone() : customer.getPhone(),
                string(metadata.get("commercialStage")),
                channel,
                selected == null ? null : selected.getItemName(),
                variantId,
                variantName,
                selected == null ? null : selected.getQuantity(),
                order != null ? order.getTotal() : journey.getTotal(),
                order != null ? order.getCurrency() : journey.getCurrency(),
                order == null || order.getStatus() == null ? null : order.getStatus().name(),
                payment == null || payment.getStatus() == null ? string(metadata.get("paymentStatus")) : payment.getStatus().name(),
                latestInventory == null || latestInventory.getStatus() == null ? null : latestInventory.getStatus().name(),
                latestOutbound == null || latestOutbound.getStatus() == null ? null : latestOutbound.getStatus().name(),
                journey.getUpdatedAt());
    }

    private static UUID uuid(Object raw) {
        if (raw == null) return null;
        try { return UUID.fromString(String.valueOf(raw)); }
        catch (Exception ignored) { return null; }
    }

    private static String string(Object raw) {
        if (raw == null) return null;
        String value = String.valueOf(raw);
        return value.isBlank() ? null : value;
    }

    public record PipelineResponse(
            long total,
            long active,
            long paid,
            long needsAction,
            List<PipelineItem> items) {}

    public record PipelineItem(
            UUID journeyId,
            UUID customerId,
            String customerName,
            String customerPhone,
            String commercialStage,
            String channel,
            String product,
            UUID variantId,
            String variant,
            Integer quantity,
            BigDecimal total,
            String currency,
            String orderStatus,
            String paymentStatus,
            String inventoryStatus,
            String outboundStatus,
            Instant updatedAt) {}
}
