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
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommercialPipelineServiceBranchCoverageTest {

    @Test
    void sparseCommercialJourneyUsesSafeFallbacksAndCountsRequiresAction() {
        UUID businessId = UUID.randomUUID();
        UUID journeyId = UUID.randomUUID();

        BusinessOperationRepository operations = mock(BusinessOperationRepository.class);
        BusinessOperationItemRepository items = mock(BusinessOperationItemRepository.class);
        BusinessOrderRepository orders = mock(BusinessOrderRepository.class);
        BusinessPaymentRepository payments = mock(BusinessPaymentRepository.class);
        InventoryReservationRepository reservations = mock(InventoryReservationRepository.class);
        InventoryProductVariantRepository variants = mock(InventoryProductVariantRepository.class);
        OutboundMessageRepository outbound = mock(OutboundMessageRepository.class);
        CustomerRepository customers = mock(CustomerRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);

        when(tenant.requireBusinessId()).thenReturn(businessId);

        BusinessOperation ignored = mock(BusinessOperation.class);
        when(ignored.getMetadata()).thenReturn(null);

        BusinessOperation journey = mock(BusinessOperation.class);
        when(journey.getId()).thenReturn(journeyId);
        when(journey.getCustomerId()).thenReturn(null);
        when(journey.getSource()).thenReturn(null);
        when(journey.getContactName()).thenReturn("Contacto");
        when(journey.getContactPhone()).thenReturn("+56911111111");
        when(journey.getTotal()).thenReturn(new BigDecimal("9990"));
        when(journey.getCurrency()).thenReturn("CLP");
        when(journey.getUpdatedAt()).thenReturn(Instant.parse("2026-09-27T02:00:00Z"));
        when(journey.getMetadata()).thenReturn(Map.of(
                "selectedCatalogItemId", UUID.randomUUID().toString(),
                "selectedVariantId", "not-a-uuid",
                "orderOperationId", "also-not-a-uuid",
                "paymentOperationId", "still-not-a-uuid",
                "paymentStatus", "REQUIRES_ACTION",
                "handoffChannel", " "));
        when(items.findAllByOperationIdOrderByCreatedAtAsc(journeyId)).thenReturn(List.of());
        when(operations.findTop100ByBusinessIdAndTypeOrderByUpdatedAtDesc(
                businessId, BusinessOperation.Type.REQUEST)).thenReturn(List.of(ignored, journey));

        CommercialPipelineService service = service(
                operations, items, orders, payments, reservations, variants, outbound, customers, tenant);

        CommercialPipelineService.PipelineResponse response = service.get();

        assertEquals(1, response.total());
        assertEquals(1, response.active());
        assertEquals(0, response.paid());
        assertEquals(1, response.needsAction());

        CommercialPipelineService.PipelineItem row = response.items().get(0);
        assertEquals("Contacto", row.customerName());
        assertEquals("+56911111111", row.customerPhone());
        assertNull(row.channel());
        assertNull(row.product());
        assertNull(row.variantId());
        assertNull(row.variant());
        assertNull(row.quantity());
        assertEquals(new BigDecimal("9990"), row.total());
        assertEquals("CLP", row.currency());
        assertNull(row.orderStatus());
        assertEquals("REQUIRES_ACTION", row.paymentStatus());
        assertNull(row.inventoryStatus());
        assertNull(row.outboundStatus());

        verifyNoInteractions(customers, orders, payments, reservations, variants, outbound);
    }

    @Test
    void nullableRelatedEntitiesUseFallbacksAndCountsFailedPayment() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID journeyId = UUID.randomUUID();
        UUID orderOperationId = UUID.randomUUID();
        UUID paymentOperationId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();

        BusinessOperationRepository operations = mock(BusinessOperationRepository.class);
        BusinessOperationItemRepository items = mock(BusinessOperationItemRepository.class);
        BusinessOrderRepository orders = mock(BusinessOrderRepository.class);
        BusinessPaymentRepository payments = mock(BusinessPaymentRepository.class);
        InventoryReservationRepository reservations = mock(InventoryReservationRepository.class);
        InventoryProductVariantRepository variants = mock(InventoryProductVariantRepository.class);
        OutboundMessageRepository outbound = mock(OutboundMessageRepository.class);
        CustomerRepository customers = mock(CustomerRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);

        when(tenant.requireBusinessId()).thenReturn(businessId);

        BusinessOperation journey = mock(BusinessOperation.class);
        when(journey.getId()).thenReturn(journeyId);
        when(journey.getCustomerId()).thenReturn(customerId);
        when(journey.getSource()).thenReturn(BusinessOrder.Source.VOICE);
        when(journey.getContactName()).thenReturn("Fallback Name");
        when(journey.getContactPhone()).thenReturn("+56933333333");
        when(journey.getUpdatedAt()).thenReturn(Instant.parse("2026-09-27T03:00:00Z"));
        when(journey.getMetadata()).thenReturn(Map.of(
                "commercialStage", "PAYMENT_LINK_SENT",
                "orderOperationId", orderOperationId.toString(),
                "paymentOperationId", paymentOperationId.toString(),
                "paymentStatus", "FAILED"));
        when(operations.findTop100ByBusinessIdAndTypeOrderByUpdatedAtDesc(
                businessId, BusinessOperation.Type.REQUEST)).thenReturn(List.of(journey));
        when(customers.findByIdAndBusinessId(customerId, businessId)).thenReturn(Optional.empty());

        BusinessOperationItem item = mock(BusinessOperationItem.class);
        when(item.getItemName()).thenReturn("Producto");
        when(item.getVariantId()).thenReturn(variantId);
        when(item.getQuantity()).thenReturn(2);
        when(items.findAllByOperationIdOrderByCreatedAtAsc(journeyId)).thenReturn(List.of(item));
        when(variants.findByIdAndBusinessId(variantId, businessId)).thenReturn(Optional.empty());

        BusinessOrder order = mock(BusinessOrder.class);
        when(order.getTotal()).thenReturn(new BigDecimal("20000"));
        when(order.getCurrency()).thenReturn("CLP");
        when(order.getStatus()).thenReturn(null);
        when(orders.findByOperationIdAndBusinessId(orderOperationId, businessId)).thenReturn(Optional.of(order));

        BusinessPayment payment = mock(BusinessPayment.class);
        when(payment.getStatus()).thenReturn(null);
        when(payments.findByOperationIdAndBusinessId(paymentOperationId, businessId))
                .thenReturn(Optional.of(payment));

        InventoryReservation reservation = mock(InventoryReservation.class);
        when(reservation.getUpdatedAt()).thenReturn(null);
        when(reservation.getStatus()).thenReturn(null);
        when(reservations.findAllByBusinessIdAndReferenceTypeAndReferenceIdOrderByCreatedAtAsc(
                businessId, "ORDER_OPERATION", orderOperationId)).thenReturn(List.of(reservation));

        OutboundMessage withoutOperation = mock(OutboundMessage.class);
        when(withoutOperation.getOperationId()).thenReturn(null);
        OutboundMessage unrelated = mock(OutboundMessage.class);
        when(unrelated.getOperationId()).thenReturn(UUID.randomUUID());
        OutboundMessage matchingOrder = mock(OutboundMessage.class);
        when(matchingOrder.getOperationId()).thenReturn(orderOperationId);
        when(matchingOrder.getStatus()).thenReturn(null);
        when(outbound.findTop50ByBusinessIdAndCustomerIdOrderByCreatedAtDesc(
                businessId, customerId)).thenReturn(List.of(withoutOperation, unrelated, matchingOrder));

        CommercialPipelineService service = service(
                operations, items, orders, payments, reservations, variants, outbound, customers, tenant);

        CommercialPipelineService.PipelineResponse response = service.get();

        assertEquals(1, response.needsAction());
        CommercialPipelineService.PipelineItem row = response.items().get(0);
        assertEquals("Fallback Name", row.customerName());
        assertEquals("+56933333333", row.customerPhone());
        assertEquals("VOICE", row.channel());
        assertEquals("Producto", row.product());
        assertEquals(variantId, row.variantId());
        assertNull(row.variant());
        assertEquals(2, row.quantity());
        assertEquals(new BigDecimal("20000"), row.total());
        assertEquals("CLP", row.currency());
        assertNull(row.orderStatus());
        assertEquals("FAILED", row.paymentStatus());
        assertNull(row.inventoryStatus());
        assertNull(row.outboundStatus());
    }

    @Test
    void coversAllCommercialMarkersStatusBranchesAndOutboundMatches() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID paymentOperationId = UUID.randomUUID();
        UUID orderOperationId = UUID.randomUUID();

        BusinessOperationRepository operations = mock(BusinessOperationRepository.class);
        BusinessOperationItemRepository items = mock(BusinessOperationItemRepository.class);
        BusinessOrderRepository orders = mock(BusinessOrderRepository.class);
        BusinessPaymentRepository payments = mock(BusinessPaymentRepository.class);
        InventoryReservationRepository reservations = mock(InventoryReservationRepository.class);
        InventoryProductVariantRepository variants = mock(InventoryProductVariantRepository.class);
        OutboundMessageRepository outbound = mock(OutboundMessageRepository.class);
        CustomerRepository customers = mock(CustomerRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);

        when(tenant.requireBusinessId()).thenReturn(businessId);

        Customer customer = mock(Customer.class);
        when(customer.getName()).thenReturn("Cliente");
        when(customer.getPhone()).thenReturn("+56944444444");
        when(customers.findByIdAndBusinessId(customerId, businessId)).thenReturn(Optional.of(customer));

        BusinessOperation paymentJourney = journey(
                customerId, Map.of(
                        "paymentOperationId", paymentOperationId.toString(),
                        "commercialStage", "PAYMENT_FAILED",
                        "paymentStatus", "PENDING"));
        BusinessOperation orderJourney = journey(
                customerId, Map.of("orderOperationId", orderOperationId.toString()));
        BusinessOperation paymentMarkerOnly = journey(
                customerId, Map.of("paymentOperationId", paymentOperationId.toString()));

        BusinessPayment succeeded = mock(BusinessPayment.class);
        when(succeeded.getStatus()).thenReturn(BusinessPayment.Status.SUCCEEDED);
        when(payments.findByOperationIdAndBusinessId(paymentOperationId, businessId))
                .thenReturn(Optional.of(succeeded));

        BusinessOrder confirmed = mock(BusinessOrder.class);
        when(confirmed.getStatus()).thenReturn(BusinessOrder.Status.CONFIRMED);
        when(confirmed.getTotal()).thenReturn(new BigDecimal("5000"));
        when(confirmed.getCurrency()).thenReturn("CLP");
        when(orders.findByOperationIdAndBusinessId(orderOperationId, businessId))
                .thenReturn(Optional.of(confirmed));

        for (BusinessOperation op : List.of(paymentJourney, orderJourney, paymentMarkerOnly)) {
            when(items.findAllByOperationIdOrderByCreatedAtAsc(op.getId())).thenReturn(List.of());
        }
        when(reservations.findAllByBusinessIdAndReferenceTypeAndReferenceIdOrderByCreatedAtAsc(
                businessId, "ORDER_OPERATION", orderOperationId)).thenReturn(List.of());

        UUID paymentJourneyId = paymentJourney.getId();
        OutboundMessage journeyMessage = mock(OutboundMessage.class);
        when(journeyMessage.getOperationId()).thenReturn(paymentJourneyId);
        when(journeyMessage.getStatus()).thenReturn(OutboundMessage.Status.SENT);

        OutboundMessage paymentMessage = mock(OutboundMessage.class);
        when(paymentMessage.getOperationId()).thenReturn(paymentOperationId);
        when(paymentMessage.getStatus()).thenReturn(OutboundMessage.Status.PREPARED);

        when(outbound.findTop50ByBusinessIdAndCustomerIdOrderByCreatedAtDesc(businessId, customerId))
                .thenReturn(List.of(journeyMessage))
                .thenReturn(List.of())
                .thenReturn(List.of(paymentMessage));

        when(operations.findTop100ByBusinessIdAndTypeOrderByUpdatedAtDesc(
                businessId, BusinessOperation.Type.REQUEST))
                .thenReturn(List.of(paymentJourney, orderJourney, paymentMarkerOnly));

        CommercialPipelineService service = service(
                operations, items, orders, payments, reservations, variants, outbound, customers, tenant);

        CommercialPipelineService.PipelineResponse response = service.get();

        assertEquals(3, response.total());
        assertEquals(3, response.active());
        assertEquals(0, response.paid());
        assertEquals(1, response.needsAction());

        CommercialPipelineService.PipelineItem first = response.items().get(0);
        assertEquals("SUCCEEDED", first.paymentStatus());
        assertEquals("SENT", first.outboundStatus());

        CommercialPipelineService.PipelineItem second = response.items().get(1);
        assertEquals("CONFIRMED", second.orderStatus());

        CommercialPipelineService.PipelineItem third = response.items().get(2);
        assertEquals("SUCCEEDED", third.paymentStatus());
        assertEquals("PREPARED", third.outboundStatus());
    }

    private static BusinessOperation journey(UUID customerId, Map<String, Object> metadata) {
        BusinessOperation journey = mock(BusinessOperation.class);
        when(journey.getId()).thenReturn(UUID.randomUUID());
        when(journey.getCustomerId()).thenReturn(customerId);
        when(journey.getSource()).thenReturn(BusinessOrder.Source.WHATSAPP);
        when(journey.getMetadata()).thenReturn(metadata);
        when(journey.getUpdatedAt()).thenReturn(Instant.parse("2026-09-27T04:00:00Z"));
        return journey;
    }

    private static CommercialPipelineService service(
            BusinessOperationRepository operations,
            BusinessOperationItemRepository items,
            BusinessOrderRepository orders,
            BusinessPaymentRepository payments,
            InventoryReservationRepository reservations,
            InventoryProductVariantRepository variants,
            OutboundMessageRepository outbound,
            CustomerRepository customers,
            TenantProvider tenant) {
        return new CommercialPipelineService(
                operations, items, orders, payments, reservations, variants, outbound, customers, tenant);
    }
}
