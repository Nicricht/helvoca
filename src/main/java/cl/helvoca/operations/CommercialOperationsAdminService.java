package cl.helvoca.operations;

import cl.helvoca.common.NotFoundException;
import cl.helvoca.delivery.BusinessDelivery;
import cl.helvoca.delivery.BusinessDeliveryRepository;
import cl.helvoca.security.TenantProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class CommercialOperationsAdminService {
    private final BusinessOrderRepository orders;
    private final BusinessOrderLineRepository orderLines;
    private final BusinessQuoteRepository quotes;
    private final BusinessLeadRepository leads;
    private final BusinessDeliveryRepository deliveries;
    private final BusinessOperationRepository operations;
    private final TenantProvider tenantProvider;

    public CommercialOperationsAdminService(BusinessOrderRepository orders,
                                            BusinessOrderLineRepository orderLines,
                                            BusinessQuoteRepository quotes,
                                            BusinessLeadRepository leads,
                                            BusinessDeliveryRepository deliveries,
                                            BusinessOperationRepository operations,
                                            TenantProvider tenantProvider) {
        this.orders = orders;
        this.orderLines = orderLines;
        this.quotes = quotes;
        this.leads = leads;
        this.deliveries = deliveries;
        this.operations = operations;
        this.tenantProvider = tenantProvider;
    }

    @Transactional(readOnly = true)
    public List<OrderView> orders() {
        UUID businessId = tenantProvider.requireBusinessId();
        return orders.findAllByBusinessIdOrderByCreatedAtDesc(businessId).stream()
                .limit(100)
                .map(this::orderView)
                .toList();
    }

    @Transactional
    public OrderView updateOrderStatus(UUID orderId, BusinessOrder.Status status) {
        if (status == null) throw new IllegalArgumentException("Order status is required");
        UUID businessId = tenantProvider.requireBusinessId();
        BusinessOrder order = orders.findByIdAndBusinessId(orderId, businessId)
                .orElseThrow(() -> new NotFoundException("Order not found"));
        validateTransition(order, status);
        if (order.getStatus() != status) {
            order.setStatus(status);
            order = orders.saveAndFlush(order);
        }
        return orderView(order);
    }

    @Transactional(readOnly = true)
    public List<DeliveryView> deliveries() {
        UUID businessId = tenantProvider.requireBusinessId();
        return deliveries.findAllByBusinessIdOrderByCreatedAtDesc(businessId).stream()
                .limit(100)
                .map(DeliveryView::from)
                .toList();
    }

    @Transactional
    public DeliveryView updateDeliveryStatus(UUID deliveryId, BusinessDelivery.Status status) {
        if (status == null) throw new IllegalArgumentException("Delivery status is required");
        UUID businessId = tenantProvider.requireBusinessId();
        BusinessDelivery delivery = deliveries.findByIdAndBusinessId(deliveryId, businessId)
                .orElseThrow(() -> new NotFoundException("Delivery not found"));
        validateDeliveryTransition(delivery, status);
        if (delivery.getStatus() != status) {
            delivery.setStatus(status);
            delivery = deliveries.saveAndFlush(delivery);
            synchronizeDeliveryOperation(businessId, delivery);
        }
        return DeliveryView.from(delivery);
    }

    @Transactional(readOnly = true)
    public List<QuoteView> quotes() {
        UUID businessId = tenantProvider.requireBusinessId();
        return quotes.findAllByBusinessIdOrderByCreatedAtDesc(businessId).stream()
                .limit(100)
                .map(QuoteView::from)
                .toList();
    }

    @Transactional
    public QuoteView updateQuoteStatus(UUID quoteId, BusinessQuote.Status status) {
        if (status == null) throw new IllegalArgumentException("Quote status is required");
        UUID businessId = tenantProvider.requireBusinessId();
        BusinessQuote quote = quotes.findByIdAndBusinessId(quoteId, businessId)
                .orElseThrow(() -> new NotFoundException("Quote not found"));
        validateQuoteTransition(quote, status);
        if (quote.getStatus() != status) {
            quote.setStatus(status);
            quote = quotes.saveAndFlush(quote);
            synchronizeQuoteOperation(businessId, quote);
        }
        return QuoteView.from(quote);
    }

    @Transactional(readOnly = true)
    public List<LeadView> leads() {
        UUID businessId = tenantProvider.requireBusinessId();
        return leads.findAllByBusinessIdOrderByCreatedAtDesc(businessId).stream()
                .limit(100)
                .map(LeadView::from)
                .toList();
    }

    @Transactional
    public LeadView updateLeadStatus(UUID leadId, BusinessLead.Status status) {
        if (status == null) throw new IllegalArgumentException("Lead status is required");
        UUID businessId = tenantProvider.requireBusinessId();
        BusinessLead lead = leads.findByIdAndBusinessId(leadId, businessId)
                .orElseThrow(() -> new NotFoundException("Lead not found"));
        validateLeadTransition(lead, status);
        if (lead.getStatus() != status) {
            lead.setStatus(status);
            lead = leads.saveAndFlush(lead);
            synchronizeLeadOperation(businessId, lead);
        }
        return LeadView.from(lead);
    }

    private static void validateTransition(BusinessOrder order, BusinessOrder.Status next) {
        BusinessOrder.Status current = order.getStatus();
        if (current == next) return;

        Set<BusinessOrder.Status> allowed = switch (current) {
            case CONFIRMED -> Set.of(BusinessOrder.Status.PREPARING, BusinessOrder.Status.CANCELLED);
            case PREPARING -> Set.of(BusinessOrder.Status.READY, BusinessOrder.Status.CANCELLED);
            case READY -> order.getFulfillmentType() == BusinessOrder.FulfillmentType.DELIVERY
                    ? Set.of(BusinessOrder.Status.DISPATCHED)
                    : Set.of(BusinessOrder.Status.COMPLETED);
            case DISPATCHED -> Set.of(BusinessOrder.Status.COMPLETED);
            case COMPLETED, CANCELLED -> Set.of();
        };
        if (!allowed.contains(next)) {
            throw new IllegalArgumentException("Invalid order status transition: " + current + " -> " + next);
        }
    }

    private static void validateQuoteTransition(BusinessQuote quote, BusinessQuote.Status next) {
        BusinessQuote.Status current = quote.getStatus();
        if (current == next) return;

        Set<BusinessQuote.Status> allowed = switch (current) {
            case REQUESTED -> Set.of(BusinessQuote.Status.READY, BusinessQuote.Status.CANCELLED);
            case READY -> Set.of(
                    BusinessQuote.Status.ACCEPTED,
                    BusinessQuote.Status.REJECTED,
                    BusinessQuote.Status.CANCELLED);
            case ACCEPTED, REJECTED, CANCELLED -> Set.of();
        };
        if (!allowed.contains(next)) {
            throw new IllegalArgumentException("Invalid quote status transition: " + current + " -> " + next);
        }
    }

    private static void validateLeadTransition(BusinessLead lead, BusinessLead.Status next) {
        BusinessLead.Status current = lead.getStatus();
        if (current == next) return;

        Set<BusinessLead.Status> allowed = switch (current) {
            case NEW -> Set.of(BusinessLead.Status.CONTACTED, BusinessLead.Status.LOST);
            case CONTACTED -> Set.of(BusinessLead.Status.QUALIFIED, BusinessLead.Status.LOST);
            case QUALIFIED -> Set.of(BusinessLead.Status.WON, BusinessLead.Status.LOST);
            case WON, LOST -> Set.of();
        };
        if (!allowed.contains(next)) {
            throw new IllegalArgumentException("Invalid lead status transition: " + current + " -> " + next);
        }
    }

    private static void validateDeliveryTransition(BusinessDelivery delivery, BusinessDelivery.Status next) {
        BusinessDelivery.Status current = delivery.getStatus();
        if (current == next) return;

        Set<BusinessDelivery.Status> allowed = switch (current) {
            case CONFIRMED -> Set.of(BusinessDelivery.Status.IN_TRANSIT, BusinessDelivery.Status.CANCELLED);
            case IN_TRANSIT -> Set.of(BusinessDelivery.Status.DELIVERED);
            case DELIVERED, CANCELLED -> Set.of();
        };
        if (!allowed.contains(next)) {
            throw new IllegalArgumentException("Invalid delivery status transition: " + current + " -> " + next);
        }
    }

    private void synchronizeQuoteOperation(UUID businessId, BusinessQuote quote) {
        BusinessOperation operation = operations
                .findByIdAndBusinessId(quote.getOperationId(), businessId)
                .orElseThrow(() -> new IllegalStateException("Quote operation projection is missing"));
        if (operation.getType() != BusinessOperation.Type.QUOTE) {
            throw new IllegalStateException("Quote points to a non-quote operation");
        }

        operation.setStatus(switch (quote.getStatus()) {
            case ACCEPTED -> BusinessOperation.Status.COMPLETED;
            case REJECTED, CANCELLED -> BusinessOperation.Status.CANCELLED;
            default -> BusinessOperation.Status.CONFIRMED;
        });
        operation.setConfirmationToken(null);
        operation.setRevision(operation.getRevision() == null ? 1 : operation.getRevision() + 1);
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (operation.getMetadata() != null) metadata.putAll(operation.getMetadata());
        metadata.put("intent", "QUOTE");
        metadata.put("confirmationPending", false);
        metadata.put("projectionStatus", quote.getStatus().name());
        metadata.put("quoteId", quote.getId().toString());
        operation.setMetadata(metadata);
        operations.saveAndFlush(operation);
    }

    private void synchronizeLeadOperation(UUID businessId, BusinessLead lead) {
        BusinessOperation operation = operations
                .findByIdAndBusinessId(lead.getOperationId(), businessId)
                .orElseThrow(() -> new IllegalStateException("Lead operation projection is missing"));
        if (operation.getType() != BusinessOperation.Type.LEAD) {
            throw new IllegalStateException("Lead points to a non-lead operation");
        }

        operation.setStatus(switch (lead.getStatus()) {
            case WON -> BusinessOperation.Status.COMPLETED;
            case LOST -> BusinessOperation.Status.CANCELLED;
            default -> BusinessOperation.Status.CONFIRMED;
        });
        operation.setConfirmationToken(null);
        operation.setRevision(operation.getRevision() == null ? 1 : operation.getRevision() + 1);
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (operation.getMetadata() != null) metadata.putAll(operation.getMetadata());
        metadata.put("intent", "LEAD");
        metadata.put("confirmationPending", false);
        metadata.put("projectionStatus", lead.getStatus().name());
        metadata.put("leadId", lead.getId().toString());
        operation.setMetadata(metadata);
        operations.saveAndFlush(operation);
    }

    private void synchronizeDeliveryOperation(UUID businessId, BusinessDelivery delivery) {
        BusinessOperation operation = operations
                .findByIdAndBusinessId(delivery.getOperationId(), businessId)
                .orElseThrow(() -> new IllegalStateException("Delivery operation projection is missing"));
        if (operation.getType() != BusinessOperation.Type.DELIVERY) {
            throw new IllegalStateException("Delivery points to a non-delivery operation");
        }

        operation.setStatus(delivery.getStatus() == BusinessDelivery.Status.CANCELLED
                ? BusinessOperation.Status.CANCELLED
                : BusinessOperation.Status.CONFIRMED);
        operation.setConfirmationToken(null);
        operation.setRevision(operation.getRevision() == null ? 1 : operation.getRevision() + 1);
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (operation.getMetadata() != null) metadata.putAll(operation.getMetadata());
        metadata.put("intent", "DELIVERY");
        metadata.put("confirmationPending", false);
        metadata.put("projectionStatus", delivery.getStatus().name());
        metadata.put("deliveryId", delivery.getId().toString());
        operation.setMetadata(metadata);
        operations.saveAndFlush(operation);
    }

    private OrderView orderView(BusinessOrder order) {
        List<OrderLineView> lines = orderLines.findAllByOrderIdOrderByCreatedAtAsc(order.getId()).stream()
                .map(OrderLineView::from)
                .toList();
        return new OrderView(order.getId(), order.getOperationId(), order.getSourceReferenceId(),
                order.getStatus(), order.getFulfillmentType(),
                order.getContactName(), order.getContactPhone(), order.getDeliveryAddress(),
                order.getSubtotal(), order.getDeliveryFee(), order.getTotal(), order.getCurrency(),
                order.getSource(), lines, order.getCreatedAt(), order.getUpdatedAt());
    }

    public record OrderLineView(UUID catalogItemId, String name, Integer quantity,
                                BigDecimal unitPrice, BigDecimal lineTotal, String notes) {
        static OrderLineView from(BusinessOrderLine line) {
            return new OrderLineView(line.getCatalogItemId(), line.getItemName(), line.getQuantity(),
                    line.getUnitPrice(), line.getLineTotal(), line.getNotes());
        }
    }

    public record OrderView(UUID id, UUID operationId, UUID sourceReferenceId,
                            BusinessOrder.Status status, BusinessOrder.FulfillmentType fulfillmentType,
                            String contactName, String contactPhone, String deliveryAddress,
                            BigDecimal subtotal, BigDecimal deliveryFee, BigDecimal total, String currency,
                            BusinessOrder.Source source, List<OrderLineView> lines,
                            Instant createdAt, Instant updatedAt) {}

    public record DeliveryView(UUID id, UUID operationId, UUID orderId,
                               BusinessDelivery.Status status, String contactName, String contactPhone,
                               UUID deliveryZoneId, String deliveryAddress, BigDecimal fee, String currency,
                               BusinessOrder.Source source, String notes,
                               Instant createdAt, Instant updatedAt) {
        static DeliveryView from(BusinessDelivery delivery) {
            return new DeliveryView(
                    delivery.getId(),
                    delivery.getOperationId(),
                    delivery.getOrderId(),
                    delivery.getStatus(),
                    delivery.getContactName(),
                    delivery.getContactPhone(),
                    delivery.getDeliveryZoneId(),
                    delivery.getDeliveryAddress(),
                    delivery.getFee(),
                    delivery.getCurrency(),
                    delivery.getSource(),
                    delivery.getNotes(),
                    delivery.getCreatedAt(),
                    delivery.getUpdatedAt());
        }
    }

    public record QuoteView(UUID id, String title, String description, BigDecimal amount, String currency,
                            BusinessQuote.Status status, String contactName, String contactPhone,
                            BusinessOrder.Source source, Instant createdAt) {
        static QuoteView from(BusinessQuote quote) {
            return new QuoteView(quote.getId(), quote.getTitle(), quote.getDescription(), quote.getAmount(),
                    quote.getCurrency(), quote.getStatus(), quote.getContactName(), quote.getContactPhone(),
                    quote.getSource(), quote.getCreatedAt());
        }
    }

    public record LeadView(UUID id, String name, String phone, String email, String interest,
                           BigDecimal budget, String notes, BusinessLead.Status status,
                           BusinessOrder.Source source, Instant createdAt) {
        static LeadView from(BusinessLead lead) {
            return new LeadView(lead.getId(), lead.getName(), lead.getPhone(), lead.getEmail(),
                    lead.getInterest(), lead.getBudget(), lead.getNotes(), lead.getStatus(),
                    lead.getSource(), lead.getCreatedAt());
        }
    }
}
