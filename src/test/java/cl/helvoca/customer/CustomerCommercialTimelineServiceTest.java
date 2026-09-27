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
}
