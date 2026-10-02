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
        UUID operationId = UUID.randomUUID();
        order.setOperationId(operationId);
        BusinessOperationRepository operations = mock(BusinessOperationRepository.class);
        BusinessOperation operation = new BusinessOperation();
        operation.setId(operationId);
        operation.setBusinessId(businessId);
        operation.setType(BusinessOperation.Type.ORDER);
        operation.setStatus(BusinessOperation.Status.CONFIRMED);
        operation.setRevision(2);
        operation.setMetadata(Map.of("intent", "ORDER"));

        when(orders.findByIdAndBusinessId(orderId, businessId)).thenReturn(Optional.of(order));
        when(orders.saveAndFlush(any(BusinessOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(lines.findAllByOrderIdOrderByCreatedAtAsc(orderId)).thenReturn(List.of());
        when(operations.findByIdAndBusinessId(operationId, businessId)).thenReturn(Optional.of(operation));
        when(operations.saveAndFlush(any(BusinessOperation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CommercialOperationsAdminService service = service(orders, lines, tenant,
                mock(BusinessDeliveryRepository.class), operations);

        var view = service.updateOrderStatus(orderId, BusinessOrder.Status.DISPATCHED);

        assertEquals(BusinessOrder.Status.DISPATCHED, view.status());
        assertEquals(BusinessOperation.Status.CONFIRMED, operation.getStatus());
        assertEquals(3, operation.getRevision());
        assertEquals("DISPATCHED", operation.getMetadata().get("projectionStatus"));
        assertEquals(orderId.toString(), operation.getMetadata().get("orderId"));
        verify(orders).saveAndFlush(order);
        verify(operations).saveAndFlush(operation);
    }

    @Test
    void readyPickupOrderCanCompleteAndClosesUniversalOperation() {
        UUID businessId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        BusinessOrderRepository orders = mock(BusinessOrderRepository.class);
        BusinessOrderLineRepository lines = mock(BusinessOrderLineRepository.class);
        BusinessOperationRepository operations = mock(BusinessOperationRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);

        BusinessOrder order = order(
                orderId,
                businessId,
                BusinessOrder.Status.READY,
                BusinessOrder.FulfillmentType.PICKUP);
        order.setOperationId(operationId);

        BusinessOperation operation = new BusinessOperation();
        operation.setId(operationId);
        operation.setBusinessId(businessId);
        operation.setType(BusinessOperation.Type.ORDER);
        operation.setStatus(BusinessOperation.Status.CONFIRMED);
        operation.setRevision(4);
        operation.setMetadata(Map.of("intent", "ORDER"));

        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(orders.findByIdAndBusinessId(orderId, businessId)).thenReturn(Optional.of(order));
        when(orders.saveAndFlush(any(BusinessOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(lines.findAllByOrderIdOrderByCreatedAtAsc(orderId)).thenReturn(List.of());
        when(operations.findByIdAndBusinessId(operationId, businessId)).thenReturn(Optional.of(operation));
        when(operations.saveAndFlush(any(BusinessOperation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CommercialOperationsAdminService service = service(
                orders,
                lines,
                tenant,
                mock(BusinessDeliveryRepository.class),
                operations);

        var view = service.updateOrderStatus(orderId, BusinessOrder.Status.COMPLETED);

        assertEquals(BusinessOrder.Status.COMPLETED, view.status());
        assertEquals(BusinessOperation.Status.COMPLETED, operation.getStatus());
        assertEquals(5, operation.getRevision());
        assertEquals("COMPLETED", operation.getMetadata().get("projectionStatus"));
        assertEquals(orderId.toString(), operation.getMetadata().get("orderId"));
        verify(orders).saveAndFlush(order);
        verify(operations).saveAndFlush(operation);
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
    void inTransitDeliveryCanBeDeliveredAndCompletesUniversalOperation() {
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
        delivery.setStatus(BusinessDelivery.Status.IN_TRANSIT);
        delivery.setSource(BusinessOrder.Source.VOICE);

        BusinessOperation operation = new BusinessOperation();
        operation.setId(operationId);
        operation.setBusinessId(businessId);
        operation.setType(BusinessOperation.Type.DELIVERY);
        operation.setStatus(BusinessOperation.Status.CONFIRMED);
        operation.setRevision(2);
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

        var view = service.updateDeliveryStatus(deliveryId, BusinessDelivery.Status.DELIVERED);

        assertEquals(BusinessDelivery.Status.DELIVERED, view.status());
        assertEquals(BusinessOperation.Status.COMPLETED, operation.getStatus());
        assertEquals(3, operation.getRevision());
        assertEquals("DELIVERED", operation.getMetadata().get("projectionStatus"));
        assertEquals(deliveryId.toString(), operation.getMetadata().get("deliveryId"));
        verify(deliveries).saveAndFlush(delivery);
        verify(operations).saveAndFlush(operation);
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

    @Test
    void preparationPermissionCanAdvanceConfirmedOrderToPreparing() {
        UUID businessId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        BusinessOrderRepository orders = mock(BusinessOrderRepository.class);
        BusinessOrderLineRepository lines = mock(BusinessOrderLineRepository.class);
        BusinessOperationRepository operations = mock(BusinessOperationRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);

        BusinessOrder order = order(
                orderId,
                businessId,
                BusinessOrder.Status.CONFIRMED,
                BusinessOrder.FulfillmentType.PICKUP);
        order.setOperationId(operationId);

        BusinessOperation operation = new BusinessOperation();
        operation.setId(operationId);
        operation.setBusinessId(businessId);
        operation.setType(BusinessOperation.Type.ORDER);
        operation.setStatus(BusinessOperation.Status.CONFIRMED);
        operation.setRevision(1);
        operation.setMetadata(Map.of("intent", "ORDER"));

        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(orders.findByIdAndBusinessId(orderId, businessId)).thenReturn(Optional.of(order));
        when(orders.saveAndFlush(any(BusinessOrder.class))).thenAnswer(i -> i.getArgument(0));
        when(lines.findAllByOrderIdOrderByCreatedAtAsc(orderId)).thenReturn(List.of());
        when(operations.findByIdAndBusinessId(operationId, businessId)).thenReturn(Optional.of(operation));
        when(operations.saveAndFlush(any(BusinessOperation.class))).thenAnswer(i -> i.getArgument(0));

        CommercialOperationsAdminService service = service(
                orders, lines, tenant, mock(BusinessDeliveryRepository.class), operations);

        var view = service.updateOrderPreparationStatus(orderId, BusinessOrder.Status.PREPARING);

        assertEquals(BusinessOrder.Status.PREPARING, view.status());
        assertEquals("PREPARING", operation.getMetadata().get("projectionStatus"));
        verify(orders).saveAndFlush(order);
    }

    @Test
    void preparationPermissionCannotCancelOrCompleteOrders() {
        TenantProvider tenant = mock(TenantProvider.class);
        CommercialOperationsAdminService service = service(
                mock(BusinessOrderRepository.class),
                mock(BusinessOrderLineRepository.class),
                tenant,
                mock(BusinessDeliveryRepository.class),
                mock(BusinessOperationRepository.class));

        UUID orderId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,
                () -> service.updateOrderPreparationStatus(orderId, BusinessOrder.Status.CANCELLED));
        assertThrows(IllegalArgumentException.class,
                () -> service.updateOrderPreparationStatus(orderId, BusinessOrder.Status.COMPLETED));

        verifyNoInteractions(tenant);
    }


    @Test
    void deliveryQuoteAndLeadListsUseBoundedRepositoryQueries() {
        UUID businessId = UUID.randomUUID();
        TenantProvider tenant = mock(TenantProvider.class);
        BusinessDeliveryRepository deliveries = mock(BusinessDeliveryRepository.class);
        BusinessQuoteRepository quotes = mock(BusinessQuoteRepository.class);
        BusinessLeadRepository leads = mock(BusinessLeadRepository.class);

        BusinessDelivery delivery = new BusinessDelivery();
        delivery.setId(UUID.randomUUID());
        delivery.setBusinessId(businessId);
        delivery.setStatus(BusinessDelivery.Status.CONFIRMED);
        delivery.setSource(BusinessOrder.Source.API);

        BusinessQuote quote = new BusinessQuote();
        quote.setId(UUID.randomUUID());
        quote.setBusinessId(businessId);
        quote.setTitle("Cotización");
        quote.setStatus(BusinessQuote.Status.REQUESTED);
        quote.setSource(BusinessOrder.Source.API);

        BusinessLead lead = new BusinessLead();
        lead.setId(UUID.randomUUID());
        lead.setBusinessId(businessId);
        lead.setName("Cliente");
        lead.setInterest("Taladro");
        lead.setStatus(BusinessLead.Status.NEW);
        lead.setSource(BusinessOrder.Source.API);

        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(deliveries.findTop100ByBusinessIdOrderByCreatedAtDesc(businessId)).thenReturn(List.of(delivery));
        when(quotes.findTop100ByBusinessIdOrderByCreatedAtDesc(businessId)).thenReturn(List.of(quote));
        when(leads.findTop100ByBusinessIdOrderByCreatedAtDesc(businessId)).thenReturn(List.of(lead));

        CommercialOperationsAdminService service = new CommercialOperationsAdminService(
                mock(BusinessOrderRepository.class),
                mock(BusinessOrderLineRepository.class),
                quotes,
                leads,
                deliveries,
                mock(BusinessOperationRepository.class),
                tenant);

        assertEquals(1, service.deliveries().size());
        assertEquals(1, service.quotes().size());
        assertEquals(1, service.leads().size());

        verify(deliveries).findTop100ByBusinessIdOrderByCreatedAtDesc(businessId);
        verify(quotes).findTop100ByBusinessIdOrderByCreatedAtDesc(businessId);
        verify(leads).findTop100ByBusinessIdOrderByCreatedAtDesc(businessId);
        verify(deliveries, never()).findAllByBusinessIdOrderByCreatedAtDesc(any());
        verify(quotes, never()).findAllByBusinessIdOrderByCreatedAtDesc(any());
        verify(leads, never()).findAllByBusinessIdOrderByCreatedAtDesc(any());
    }

    @Test
    void ordersUsesBoundedRepositoryQueryAndBatchLoadsLines() {
        UUID businessId = UUID.randomUUID();
        UUID firstOrderId = UUID.randomUUID();
        UUID secondOrderId = UUID.randomUUID();
        BusinessOrderRepository orders = mock(BusinessOrderRepository.class);
        BusinessOrderLineRepository lines = mock(BusinessOrderLineRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);

        BusinessOrder first = order(
                firstOrderId,
                businessId,
                BusinessOrder.Status.CONFIRMED,
                BusinessOrder.FulfillmentType.PICKUP);
        BusinessOrder second = order(
                secondOrderId,
                businessId,
                BusinessOrder.Status.PREPARING,
                BusinessOrder.FulfillmentType.DELIVERY);

        BusinessOrderLine firstLine = line(firstOrderId, "Martillo");
        BusinessOrderLine secondLine = line(secondOrderId, "Taladro");

        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(orders.findTop100ByBusinessIdOrderByCreatedAtDesc(businessId))
                .thenReturn(List.of(first, second));
        when(lines.findAllByOrderIdInOrderByCreatedAtAsc(anyCollection()))
                .thenReturn(List.of(firstLine, secondLine));

        CommercialOperationsAdminService service = service(
                orders,
                lines,
                tenant,
                mock(BusinessDeliveryRepository.class),
                mock(BusinessOperationRepository.class));

        var result = service.orders();

        assertEquals(2, result.size());
        assertEquals(List.of("Martillo"), result.get(0).lines().stream()
                .map(CommercialOperationsAdminService.OrderLineView::name)
                .toList());
        assertEquals(List.of("Taladro"), result.get(1).lines().stream()
                .map(CommercialOperationsAdminService.OrderLineView::name)
                .toList());

        verify(orders).findTop100ByBusinessIdOrderByCreatedAtDesc(businessId);
        verify(orders, never()).findAllByBusinessIdOrderByCreatedAtDesc(any());
        verify(lines).findAllByOrderIdInOrderByCreatedAtAsc(anyCollection());
        verify(lines, never()).findAllByOrderIdOrderByCreatedAtAsc(any());
    }

    private static BusinessOrderLine line(UUID orderId, String name) {
        BusinessOrderLine line = new BusinessOrderLine();
        line.setId(UUID.randomUUID());
        line.setOrderId(orderId);
        line.setBusinessId(UUID.randomUUID());
        line.setCatalogItemId(UUID.randomUUID());
        line.setItemName(name);
        line.setQuantity(1);
        line.setUnitPrice(java.math.BigDecimal.valueOf(1000));
        line.setLineTotal(java.math.BigDecimal.valueOf(1000));
        return line;
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
