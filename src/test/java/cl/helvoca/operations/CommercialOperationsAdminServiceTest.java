package cl.helvoca.operations;

import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
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

        CommercialOperationsAdminService service = new CommercialOperationsAdminService(
                orders, lines, mock(BusinessQuoteRepository.class), mock(BusinessLeadRepository.class), tenant);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.updateOrderStatus(orderId, BusinessOrder.Status.COMPLETED));

        assertTrue(error.getMessage().contains("CONFIRMED -> COMPLETED"));
        verify(orders, never()).saveAndFlush(any());
    }

    @Test
    void readyDeliveryCanAdvanceToDispatched() {
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

        CommercialOperationsAdminService service = new CommercialOperationsAdminService(
                orders, lines, mock(BusinessQuoteRepository.class), mock(BusinessLeadRepository.class), tenant);

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

        CommercialOperationsAdminService service = new CommercialOperationsAdminService(
                orders, mock(BusinessOrderLineRepository.class), mock(BusinessQuoteRepository.class),
                mock(BusinessLeadRepository.class), tenant);

        assertThrows(IllegalArgumentException.class,
                () -> service.updateOrderStatus(orderId, BusinessOrder.Status.PREPARING));
        verify(orders, never()).saveAndFlush(any());
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
