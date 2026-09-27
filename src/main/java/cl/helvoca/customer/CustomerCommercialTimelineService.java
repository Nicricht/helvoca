package cl.helvoca.customer;

import cl.helvoca.call.CallSession;
import cl.helvoca.call.CallSessionRepository;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.inventory.InventoryProductVariantRepository;
import cl.helvoca.inventory.InventoryReservation;
import cl.helvoca.inventory.InventoryReservationRepository;
import cl.helvoca.messaging.MessagingConversation;
import cl.helvoca.messaging.MessagingConversationRepository;
import cl.helvoca.messaging.outbound.OutboundMessage;
import cl.helvoca.messaging.outbound.OutboundMessageRepository;
import cl.helvoca.operations.BusinessOperation;
import cl.helvoca.operations.BusinessOperationItem;
import cl.helvoca.operations.BusinessOperationItemRepository;
import cl.helvoca.operations.BusinessOperationRepository;
import cl.helvoca.operations.BusinessOrder;
import cl.helvoca.operations.BusinessOrderLine;
import cl.helvoca.operations.BusinessOrderLineRepository;
import cl.helvoca.operations.BusinessOrderRepository;
import cl.helvoca.payment.BusinessPayment;
import cl.helvoca.payment.BusinessPaymentRepository;
import cl.helvoca.security.TenantProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CustomerCommercialTimelineService {
    private final CustomerRepository customers;
    private final TenantProvider tenant;
    private final CallSessionRepository calls;
    private final MessagingConversationRepository conversations;
    private final BusinessOperationRepository operations;
    private final BusinessOperationItemRepository operationItems;
    private final BusinessOrderRepository orders;
    private final BusinessOrderLineRepository orderLines;
    private final BusinessPaymentRepository payments;
    private final OutboundMessageRepository outbound;
    private final InventoryReservationRepository reservations;
    private final InventoryProductVariantRepository variants;

    public CustomerCommercialTimelineService(
            CustomerRepository customers,
            TenantProvider tenant,
            CallSessionRepository calls,
            MessagingConversationRepository conversations,
            BusinessOperationRepository operations,
            BusinessOperationItemRepository operationItems,
            BusinessOrderRepository orders,
            BusinessOrderLineRepository orderLines,
            BusinessPaymentRepository payments,
            OutboundMessageRepository outbound,
            InventoryReservationRepository reservations,
            InventoryProductVariantRepository variants) {
        this.customers = customers;
        this.tenant = tenant;
        this.calls = calls;
        this.conversations = conversations;
        this.operations = operations;
        this.operationItems = operationItems;
        this.orders = orders;
        this.orderLines = orderLines;
        this.payments = payments;
        this.outbound = outbound;
        this.reservations = reservations;
        this.variants = variants;
    }

    @Transactional(readOnly = true)
    public TimelineResponse get(UUID customerId) {
        UUID businessId = tenant.requireBusinessId();
        customers.findByIdAndBusinessId(customerId, businessId)
                .orElseThrow(() -> new NotFoundException("Customer not found"));

        List<CallSession> callRows =
                calls.findTop20ByBusinessIdAndCustomerIdAndCertificationFalseOrderByStartedAtDesc(
                        businessId, customerId);
        List<MessagingConversation> conversationRows =
                conversations.findTop20ByBusinessIdAndCustomerIdOrderByLastMessageAtDesc(
                        businessId, customerId);
        List<BusinessOperation> operationRows =
                operations.findTop50ByBusinessIdAndCustomerIdOrderByUpdatedAtDesc(
                        businessId, customerId);
        List<BusinessOrder> orderRows =
                orders.findTop5ByBusinessIdAndCustomerIdOrderByCreatedAtDesc(businessId, customerId);
        List<BusinessPayment> paymentRows =
                payments.findTop5ByBusinessIdAndCustomerIdOrderByCreatedAtDesc(businessId, customerId);
        List<OutboundMessage> outboundRows =
                outbound.findTop50ByBusinessIdAndCustomerIdOrderByCreatedAtDesc(businessId, customerId);

        String commercialStage = null;
        String selectedProduct = null;
        UUID selectedVariantId = null;

        for (BusinessOperation operation : operationRows) {
            Map<String, Object> metadata = operation.getMetadata();
            if (commercialStage == null && metadata != null && metadata.get("commercialStage") != null) {
                commercialStage = String.valueOf(metadata.get("commercialStage"));
            }
            if (selectedVariantId == null && metadata != null && metadata.get("selectedVariantId") != null) {
                selectedVariantId = uuidOrNull(metadata.get("selectedVariantId"));
            }

            List<BusinessOperationItem> items =
                    operationItems.findAllByOperationIdOrderByCreatedAtAsc(operation.getId());
            if (selectedProduct == null && !items.isEmpty()) {
                selectedProduct = items.get(0).getItemName();
            }
            if (selectedVariantId == null && !items.isEmpty()) {
                selectedVariantId = items.get(0).getVariantId();
            }
            if (commercialStage != null && selectedProduct != null && selectedVariantId != null) break;
        }

        String selectedVariant = selectedVariantId == null ? null
                : variants.findByIdAndBusinessId(selectedVariantId, businessId)
                        .map(value -> value.getName())
                        .orElse(null);

        BusinessOrder latestOrder = orderRows.isEmpty() ? null : orderRows.get(0);
        BusinessPayment latestPayment = paymentRows.isEmpty() ? null : paymentRows.get(0);

        List<InventoryReservation> inventoryRows = new ArrayList<>();
        for (BusinessOrder order : orderRows) {
            inventoryRows.addAll(reservations
                    .findAllByBusinessIdAndReferenceTypeAndReferenceIdOrderByCreatedAtAsc(
                            businessId, "ORDER_OPERATION", order.getOperationId()));
        }
        inventoryRows.sort(Comparator.comparing(
                InventoryReservation::getUpdatedAt,
                Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        InventoryReservation latestInventory = inventoryRows.isEmpty() ? null : inventoryRows.get(0);

        List<TimelineEvent> events = new ArrayList<>();
        String lastChannel = null;
        Instant lastChannelAt = null;

        for (CallSession call : callRows) {
            Instant at = call.getStartedAt();
            add(events, new TimelineEvent(
                    at, "CALL", "VOICE", "Llamada",
                    text(call.getResolution(), "Atención telefónica"),
                    enumName(call.getStatus()), null));
            if (newer(at, lastChannelAt)) {
                lastChannel = "VOICE";
                lastChannelAt = at;
            }
        }

        for (MessagingConversation conversation : conversationRows) {
            Instant at = conversation.getLastMessageAt();
            String channel = text(conversation.getChannel(), "WHATSAPP").toUpperCase();
            add(events, new TimelineEvent(
                    at, "WHATSAPP", channel, "Conversación WhatsApp",
                    "Continuó la atención por WhatsApp", "ACTIVE", null));
            if (newer(at, lastChannelAt)) {
                lastChannel = channel;
                lastChannelAt = at;
            }
        }

        for (BusinessOperation operation : operationRows) {
            Map<String, Object> metadata = operation.getMetadata();
            String stage = metadata == null || metadata.get("commercialStage") == null
                    ? enumName(operation.getStatus())
                    : String.valueOf(metadata.get("commercialStage"));
            List<BusinessOperationItem> items =
                    operationItems.findAllByOperationIdOrderByCreatedAtAsc(operation.getId());
            String detail = items.isEmpty() ? null : items.get(0).getItemName();
            String channel = source(operation.getSource());
            add(events, new TimelineEvent(
                    operation.getUpdatedAt(), "COMMERCIAL", channel,
                    operationTitle(operation.getType()), detail, stage, operation.getId()));
            if (newer(operation.getUpdatedAt(), lastChannelAt) && !"SYSTEM".equals(channel)) {
                lastChannel = channel;
                lastChannelAt = operation.getUpdatedAt();
            }
        }

        for (BusinessOrder order : orderRows) {
            List<BusinessOrderLine> lines = orderLines.findAllByOrderIdOrderByCreatedAtAsc(order.getId());
            String detail = lines.isEmpty()
                    ? selectedProduct
                    : lines.stream()
                            .map(BusinessOrderLine::getItemName)
                            .filter(value -> value != null && !value.isBlank())
                            .findFirst().orElse(selectedProduct);
            String channel = source(order.getSource());
            add(events, new TimelineEvent(
                    order.getCreatedAt(), "ORDER", channel, "Pedido",
                    detail, enumName(order.getStatus()), order.getOperationId()));
            if (newer(order.getCreatedAt(), lastChannelAt) && !"SYSTEM".equals(channel)) {
                lastChannel = channel;
                lastChannelAt = order.getCreatedAt();
            }
        }

        for (BusinessPayment payment : paymentRows) {
            String channel = source(payment.getSource());
            add(events, new TimelineEvent(
                    payment.getCreatedAt(), "PAYMENT", channel, "Pago",
                    money(payment.getAmount(), payment.getCurrency()),
                    enumName(payment.getStatus()), payment.getOperationId()));
            if (newer(payment.getCreatedAt(), lastChannelAt) && !"SYSTEM".equals(channel)) {
                lastChannel = channel;
                lastChannelAt = payment.getCreatedAt();
            }
        }

        for (InventoryReservation reservation : inventoryRows) {
            String detail = inventoryDetail(
                    selectedProduct, selectedVariant,
                    reservation.getQuantity(), reservation.getVariantId());
            add(events, new TimelineEvent(
                    reservation.getUpdatedAt(), "INVENTORY", "SYSTEM",
                    inventoryTitle(reservation.getStatus()), detail,
                    enumName(reservation.getStatus()), reservation.getReferenceId()));
        }

        for (OutboundMessage message : outboundRows) {
            add(events, new TimelineEvent(
                    message.getCreatedAt(), "OUTBOUND", "WHATSAPP",
                    outboundTitle(message.getPurpose()),
                    outboundDetail(message), enumName(message.getStatus()), message.getOperationId()));
            if (newer(message.getCreatedAt(), lastChannelAt)) {
                lastChannel = "WHATSAPP";
                lastChannelAt = message.getCreatedAt();
            }
        }

        events.sort(Comparator.comparing(
                TimelineEvent::at,
                Comparator.nullsLast(Comparator.naturalOrder())).reversed());

        TimelineSummary summary = new TimelineSummary(
                commercialStage,
                selectedProduct,
                selectedVariantId,
                selectedVariant,
                latestOrder == null ? null : enumName(latestOrder.getStatus()),
                latestPayment == null ? null : enumName(latestPayment.getStatus()),
                latestInventory == null ? null : enumName(latestInventory.getStatus()),
                lastChannel);

        return new TimelineResponse(summary, List.copyOf(events));
    }

    private static void add(List<TimelineEvent> events, TimelineEvent event) {
        if (event.at() != null) events.add(event);
    }

    private static boolean newer(Instant candidate, Instant current) {
        return candidate != null && (current == null || candidate.isAfter(current));
    }

    private static String operationTitle(BusinessOperation.Type type) {
        if (type == null) return "Actividad comercial";
        return switch (type) {
            case ORDER -> "Pedido comercial";
            case QUOTE -> "Cotización";
            case PAYMENT -> "Pago comercial";
            case REQUEST -> "Compra omnicanal";
            case LEAD -> "Oportunidad";
            case DELIVERY -> "Despacho";
            case BOOKING -> "Reserva comercial";
        };
    }

    private static String inventoryTitle(InventoryReservation.Status status) {
        if (status == null) return "Inventario";
        return switch (status) {
            case ACTIVE -> "Inventario reservado";
            case CONSUMED -> "Inventario consumido";
            case RELEASED -> "Inventario liberado";
            case EXPIRED -> "Reserva de inventario expirada";
        };
    }

    private static String inventoryDetail(
            String product, String variant, int quantity, UUID reservationVariantId) {
        List<String> parts = new ArrayList<>();
        if (product != null && !product.isBlank()) parts.add(product);
        if (reservationVariantId != null && variant != null && !variant.isBlank()) parts.add(variant);
        parts.add(quantity + (quantity == 1 ? " unidad" : " unidades"));
        return String.join(" · ", parts);
    }

    private static String outboundTitle(OutboundMessage.Purpose purpose) {
        if (purpose == null) return "Mensaje saliente";
        return switch (purpose) {
            case PAYMENT_LINK -> "Link de pago";
            case PAYMENT_CONFIRMATION -> "Confirmación de pago";
            case BOOKING_CONFIRMATION -> "Confirmación de reserva";
            case MEETING_LINK -> "Link de reunión";
            case ORDER_STATUS -> "Estado del pedido";
            case QUOTE -> "Cotización enviada";
            case REMINDER -> "Recordatorio";
            case DELIVERY_STATUS -> "Estado del despacho";
            case INCIDENT_NOTICE -> "Aviso de incidente";
            case PRODUCT_SHOWCASE -> "Productos enviados";
        };
    }

    private static String outboundDetail(OutboundMessage message) {
        return switch (message.getStatus()) {
            case PREPARED -> "Mensaje preparado";
            case QUEUED -> "Mensaje en cola";
            case SENT -> "Mensaje enviado";
            case FAILED -> "Error de envío";
            case CANCELLED -> "Mensaje cancelado";
            case BLOCKED -> "Mensaje bloqueado";
        };
    }

    private static String money(BigDecimal amount, String currency) {
        if (amount == null) return null;
        return amount.stripTrailingZeros().toPlainString() + " " + text(currency, "CLP");
    }

    private static String source(BusinessOrder.Source source) {
        return source == null ? "SYSTEM" : source.name();
    }

    private static String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static UUID uuidOrNull(Object raw) {
        if (raw == null) return null;
        try {
            return UUID.fromString(String.valueOf(raw));
        } catch (Exception ignored) {
            return null;
        }
    }

    public record TimelineResponse(TimelineSummary summary, List<TimelineEvent> events) {}

    public record TimelineSummary(
            String commercialStage,
            String selectedProduct,
            UUID selectedVariantId,
            String selectedVariant,
            String orderStatus,
            String paymentStatus,
            String inventoryStatus,
            String lastChannel) {}

    public record TimelineEvent(
            Instant at,
            String type,
            String channel,
            String title,
            String detail,
            String status,
            UUID operationId) {}
}
