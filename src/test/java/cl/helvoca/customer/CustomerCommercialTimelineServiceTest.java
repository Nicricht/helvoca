package cl.helvoca.customer;

import cl.helvoca.call.CallSession;
import cl.helvoca.call.CallSessionRepository;
import cl.helvoca.inventory.InventoryProductVariant;
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
import cl.helvoca.operations.BusinessOrderLineRepository;
import cl.helvoca.operations.BusinessOrderRepository;
import cl.helvoca.payment.BusinessPayment;
import cl.helvoca.payment.BusinessPaymentRepository;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerCommercialTimelineServiceTest {

    @Test
    void aggregatesCrossChannelCommerceIntoOneTenantScopedTimeline() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID journeyId = UUID.randomUUID();
        UUID orderOperationId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID paymentOperationId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();

        CustomerRepository customers = mock(CustomerRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);
        CallSessionRepository calls = mock(CallSessionRepository.class);
        MessagingConversationRepository conversations = mock(MessagingConversationRepository.class);
        BusinessOperationRepository operations = mock(BusinessOperationRepository.class);
        BusinessOperationItemRepository operationItems = mock(BusinessOperationItemRepository.class);
        BusinessOrderRepository orders = mock(BusinessOrderRepository.class);
        BusinessOrderLineRepository orderLines = mock(BusinessOrderLineRepository.class);
        BusinessPaymentRepository payments = mock(BusinessPaymentRepository.class);
        OutboundMessageRepository outbound = mock(OutboundMessageRepository.class);
        InventoryReservationRepository reservations = mock(InventoryReservationRepository.class);
        InventoryProductVariantRepository variants = mock(InventoryProductVariantRepository.class);

        Customer customer = mock(Customer.class);
        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(customers.findByIdAndBusinessId(customerId, businessId)).thenReturn(Optional.of(customer));

        CallSession call = mock(CallSession.class);
        when(call.getStartedAt()).thenReturn(Instant.parse("2026-09-26T13:00:00Z"));
        when(call.getStatus()).thenReturn(cl.helvoca.call.CallStatus.COMPLETED);
        when(call.getResolution()).thenReturn("PRODUCT_INQUIRY");
        when(calls.findTop20ByBusinessIdAndCustomerIdAndCertificationFalseOrderByStartedAtDesc(
                businessId, customerId)).thenReturn(List.of(call));

        MessagingConversation conversation = mock(MessagingConversation.class);
        when(conversation.getId()).thenReturn(UUID.randomUUID());
        when(conversation.getChannel()).thenReturn("WHATSAPP");
        when(conversation.getLastMessageAt()).thenReturn(Instant.parse("2026-09-26T13:05:00Z"));
        when(conversations.findTop20ByBusinessIdAndCustomerIdOrderByLastMessageAtDesc(
                businessId, customerId)).thenReturn(List.of(conversation));

        BusinessOperation journey = mock(BusinessOperation.class);
        when(journey.getId()).thenReturn(journeyId);
        when(journey.getType()).thenReturn(BusinessOperation.Type.REQUEST);
        when(journey.getStatus()).thenReturn(BusinessOperation.Status.COMPLETED);
        when(journey.getSource()).thenReturn(BusinessOrder.Source.VOICE);
        when(journey.getUpdatedAt()).thenReturn(Instant.parse("2026-09-26T13:10:00Z"));
        when(journey.getMetadata()).thenReturn(Map.of(
                "commercialStage", "PAID",
                "selectedVariantId", variantId.toString(),
                "orderOperationId", orderOperationId.toString(),
                "paymentOperationId", paymentOperationId.toString()));
        when(operations.findTop50ByBusinessIdAndCustomerIdOrderByUpdatedAtDesc(
                businessId, customerId)).thenReturn(List.of(journey));

        BusinessOperationItem item = mock(BusinessOperationItem.class);
        when(item.getItemName()).thenReturn("Zapatilla Urban");
        when(item.getVariantId()).thenReturn(variantId);
        when(operationItems.findAllByOperationIdOrderByCreatedAtAsc(journeyId))
                .thenReturn(List.of(item));

        InventoryProductVariant variant = mock(InventoryProductVariant.class);
        when(variant.getName()).thenReturn("Negro / 42");
        when(variants.findByIdAndBusinessId(variantId, businessId)).thenReturn(Optional.of(variant));

        BusinessOrder order = mock(BusinessOrder.class);
        when(order.getId()).thenReturn(orderId);
        when(order.getOperationId()).thenReturn(orderOperationId);
        when(order.getStatus()).thenReturn(BusinessOrder.Status.CONFIRMED);
        when(order.getSource()).thenReturn(BusinessOrder.Source.WHATSAPP);
        when(order.getCreatedAt()).thenReturn(Instant.parse("2026-09-26T13:12:00Z"));
        when(orders.findTop5ByBusinessIdAndCustomerIdOrderByCreatedAtDesc(businessId, customerId))
                .thenReturn(List.of(order));
        when(orderLines.findAllByOrderIdOrderByCreatedAtAsc(orderId)).thenReturn(List.of());

        BusinessPayment payment = mock(BusinessPayment.class);
        when(payment.getOperationId()).thenReturn(paymentOperationId);
        when(payment.getTargetOperationId()).thenReturn(orderOperationId);
        when(payment.getStatus()).thenReturn(BusinessPayment.Status.SUCCEEDED);
        when(payment.getSource()).thenReturn(BusinessOrder.Source.WHATSAPP);
        when(payment.getCreatedAt()).thenReturn(Instant.parse("2026-09-26T13:15:00Z"));
        when(payments.findTop5ByBusinessIdAndCustomerIdOrderByCreatedAtDesc(businessId, customerId))
                .thenReturn(List.of(payment));

        InventoryReservation reservation = mock(InventoryReservation.class);
        when(reservation.getStatus()).thenReturn(InventoryReservation.Status.CONSUMED);
        when(reservation.getVariantId()).thenReturn(variantId);
        when(reservation.getQuantity()).thenReturn(1);
        when(reservation.getUpdatedAt()).thenReturn(Instant.parse("2026-09-26T13:16:00Z"));
        when(reservations.findAllByBusinessIdAndReferenceTypeAndReferenceIdOrderByCreatedAtAsc(
                businessId, "ORDER_OPERATION", orderOperationId)).thenReturn(List.of(reservation));

        OutboundMessage confirmation = mock(OutboundMessage.class);
        when(confirmation.getOperationId()).thenReturn(paymentOperationId);
        when(confirmation.getPurpose()).thenReturn(OutboundMessage.Purpose.PAYMENT_CONFIRMATION);
        when(confirmation.getStatus()).thenReturn(OutboundMessage.Status.PREPARED);
        when(confirmation.getCreatedAt()).thenReturn(Instant.parse("2026-09-26T13:17:00Z"));
        when(outbound.findTop50ByBusinessIdAndCustomerIdOrderByCreatedAtDesc(
                businessId, customerId)).thenReturn(List.of(confirmation));

        CustomerCommercialTimelineService service = new CustomerCommercialTimelineService(
                customers, tenant, calls, conversations, operations, operationItems,
                orders, orderLines, payments, outbound, reservations, variants);

        CustomerCommercialTimelineService.TimelineResponse result = service.get(customerId);

        assertEquals("PAID", result.summary().commercialStage());
        assertEquals("Zapatilla Urban", result.summary().selectedProduct());
        assertEquals(variantId, result.summary().selectedVariantId());
        assertEquals("Negro / 42", result.summary().selectedVariant());
        assertEquals("CONFIRMED", result.summary().orderStatus());
        assertEquals("SUCCEEDED", result.summary().paymentStatus());
        assertEquals("CONSUMED", result.summary().inventoryStatus());
        assertEquals("WHATSAPP", result.summary().lastChannel());

        assertFalse(result.events().isEmpty());
        assertEquals("OUTBOUND", result.events().get(0).type());
        assertTrue(result.events().stream().anyMatch(event -> "CALL".equals(event.type())));
        assertTrue(result.events().stream().anyMatch(event -> "WHATSAPP".equals(event.type())));
        assertTrue(result.events().stream().anyMatch(event -> "PAYMENT".equals(event.type())));
        assertTrue(result.events().stream().anyMatch(event -> "INVENTORY".equals(event.type())));
    }

    @Test
    void rejectsCustomerOutsideAuthenticatedTenantBeforeReadingTimelineSources() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        CustomerRepository customers = mock(CustomerRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);
        CallSessionRepository calls = mock(CallSessionRepository.class);
        MessagingConversationRepository conversations = mock(MessagingConversationRepository.class);
        BusinessOperationRepository operations = mock(BusinessOperationRepository.class);
        BusinessOperationItemRepository operationItems = mock(BusinessOperationItemRepository.class);
        BusinessOrderRepository orders = mock(BusinessOrderRepository.class);
        BusinessOrderLineRepository orderLines = mock(BusinessOrderLineRepository.class);
        BusinessPaymentRepository payments = mock(BusinessPaymentRepository.class);
        OutboundMessageRepository outbound = mock(OutboundMessageRepository.class);
        InventoryReservationRepository reservations = mock(InventoryReservationRepository.class);
        InventoryProductVariantRepository variants = mock(InventoryProductVariantRepository.class);

        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(customers.findByIdAndBusinessId(customerId, businessId)).thenReturn(Optional.empty());

        CustomerCommercialTimelineService service = new CustomerCommercialTimelineService(
                customers, tenant, calls, conversations, operations, operationItems,
                orders, orderLines, payments, outbound, reservations, variants);

        assertThrows(cl.helvoca.common.NotFoundException.class, () -> service.get(customerId));
        verifyNoInteractions(calls, conversations, operations, operationItems, orders, orderLines,
                payments, outbound, reservations, variants);
    }
    @Test
    void coversTimelineFallbacksTitlesStatusesAndNullDates() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID orderOperationId = UUID.randomUUID();

        CustomerRepository customers = mock(CustomerRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);
        CallSessionRepository calls = mock(CallSessionRepository.class);
        MessagingConversationRepository conversations = mock(MessagingConversationRepository.class);
        BusinessOperationRepository operations = mock(BusinessOperationRepository.class);
        BusinessOperationItemRepository operationItems = mock(BusinessOperationItemRepository.class);
        BusinessOrderRepository orders = mock(BusinessOrderRepository.class);
        BusinessOrderLineRepository orderLines = mock(BusinessOrderLineRepository.class);
        BusinessPaymentRepository payments = mock(BusinessPaymentRepository.class);
        OutboundMessageRepository outbound = mock(OutboundMessageRepository.class);
        InventoryReservationRepository reservations = mock(InventoryReservationRepository.class);
        InventoryProductVariantRepository variants = mock(InventoryProductVariantRepository.class);

        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(customers.findByIdAndBusinessId(customerId, businessId))
                .thenReturn(Optional.of(mock(Customer.class)));

        CallSession nullDateCall = mock(CallSession.class);
        when(nullDateCall.getStartedAt()).thenReturn(null);
        when(nullDateCall.getResolution()).thenReturn(null);
        when(nullDateCall.getStatus()).thenReturn(null);
        when(calls.findTop20ByBusinessIdAndCustomerIdAndCertificationFalseOrderByStartedAtDesc(
                businessId, customerId)).thenReturn(List.of(nullDateCall));

        MessagingConversation conversation = mock(MessagingConversation.class);
        when(conversation.getLastMessageAt()).thenReturn(Instant.parse("2026-09-27T02:00:00Z"));
        when(conversation.getChannel()).thenReturn(" ");
        when(conversations.findTop20ByBusinessIdAndCustomerIdOrderByLastMessageAtDesc(
                businessId, customerId)).thenReturn(List.of(conversation));

        List<BusinessOperation> operationRows = new java.util.ArrayList<>();
        int index = 0;
        for (BusinessOperation.Type type : BusinessOperation.Type.values()) {
            BusinessOperation operation = mock(BusinessOperation.class);
            UUID operationId = UUID.randomUUID();
            when(operation.getId()).thenReturn(operationId);
            when(operation.getType()).thenReturn(type);
            when(operation.getSource()).thenReturn(index % 2 == 0 ? null : BusinessOrder.Source.WHATSAPP);
            when(operation.getUpdatedAt()).thenReturn(Instant.parse("2026-09-27T02:1" + index + ":00Z"));
            when(operation.getStatus()).thenReturn(index == 0 ? null : BusinessOperation.Status.CONFIRMED);
            when(operation.getMetadata()).thenReturn(index == 0
                    ? Map.of("selectedVariantId", "not-a-uuid")
                    : null);
            when(operationItems.findAllByOperationIdOrderByCreatedAtAsc(operationId))
                    .thenReturn(List.of());
            operationRows.add(operation);
            index++;
        }
        when(operations.findTop50ByBusinessIdAndCustomerIdOrderByUpdatedAtDesc(
                businessId, customerId)).thenReturn(operationRows);

        BusinessOrder order = mock(BusinessOrder.class);
        when(order.getId()).thenReturn(orderId);
        when(order.getOperationId()).thenReturn(orderOperationId);
        when(order.getStatus()).thenReturn(null);
        when(order.getSource()).thenReturn(null);
        when(order.getCreatedAt()).thenReturn(Instant.parse("2026-09-27T03:00:00Z"));
        when(orders.findTop5ByBusinessIdAndCustomerIdOrderByCreatedAtDesc(
                businessId, customerId)).thenReturn(List.of(order));
        when(orderLines.findAllByOrderIdOrderByCreatedAtAsc(orderId)).thenReturn(List.of());

        BusinessPayment payment = mock(BusinessPayment.class);
        when(payment.getOperationId()).thenReturn(UUID.randomUUID());
        when(payment.getStatus()).thenReturn(null);
        when(payment.getSource()).thenReturn(null);
        when(payment.getCreatedAt()).thenReturn(Instant.parse("2026-09-27T03:05:00Z"));
        when(payment.getAmount()).thenReturn(null);
        when(payment.getCurrency()).thenReturn(null);
        when(payments.findTop5ByBusinessIdAndCustomerIdOrderByCreatedAtDesc(
                businessId, customerId)).thenReturn(List.of(payment));

        List<InventoryReservation> inventoryRows = new java.util.ArrayList<>();
        int minute = 10;
        for (InventoryReservation.Status status : InventoryReservation.Status.values()) {
            InventoryReservation reservation = mock(InventoryReservation.class);
            when(reservation.getStatus()).thenReturn(status);
            when(reservation.getVariantId()).thenReturn(null);
            when(reservation.getQuantity()).thenReturn(status == InventoryReservation.Status.ACTIVE ? 1 : 2);
            when(reservation.getReferenceId()).thenReturn(orderOperationId);
            when(reservation.getUpdatedAt()).thenReturn(
                    Instant.parse("2026-09-27T03:" + minute + ":00Z"));
            inventoryRows.add(reservation);
            minute++;
        }
        when(reservations.findAllByBusinessIdAndReferenceTypeAndReferenceIdOrderByCreatedAtAsc(
                businessId, "ORDER_OPERATION", orderOperationId)).thenReturn(inventoryRows);

        List<OutboundMessage> outboundRows = new java.util.ArrayList<>();
        OutboundMessage.Status[] statuses = OutboundMessage.Status.values();
        int outboundMinute = 20;
        int statusIndex = 0;
        for (OutboundMessage.Purpose purpose : OutboundMessage.Purpose.values()) {
            OutboundMessage message = mock(OutboundMessage.class);
            when(message.getPurpose()).thenReturn(purpose);
            when(message.getStatus()).thenReturn(statuses[statusIndex % statuses.length]);
            when(message.getOperationId()).thenReturn(UUID.randomUUID());
            when(message.getCreatedAt()).thenReturn(
                    Instant.parse("2026-09-27T03:" + outboundMinute + ":00Z"));
            outboundRows.add(message);
            outboundMinute++;
            statusIndex++;
        }
        when(outbound.findTop50ByBusinessIdAndCustomerIdOrderByCreatedAtDesc(
                businessId, customerId)).thenReturn(outboundRows);

        CustomerCommercialTimelineService service = new CustomerCommercialTimelineService(
                customers, tenant, calls, conversations, operations, operationItems,
                orders, orderLines, payments, outbound, reservations, variants);

        CustomerCommercialTimelineService.TimelineResponse result = service.get(customerId);

        assertNull(result.summary().commercialStage());
        assertNull(result.summary().selectedProduct());
        assertNull(result.summary().selectedVariantId());
        assertNull(result.summary().selectedVariant());
        assertNull(result.summary().orderStatus());
        assertNull(result.summary().paymentStatus());
        assertEquals("EXPIRED", result.summary().inventoryStatus());
        assertEquals("WHATSAPP", result.summary().lastChannel());

        assertEquals(BusinessOperation.Type.values().length,
                result.events().stream().filter(event -> "COMMERCIAL".equals(event.type())).count());
        assertEquals(InventoryReservation.Status.values().length,
                result.events().stream().filter(event -> "INVENTORY".equals(event.type())).count());
        assertEquals(OutboundMessage.Purpose.values().length,
                result.events().stream().filter(event -> "OUTBOUND".equals(event.type())).count());
        assertTrue(result.events().stream().anyMatch(event ->
                "Actividad comercial".equals(event.title()) || "Pedido comercial".equals(event.title())));
        assertTrue(result.events().stream().anyMatch(event -> "Inventario reservado".equals(event.title())));
        assertTrue(result.events().stream().anyMatch(event -> "Inventario liberado".equals(event.title())));
        assertTrue(result.events().stream().anyMatch(event -> "Reserva de inventario expirada".equals(event.title())));
        assertTrue(result.events().stream().anyMatch(event -> "Productos enviados".equals(event.title())));
        assertFalse(result.events().stream().anyMatch(event -> "CALL".equals(event.type())));
    }

}
