package cl.helvoca.operations;

import cl.helvoca.delivery.BusinessDelivery;
import cl.helvoca.delivery.BusinessDeliveryRepository;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CommercialOperationsAdminServiceTest {

    @Test
    void confirmedOrderCannotJumpDirectlyToCompleted() {
        UUID businessId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        BusinessOrderRepository orders = mock(BusinessOrderRepository.class);
        BusinessOrderLineRepository lines = mock(BusinessOrderLineRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);
        BusinessOrder order = order(orderId, businessId, BusinessOrder.Status.CONFIRMED, BusinessOrder.FulfillmentType.PICKUP);

        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(orders.findByIdAndBusinessId(orderId, businessId)).thenReturn(Optional.of(order));

        CommercialOperationsAdminService service = service(orders, lines, tenant,
                mock(BusinessDeliveryRepository.class), mock(BusinessOperationRepository.class));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.updateOrderStatus(orderId, BusinessOrder.Status.COMPLETED));

        assertTrue(error.getMessage().contains("CONFIRMED -> COMPLETED"));
        verify(orders, never()).saveAndFlush(any());
    }

    @Test
    void readyDeliveryOrderCanAdvanceToDispatched() {
        UUID businessId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        BusinessOrderRepository orders = mock(BusinessOrderRepository.class);
        BusinessOrderLineRepository lines = mock(BusinessOrderLineRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);
        BusinessOrder order = order(orderId, businessId, BusinessOrder.Status.READY, BusinessOrder.FulfillmentType.DELIVERY);

        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(orders.findByIdAndBusinessId(orderId, businessId)).thenReturn(Optional.of(order));
        when(orders.saveAndFlush(any(BusinessOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(lines.findAllByOrderIdOrderByCreatedAtAsc(orderId)).thenReturn(List.of());

        CommercialOperationsAdminService service = service(orders, lines, tenant,
                mock(BusinessDeliveryRepository.class), mock(BusinessOperationRepository.class));

        var view = service.updateOrderStatus(orderId, BusinessOrder.Status.DISPATCHED);

        assertEquals(BusinessOrder.Status.DISPATCHED, view.status());
        verify(orders).saveAndFlush(order);
    }

    @Test
    void cancelledOrderIsTerminal() {
        UUID businessId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        BusinessOrderRepository orders = mock(BusinessOrderRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);
        BusinessOrder order = order(orderId, businessId, BusinessOrder.Status.CANCELLED, BusinessOrder.FulfillmentType.PICKUP);

        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(orders.findByIdAndBusinessId(orderId, businessId)).thenReturn(Optional.of(order));

        CommercialOperationsAdminService service = service(
                orders,
                mock(BusinessOrderLineRepository.class),
                tenant,
                mock(BusinessDeliveryRepository.class),
                mock(BusinessOperationRepository.class));

        assertThrows(IllegalArgumentException.class,
                () -> service.updateOrderStatus(orderId, BusinessOrder.Status.PREPARING));
        verify(orders, never()).saveAndFlush(any());
    }

    @Test
    void confirmedStandaloneDeliveryCanAdvanceToInTransitAndSyncUniversalOperation() {
        UUID businessId = UUID.randomUUID();
        UUID deliveryId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        BusinessDeliveryRepository deliveries = mock(BusinessDeliveryRepository.class);
        BusinessOperationRepository operations = mock(BusinessOperationRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);

        BusinessDelivery delivery = new BusinessDelivery();
        delivery.setId(deliveryId);
        delivery.setOperationId(operationId);
        delivery.setBusinessId(businessId);
        delivery.setDeliveryZoneId(UUID.randomUUID());
        delivery.setDeliveryAddress("Apoquindo 3000");
        delivery.setStatus(BusinessDelivery.Status.CONFIRMED);
        delivery.setSource(BusinessOrder.Source.VOICE);

        BusinessOperation operation = new BusinessOperation();
        operation.setId(operationId);
        operation.setBusinessId(businessId);
        operation.setType(BusinessOperation.Type.DELIVERY);
        operation.setStatus(BusinessOperation.Status.CONFIRMED);
        operation.setRevision(1);
        operation.setMetadata(Map.of("intent", "DELIVERY"));

        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(deliveries.findByIdAndBusinessId(deliveryId, businessId)).thenReturn(Optional.of(delivery));
        when(deliveries.saveAndFlush(any(BusinessDelivery.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(operations.findByIdAndBusinessId(operationId, businessId)).thenReturn(Optional.of(operation));
        when(operations.saveAndFlush(any(BusinessOperation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CommercialOperationsAdminService service = service(
                mock(BusinessOrderRepository.class),
                mock(BusinessOrderLineRepository.class),
                tenant,
                deliveries,
                operations);

        var view = service.updateDeliveryStatus(deliveryId, BusinessDelivery.Status.IN_TRANSIT);

        assertEquals(BusinessDelivery.Status.IN_TRANSIT, view.status());
        assertEquals(BusinessOperation.Status.CONFIRMED, operation.getStatus());
        assertEquals(2, operation.getRevision());
        assertEquals("IN_TRANSIT", operation.getMetadata().get("projectionStatus"));
        verify(deliveries).saveAndFlush(delivery);
        verify(operations).saveAndFlush(operation);
    }

    @Test
    void readyQuoteCanBeAcceptedAndCompletesUniversalOperation() {
        UUID businessId = UUID.randomUUID();
        UUID quoteId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        BusinessQuoteRepository quotes = mock(BusinessQuoteRepository.class);
        BusinessOperationRepository operations = mock(BusinessOperationRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);

        BusinessQuote quote = new BusinessQuote();
        quote.setId(quoteId);
        quote.setOperationId(operationId);
        quote.setBusinessId(businessId);
        quote.setTitle("Cotización");
        quote.setStatus(BusinessQuote.Status.READY);
        quote.setSource(BusinessOrder.Source.API);

        BusinessOperation operation = new BusinessOperation();
        operation.setId(operationId);
        operation.setBusinessId(businessId);
        operation.setType(BusinessOperation.Type.QUOTE);
        operation.setStatus(BusinessOperation.Status.CONFIRMED);
        operation.setRevision(2);
        operation.setMetadata(Map.of("intent", "QUOTE"));

        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(quotes.findByIdAndBusinessId(quoteId, businessId)).thenReturn(Optional.of(quote));
        when(quotes.saveAndFlush(any(BusinessQuote.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(operations.findByIdAndBusinessId(operationId, businessId)).thenReturn(Optional.of(operation));
        when(operations.saveAndFlush(any(BusinessOperation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CommercialOperationsAdminService service = new CommercialOperationsAdminService(
                mock(BusinessOrderRepository.class),
                mock(BusinessOrderLineRepository.class),
                quotes,
                mock(BusinessLeadRepository.class),
                mock(BusinessDeliveryRepository.class),
                operations,
                tenant);

        var view = service.updateQuoteStatus(quoteId, BusinessQuote.Status.ACCEPTED);

        assertEquals(BusinessQuote.Status.ACCEPTED, view.status());
        assertEquals(BusinessOperation.Status.COMPLETED, operation.getStatus());
        assertEquals(3, operation.getRevision());
        assertEquals("ACCEPTED", operation.getMetadata().get("projectionStatus"));
        assertEquals(quoteId.toString(), operation.getMetadata().get("quoteId"));
        verify(quotes).saveAndFlush(quote);
        verify(operations).saveAndFlush(operation);
    }

    @Test
    void acceptedQuoteIsTerminal() {
        UUID businessId = UUID.randomUUID();
        UUID quoteId = UUID.randomUUID();
        BusinessQuoteRepository quotes = mock(BusinessQuoteRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);
        BusinessQuote quote = new BusinessQuote();
        quote.setId(quoteId);
        quote.setBusinessId(businessId);
        quote.setTitle("Cotización");
        quote.setStatus(BusinessQuote.Status.ACCEPTED);

        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(quotes.findByIdAndBusinessId(quoteId, businessId)).thenReturn(Optional.of(quote));

        CommercialOperationsAdminService service = new CommercialOperationsAdminService(
                mock(BusinessOrderRepository.class),
                mock(BusinessOrderLineRepository.class),
                quotes,
                mock(BusinessLeadRepository.class),
                mock(BusinessDeliveryRepository.class),
                mock(BusinessOperationRepository.class),
                tenant);

        assertThrows(IllegalArgumentException.class,
                () -> service.updateQuoteStatus(quoteId, BusinessQuote.Status.READY));
        verify(quotes, never()).saveAndFlush(any());
    }

    @Test
    void qualifiedLeadCanBeWonAndCompletesUniversalOperation() {
        UUID businessId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        BusinessLeadRepository leads = mock(BusinessLeadRepository.class);
        BusinessOperationRepository operations = mock(BusinessOperationRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);

        BusinessLead lead = new BusinessLead();
        lead.setId(leadId);
        lead.setOperationId(operationId);
        lead.setBusinessId(businessId);
        lead.setName("Cliente");
        lead.setInterest("Servicio");
        lead.setStatus(BusinessLead.Status.QUALIFIED);
        lead.setSource(BusinessOrder.Source.API);

        BusinessOperation operation = new BusinessOperation();
        operation.setId(operationId);
        operation.setBusinessId(businessId);
        operation.setType(BusinessOperation.Type.LEAD);
        operation.setStatus(BusinessOperation.Status.CONFIRMED);
        operation.setRevision(3);
        operation.setMetadata(Map.of("intent", "LEAD"));

        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(leads.findByIdAndBusinessId(leadId, businessId)).thenReturn(Optional.of(lead));
        when(leads.saveAndFlush(any(BusinessLead.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(operations.findByIdAndBusinessId(operationId, businessId)).thenReturn(Optional.of(operation));
        when(operations.saveAndFlush(any(BusinessOperation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CommercialOperationsAdminService service = new CommercialOperationsAdminService(
                mock(BusinessOrderRepository.class),
                mock(BusinessOrderLineRepository.class),
                mock(BusinessQuoteRepository.class),
                leads,
                mock(BusinessDeliveryRepository.class),
                operations,
                tenant);

        var view = service.updateLeadStatus(leadId, BusinessLead.Status.WON);

        assertEquals(BusinessLead.Status.WON, view.status());
        assertEquals(BusinessOperation.Status.COMPLETED, operation.getStatus());
        assertEquals(4, operation.getRevision());
        assertEquals("WON", operation.getMetadata().get("projectionStatus"));
        assertEquals(leadId.toString(), operation.getMetadata().get("leadId"));
        verify(leads).saveAndFlush(lead);
        verify(operations).saveAndFlush(operation);
    }

    @Test
    void wonLeadIsTerminal() {
        UUID businessId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();
        BusinessLeadRepository leads = mock(BusinessLeadRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);
        BusinessLead lead = new BusinessLead();
        lead.setId(leadId);
        lead.setBusinessId(businessId);
        lead.setStatus(BusinessLead.Status.WON);

        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(leads.findByIdAndBusinessId(leadId, businessId)).thenReturn(Optional.of(lead));

        CommercialOperationsAdminService service = new CommercialOperationsAdminService(
                mock(BusinessOrderRepository.class),
                mock(BusinessOrderLineRepository.class),
                mock(BusinessQuoteRepository.class),
                leads,
                mock(BusinessDeliveryRepository.class),
                mock(BusinessOperationRepository.class),
                tenant);

        assertThrows(IllegalArgumentException.class,
                () -> service.updateLeadStatus(leadId, BusinessLead.Status.CONTACTED));
        verify(leads, never()).saveAndFlush(any());
    }

    @Test
    void deliveredStandaloneDeliveryIsTerminal() {
        UUID businessId = UUID.randomUUID();
        UUID deliveryId = UUID.randomUUID();
        BusinessDeliveryRepository deliveries = mock(BusinessDeliveryRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);
        BusinessDelivery delivery = new BusinessDelivery();
        delivery.setId(deliveryId);
        delivery.setBusinessId(businessId);
        delivery.setStatus(BusinessDelivery.Status.DELIVERED);

        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(deliveries.findByIdAndBusinessId(deliveryId, businessId)).thenReturn(Optional.of(delivery));

        CommercialOperationsAdminService service = service(
                mock(BusinessOrderRepository.class),
                mock(BusinessOrderLineRepository.class),
                tenant,
                deliveries,
                mock(BusinessOperationRepository.class));

        assertThrows(IllegalArgumentException.class,
                () -> service.updateDeliveryStatus(deliveryId, BusinessDelivery.Status.CANCELLED));
        verify(deliveries, never()).saveAndFlush(any());
    }

    private static CommercialOperationsAdminService service(BusinessOrderRepository orders,
                                                            BusinessOrderLineRepository lines,
                                                            TenantProvider tenant,
                                                            BusinessDeliveryRepository deliveries,
                                                            BusinessOperationRepository operations) {
        return new CommercialOperationsAdminService(
                orders,
                lines,
                mock(BusinessQuoteRepository.class),
                mock(BusinessLeadRepository.class),
                deliveries,
                operations,
                tenant);
    }

    private static BusinessOrder order(UUID id,
                                       UUID businessId,
                                       BusinessOrder.Status status,
                                       BusinessOrder.FulfillmentType fulfillment) {
        BusinessOrder order = new BusinessOrder();
        order.setId(id);
        order.setBusinessId(businessId);
        order.setStatus(status);
        order.setFulfillmentType(fulfillment);
        return order;
    }
}
