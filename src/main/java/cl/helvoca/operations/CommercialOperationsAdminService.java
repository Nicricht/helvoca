package cl.helvoca.operations;

import cl.helvoca.common.NotFoundException;
import cl.helvoca.security.TenantProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class CommercialOperationsAdminService {
    private final BusinessOrderRepository orders;
    private final BusinessOrderLineRepository orderLines;
    private final BusinessQuoteRepository quotes;
    private final BusinessLeadRepository leads;
    private final TenantProvider tenantProvider;

    public CommercialOperationsAdminService(BusinessOrderRepository orders,
                                            BusinessOrderLineRepository orderLines,
                                            BusinessQuoteRepository quotes,
                                            BusinessLeadRepository leads,
                                            TenantProvider tenantProvider) {
        this.orders = orders;
        this.orderLines = orderLines;
        this.quotes = quotes;
        this.leads = leads;
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
        order.setStatus(status);
        return orderView(orders.saveAndFlush(order));
    }

    @Transactional(readOnly = true)
    public List<QuoteView> quotes() {
        UUID businessId = tenantProvider.requireBusinessId();
        return quotes.findAllByBusinessIdOrderByCreatedAtDesc(businessId).stream()
                .limit(100)
                .map(QuoteView::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LeadView> leads() {
        UUID businessId = tenantProvider.requireBusinessId();
        return leads.findAllByBusinessIdOrderByCreatedAtDesc(businessId).stream()
                .limit(100)
                .map(LeadView::from)
                .toList();
    }

    private OrderView orderView(BusinessOrder order) {
        List<OrderLineView> lines = orderLines.findAllByOrderIdOrderByCreatedAtAsc(order.getId()).stream()
                .map(OrderLineView::from)
                .toList();
        return new OrderView(order.getId(), order.getStatus(), order.getFulfillmentType(),
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

    public record OrderView(UUID id, BusinessOrder.Status status, BusinessOrder.FulfillmentType fulfillmentType,
                            String contactName, String contactPhone, String deliveryAddress,
                            BigDecimal subtotal, BigDecimal deliveryFee, BigDecimal total, String currency,
                            BusinessOrder.Source source, List<OrderLineView> lines,
                            Instant createdAt, Instant updatedAt) {}

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
