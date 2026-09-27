package cl.helvoca.operations;

import cl.helvoca.customer.Customer;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.inventory.InventoryProductVariant;
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

class CommercialPipelineServiceTest {

    @Test
    void exposesExactVariantPaymentInventoryAndOutboundStateForTenantJourney() {
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
        when(journey.getBusinessId()).thenReturn(businessId);
        when(journey.getCustomerId()).thenReturn(customerId);
        when(journey.getType()).thenReturn(BusinessOperation.Type.REQUEST);
        when(journey.getSource()).thenReturn(BusinessOrder.Source.VOICE);
        when(journey.getUpdatedAt()).thenReturn(Instant.parse("2026-09-27T01:00:00Z"));
        when(journey.getMetadata()).thenReturn(Map.of(
                "commercialStage", "PAID",
                "handoffChannel", "WHATSAPP",
                "selectedVariantId", variantId.toString(),
                "orderOperationId", orderOperationId.toString(),
                "paymentOperationId", paymentOperationId.toString()));
        when(operations.findTop100ByBusinessIdAndTypeOrderByUpdatedAtDesc(
                businessId, BusinessOperation.Type.REQUEST)).thenReturn(List.of(journey));

        Customer customer = mock(Customer.class);
        when(customer.getName()).thenReturn("Ana Compra");
        when(customer.getPhone()).thenReturn("+56922222222");
        when(customers.findByIdAndBusinessId(customerId, businessId)).thenReturn(Optional.of(customer));

        BusinessOperationItem item = mock(BusinessOperationItem.class);
        when(item.getItemName()).thenReturn("Zapatilla Urban");
        when(item.getVariantId()).thenReturn(variantId);
        when(item.getQuantity()).thenReturn(1);
        when(items.findAllByOperationIdOrderByCreatedAtAsc(journeyId)).thenReturn(List.of(item));

        InventoryProductVariant variant = mock(InventoryProductVariant.class);
        when(variant.getName()).thenReturn("Negro / 42");
        when(variants.findByIdAndBusinessId(variantId, businessId)).thenReturn(Optional.of(variant));

        BusinessOrder order = mock(BusinessOrder.class);
        when(order.getTotal()).thenReturn(new BigDecimal("12990"));
        when(order.getCurrency()).thenReturn("CLP");
        when(order.getStatus()).thenReturn(BusinessOrder.Status.CONFIRMED);
        when(orders.findByOperationIdAndBusinessId(orderOperationId, businessId)).thenReturn(Optional.of(order));

        BusinessPayment payment = mock(BusinessPayment.class);
        when(payment.getStatus()).thenReturn(BusinessPayment.Status.SUCCEEDED);
        when(payments.findByOperationIdAndBusinessId(paymentOperationId, businessId)).thenReturn(Optional.of(payment));

        InventoryReservation reservation = mock(InventoryReservation.class);
        when(reservation.getStatus()).thenReturn(InventoryReservation.Status.CONSUMED);
        when(reservation.getUpdatedAt()).thenReturn(Instant.parse("2026-09-27T01:01:00Z"));
        when(reservations.findAllByBusinessIdAndReferenceTypeAndReferenceIdOrderByCreatedAtAsc(
                businessId, "ORDER_OPERATION", orderOperationId)).thenReturn(List.of(reservation));

        OutboundMessage message = mock(OutboundMessage.class);
        when(message.getOperationId()).thenReturn(paymentOperationId);
        when(message.getStatus()).thenReturn(OutboundMessage.Status.PREPARED);
        when(outbound.findTop50ByBusinessIdAndCustomerIdOrderByCreatedAtDesc(
                businessId, customerId)).thenReturn(List.of(message));

        CommercialPipelineService resultService = new CommercialPipelineService(
                operations, items, orders, payments, reservations, variants, outbound, customers, tenant);

        CommercialPipelineService.PipelineResponse result = resultService.get();

        assertEquals(1, result.total());
        assertEquals(1, result.paid());
        assertEquals(0, result.needsAction());
        assertEquals(0, result.active());

        CommercialPipelineService.PipelineItem row = result.items().get(0);
        assertEquals(customerId, row.customerId());
        assertEquals("Ana Compra", row.customerName());
        assertEquals("PAID", row.commercialStage());
        assertEquals("WHATSAPP", row.channel());
        assertEquals("Zapatilla Urban", row.product());
        assertEquals(variantId, row.variantId());
        assertEquals("Negro / 42", row.variant());
        assertEquals("CONFIRMED", row.orderStatus());
        assertEquals("SUCCEEDED", row.paymentStatus());
        assertEquals("CONSUMED", row.inventoryStatus());
        assertEquals("PREPARED", row.outboundStatus());
        assertEquals(0, new BigDecimal("12990").compareTo(row.total()));
    }

    @Test
    void ignoresNonCommercialRequestOperations() {
        UUID businessId = UUID.randomUUID();
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
        BusinessOperation generic = mock(BusinessOperation.class);
        when(generic.getMetadata()).thenReturn(Map.of("intent", "GENERAL_REQUEST"));
        when(operations.findTop100ByBusinessIdAndTypeOrderByUpdatedAtDesc(
                businessId, BusinessOperation.Type.REQUEST)).thenReturn(List.of(generic));

        CommercialPipelineService service = new CommercialPipelineService(
                operations, items, orders, payments, reservations, variants, outbound, customers, tenant);

        assertTrue(service.get().items().isEmpty());
        verifyNoInteractions(items, orders, payments, reservations, variants, outbound, customers);
    }
    @Test
    void coversPipelineFallbacksMissingRelationsAndNeedsActionStates() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID journeyOneId = UUID.randomUUID();
        UUID journeyTwoId = UUID.randomUUID();
        UUID journeyThreeId = UUID.randomUUID();
        UUID itemVariantId = UUID.randomUUID();

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

        BusinessOperation first = mock(BusinessOperation.class);
        when(first.getId()).thenReturn(journeyOneId);
        when(first.getCustomerId()).thenReturn(null);
        when(first.getContactName()).thenReturn("Sin ficha");
        when(first.getContactPhone()).thenReturn("+56900000001");
        when(first.getSource()).thenReturn(null);
        when(first.getTotal()).thenReturn(new BigDecimal("100"));
        when(first.getCurrency()).thenReturn("CLP");
        when(first.getUpdatedAt()).thenReturn(Instant.parse("2026-09-27T05:00:00Z"));
        when(first.getMetadata()).thenReturn(Map.of(
                "selectedCatalogItemId", UUID.randomUUID().toString(),
                "paymentStatus", "FAILED",
                "handoffChannel", " "));

        BusinessOperationItem firstItem = mock(BusinessOperationItem.class);
        when(firstItem.getItemName()).thenReturn("Producto fallback");
        when(firstItem.getVariantId()).thenReturn(itemVariantId);
        when(firstItem.getQuantity()).thenReturn(2);
        when(items.findAllByOperationIdOrderByCreatedAtAsc(journeyOneId))
                .thenReturn(List.of(firstItem));
        when(variants.findByIdAndBusinessId(itemVariantId, businessId)).thenReturn(Optional.empty());

        BusinessOperation second = mock(BusinessOperation.class);
        when(second.getId()).thenReturn(journeyTwoId);
        when(second.getCustomerId()).thenReturn(customerId);
        when(second.getContactName()).thenReturn("Contacto original");
        when(second.getContactPhone()).thenReturn("+56900000002");
        when(second.getSource()).thenReturn(BusinessOrder.Source.VOICE);
        when(second.getTotal()).thenReturn(new BigDecimal("200"));
        when(second.getCurrency()).thenReturn("USD");
        when(second.getUpdatedAt()).thenReturn(Instant.parse("2026-09-27T05:01:00Z"));
        when(second.getMetadata()).thenReturn(Map.of(
                "orderOperationId", "not-a-uuid",
                "paymentOperationId", "also-not-a-uuid",
                "commercialStage", "PAYMENT_FAILED"));

        when(customers.findByIdAndBusinessId(customerId, businessId)).thenReturn(Optional.empty());
        when(items.findAllByOperationIdOrderByCreatedAtAsc(journeyTwoId)).thenReturn(List.of());
        OutboundMessage irrelevant = mock(OutboundMessage.class);
        when(irrelevant.getOperationId()).thenReturn(null);
        when(outbound.findTop50ByBusinessIdAndCustomerIdOrderByCreatedAtDesc(businessId, customerId))
                .thenReturn(List.of(irrelevant));

        BusinessOperation third = mock(BusinessOperation.class);
        UUID missingOrderOperationId = UUID.randomUUID();
        UUID missingPaymentOperationId = UUID.randomUUID();
        when(third.getId()).thenReturn(journeyThreeId);
        when(third.getCustomerId()).thenReturn(customerId);
        when(third.getSource()).thenReturn(BusinessOrder.Source.WHATSAPP);
        when(third.getUpdatedAt()).thenReturn(Instant.parse("2026-09-27T05:02:00Z"));
        when(third.getMetadata()).thenReturn(Map.of(
                "paymentOperationId", missingPaymentOperationId.toString(),
                "orderOperationId", missingOrderOperationId.toString(),
                "selectedVariantId", "bad-variant-id",
                "paymentStatus", "REQUIRES_ACTION"));
        when(items.findAllByOperationIdOrderByCreatedAtAsc(journeyThreeId)).thenReturn(List.of());
        when(orders.findByOperationIdAndBusinessId(missingOrderOperationId, businessId))
                .thenReturn(Optional.empty());
        when(payments.findByOperationIdAndBusinessId(missingPaymentOperationId, businessId))
                .thenReturn(Optional.empty());
        when(reservations.findAllByBusinessIdAndReferenceTypeAndReferenceIdOrderByCreatedAtAsc(
                businessId, "ORDER_OPERATION", missingOrderOperationId)).thenReturn(List.of());

        when(operations.findTop100ByBusinessIdAndTypeOrderByUpdatedAtDesc(
                businessId, BusinessOperation.Type.REQUEST))
                .thenReturn(List.of(first, second, third));

        CommercialPipelineService service = new CommercialPipelineService(
                operations, items, orders, payments, reservations, variants, outbound, customers, tenant);

        CommercialPipelineService.PipelineResponse result = service.get();

        assertEquals(3, result.total());
        assertEquals(3, result.active());
        assertEquals(0, result.paid());
        assertEquals(3, result.needsAction());

        CommercialPipelineService.PipelineItem firstRow = result.items().get(0);
        assertEquals("Sin ficha", firstRow.customerName());
        assertEquals("Producto fallback", firstRow.product());
        assertEquals(itemVariantId, firstRow.variantId());
        assertNull(firstRow.variant());
        assertEquals("FAILED", firstRow.paymentStatus());
        assertNull(firstRow.channel());

        CommercialPipelineService.PipelineItem secondRow = result.items().get(1);
        assertEquals("Contacto original", secondRow.customerName());
        assertEquals("VOICE", secondRow.channel());
        assertEquals("PAYMENT_FAILED", secondRow.commercialStage());
        assertNull(secondRow.orderStatus());
        assertNull(secondRow.inventoryStatus());
        assertNull(secondRow.outboundStatus());

        CommercialPipelineService.PipelineItem thirdRow = result.items().get(2);
        assertEquals("REQUIRES_ACTION", thirdRow.paymentStatus());
        assertEquals("WHATSAPP", thirdRow.channel());
        assertNull(thirdRow.orderStatus());
        assertNull(thirdRow.variantId());
    }

}
