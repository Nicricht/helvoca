package cl.helvoca.operations;

import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.payment.BusinessPayment;
import cl.helvoca.payment.BusinessPaymentRepository;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SalesAnalyticsServiceTest {

    @Test
    void aggregatesPaidOrdersProductsChannelsPeaksAndRecepVozImpactForCurrentTenant() {
        UUID businessId = UUID.randomUUID();
        UUID op1 = UUID.randomUUID();
        UUID op2 = UUID.randomUUID();
        UUID orderId1 = UUID.randomUUID();
        UUID orderId2 = UUID.randomUUID();

        BusinessRepository businesses = mock(BusinessRepository.class);
        BusinessPaymentRepository payments = mock(BusinessPaymentRepository.class);
        BusinessOrderRepository orders = mock(BusinessOrderRepository.class);
        BusinessOrderLineRepository lines = mock(BusinessOrderLineRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);

        when(tenant.requireBusinessId()).thenReturn(businessId);
        Business business = new Business();
        business.setTimezone("America/Santiago");
        when(businesses.findById(businessId)).thenReturn(Optional.of(business));

        Instant now = Instant.now();
        BusinessPayment p1 = payment(op1, new BigDecimal("25000"), "CLP", BusinessOrder.Source.WHATSAPP, now.minusSeconds(3600));
        BusinessPayment p2 = payment(op2, new BigDecimal("15000"), "CLP", BusinessOrder.Source.VOICE, now.minusSeconds(1800));
        BusinessPayment duplicate = payment(op1, new BigDecimal("25000"), "CLP", BusinessOrder.Source.WHATSAPP, now.minusSeconds(7200));
        BusinessPayment failed = payment(UUID.randomUUID(), new BigDecimal("99999"), "CLP", BusinessOrder.Source.API, now.minusSeconds(900));
        when(failed.getStatus()).thenReturn(BusinessPayment.Status.FAILED);

        BusinessPayment previous = payment(UUID.randomUUID(), new BigDecimal("20000"), "CLP", BusinessOrder.Source.MANUAL, now.minusSeconds(10 * 86400L));

        when(payments.findAllByBusinessIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                eq(businessId), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(p1, p2, duplicate, failed), List.of(previous));

        BusinessOrder o1 = order(orderId1, op1, BusinessOrder.Source.WHATSAPP, BusinessOrder.Status.COMPLETED, "CLP");
        BusinessOrder o2 = order(orderId2, op2, BusinessOrder.Source.VOICE, BusinessOrder.Status.COMPLETED, "CLP");
        BusinessOrder previousOrder = order(UUID.randomUUID(), previous.getTargetOperationId(), BusinessOrder.Source.MANUAL, BusinessOrder.Status.COMPLETED, "CLP");

        when(orders.findAllByBusinessIdAndOperationIdIn(eq(businessId), anyCollection()))
                .thenReturn(List.of(o1, o2), List.of(previousOrder));

        UUID burger = UUID.randomUUID();
        UUID fries = UUID.randomUUID();
        BusinessOrderLine burgerOne = line(orderId1, burger, "Hamburguesa Doble", 2, "10000", "20000");
        BusinessOrderLine burgerTwo = line(orderId2, burger, "Hamburguesa Doble", 1, "10000", "10000");
        BusinessOrderLine friesLine = line(orderId2, fries, "Papas Grandes", 1, "5000", "5000");
        when(lines.findAllByOrderIdInOrderByCreatedAtAsc(anyCollection()))
                .thenReturn(List.of(burgerOne, burgerTwo, friesLine));

        SalesAnalyticsService service = new SalesAnalyticsService(businesses, payments, orders, lines, tenant);
        SalesAnalyticsService.AnalyticsResponse result = service.analytics(7);

        assertEquals(7, result.days());
        assertEquals("America/Santiago", result.timezone());
        assertEquals("CLP", result.primaryCurrency());
        assertEquals(0, new BigDecimal("40000").compareTo(result.totalRevenue()));
        assertEquals(2, result.paidOrders());
        assertEquals(4, result.unitsSold());
        assertEquals(0, new BigDecimal("20000").compareTo(result.averageTicket()));
        assertEquals(100.0, result.revenueChangePercent());
        assertEquals(2, result.recepVozOrders());
        assertEquals(0, new BigDecimal("40000").compareTo(result.recepVozRevenue()));

        assertFalse(result.salesOverTime().isEmpty());
        assertEquals("Hamburguesa Doble", result.topProducts().getFirst().name());
        assertEquals(3, result.topProducts().getFirst().units());
        assertEquals(0, new BigDecimal("30000").compareTo(result.topProducts().getFirst().revenue()));

        assertEquals(2, result.channels().size());
        assertTrue(result.channels().stream().anyMatch(channel -> channel.channel().equals("WHATSAPP") && channel.orders() == 1));
        assertTrue(result.channels().stream().anyMatch(channel -> channel.channel().equals("VOICE") && channel.orders() == 1));
        assertNotNull(result.peakWeekday());
        assertNotNull(result.peakHour());
        assertFalse(result.insights().isEmpty());

        verify(payments, times(2)).findAllByBusinessIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                eq(businessId), any(Instant.class), any(Instant.class));
        verify(orders, times(2)).findAllByBusinessIdAndOperationIdIn(eq(businessId), anyCollection());
    }

    @Test
    void avoidsFalseCombinedRevenueWhenPeriodContainsMultipleCurrencies() {
        UUID businessId = UUID.randomUUID();
        UUID op1 = UUID.randomUUID();
        UUID op2 = UUID.randomUUID();

        BusinessRepository businesses = mock(BusinessRepository.class);
        BusinessPaymentRepository payments = mock(BusinessPaymentRepository.class);
        BusinessOrderRepository orders = mock(BusinessOrderRepository.class);
        BusinessOrderLineRepository lines = mock(BusinessOrderLineRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);

        when(tenant.requireBusinessId()).thenReturn(businessId);
        Business business = new Business();
        business.setTimezone("America/Santiago");
        when(businesses.findById(businessId)).thenReturn(Optional.of(business));

        Instant now = Instant.now();
        BusinessPayment clpPayment = payment(
                op1, new BigDecimal("10000"), "CLP", BusinessOrder.Source.WHATSAPP, now.minusSeconds(300));
        BusinessPayment usdPayment = payment(
                op2, new BigDecimal("20"), "USD", BusinessOrder.Source.API, now.minusSeconds(200));
        when(payments.findAllByBusinessIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                eq(businessId), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(clpPayment, usdPayment), List.of());

        BusinessOrder clp = order(UUID.randomUUID(), op1, BusinessOrder.Source.WHATSAPP, BusinessOrder.Status.COMPLETED, "CLP");
        BusinessOrder usd = order(UUID.randomUUID(), op2, BusinessOrder.Source.API, BusinessOrder.Status.COMPLETED, "USD");
        when(orders.findAllByBusinessIdAndOperationIdIn(eq(businessId), anyCollection()))
                .thenReturn(List.of(clp, usd), List.of());
        when(lines.findAllByOrderIdInOrderByCreatedAtAsc(anyCollection())).thenReturn(List.of());

        SalesAnalyticsService.AnalyticsResponse result =
                new SalesAnalyticsService(businesses, payments, orders, lines, tenant).analytics(30);

        assertNull(result.primaryCurrency());
        assertNull(result.totalRevenue());
        assertNull(result.averageTicket());
        assertNull(result.revenueChangePercent());
        assertEquals(2, result.currencyTotals().size());
        assertEquals(2, result.paidOrders());
    }

    @Test
    void clampsPeriodAndExcludesCancelledOrdersFromSalesTruth() {
        UUID businessId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();

        BusinessRepository businesses = mock(BusinessRepository.class);
        BusinessPaymentRepository payments = mock(BusinessPaymentRepository.class);
        BusinessOrderRepository orders = mock(BusinessOrderRepository.class);
        BusinessOrderLineRepository lines = mock(BusinessOrderLineRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);

        when(tenant.requireBusinessId()).thenReturn(businessId);
        Business business = new Business();
        business.setTimezone("Invalid/Timezone");
        when(businesses.findById(businessId)).thenReturn(Optional.of(business));

        BusinessPayment succeeded = payment(operationId, new BigDecimal("50000"), "CLP", BusinessOrder.Source.WHATSAPP, Instant.now());
        when(payments.findAllByBusinessIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                eq(businessId), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(succeeded), List.of());

        BusinessOrder cancelled = order(UUID.randomUUID(), operationId, BusinessOrder.Source.WHATSAPP, BusinessOrder.Status.CANCELLED, "CLP");
        when(orders.findAllByBusinessIdAndOperationIdIn(eq(businessId), anyCollection()))
                .thenReturn(List.of(cancelled), List.of());

        SalesAnalyticsService.AnalyticsResponse result =
                new SalesAnalyticsService(businesses, payments, orders, lines, tenant).analytics(9999);

        assertEquals(365, result.days());
        assertEquals("UTC", result.timezone());
        assertEquals(0, result.paidOrders());
        assertEquals(0, result.unitsSold());
        assertEquals(0, result.currencyTotals().size());
        verify(lines, never()).findAllByOrderIdInOrderByCreatedAtAsc(anyCollection());
    }

    @Test
    void reportsNegativeTrendAndHandlesSparseProductLinesWithoutInventingValues() {
        UUID businessId = UUID.randomUUID();
        UUID currentOperation = UUID.randomUUID();
        UUID previousOperation = UUID.randomUUID();
        UUID currentOrderId = UUID.randomUUID();

        BusinessRepository businesses = mock(BusinessRepository.class);
        BusinessPaymentRepository payments = mock(BusinessPaymentRepository.class);
        BusinessOrderRepository orders = mock(BusinessOrderRepository.class);
        BusinessOrderLineRepository lines = mock(BusinessOrderLineRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);

        when(tenant.requireBusinessId()).thenReturn(businessId);
        Business business = new Business();
        business.setTimezone("America/Santiago");
        when(businesses.findById(businessId)).thenReturn(Optional.of(business));

        Instant now = Instant.now();
        BusinessPayment currentPayment = payment(
                currentOperation, new BigDecimal("10000"), "CLP", BusinessOrder.Source.MANUAL, now.minusSeconds(600));
        BusinessPayment previousPayment = payment(
                previousOperation, new BigDecimal("20000"), "CLP", BusinessOrder.Source.MANUAL, now.minusSeconds(10 * 86400L));
        when(payments.findAllByBusinessIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                eq(businessId), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(currentPayment), List.of(previousPayment));

        BusinessOrder currentOrder = order(
                currentOrderId, currentOperation, BusinessOrder.Source.MANUAL, BusinessOrder.Status.COMPLETED, "CLP");
        BusinessOrder previousOrder = order(
                UUID.randomUUID(), previousOperation, BusinessOrder.Source.MANUAL, BusinessOrder.Status.COMPLETED, "CLP");
        when(orders.findAllByBusinessIdAndOperationIdIn(eq(businessId), anyCollection()))
                .thenReturn(List.of(currentOrder), List.of(previousOrder));

        BusinessOrderLine sparse = mock(BusinessOrderLine.class);
        when(sparse.getOrderId()).thenReturn(currentOrderId);
        when(sparse.getCatalogItemId()).thenReturn(null);
        when(sparse.getItemName()).thenReturn(null);
        when(sparse.getQuantity()).thenReturn(null);
        when(sparse.getLineTotal()).thenReturn(null);
        when(lines.findAllByOrderIdInOrderByCreatedAtAsc(anyCollection())).thenReturn(List.of(sparse));

        SalesAnalyticsService.AnalyticsResponse result =
                new SalesAnalyticsService(businesses, payments, orders, lines, tenant).analytics(7);

        assertEquals(-50.0, result.revenueChangePercent());
        assertEquals(0, result.unitsSold());
        assertEquals("Producto sin nombre", result.topProducts().getFirst().name());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.topProducts().getFirst().revenue()));
        assertEquals(0, result.recepVozOrders());
        assertTrue(result.insights().stream()
                .anyMatch(insight -> insight.text().contains("bajaron 50%")));
    }

    private static BusinessPayment payment(UUID targetOperationId,
                                           BigDecimal amount,
                                           String currency,
                                           BusinessOrder.Source source,
                                           Instant createdAt) {
        BusinessPayment payment = mock(BusinessPayment.class);
        when(payment.getTargetOperationId()).thenReturn(targetOperationId);
        when(payment.getAmount()).thenReturn(amount);
        when(payment.getCurrency()).thenReturn(currency);
        when(payment.getSource()).thenReturn(source);
        when(payment.getStatus()).thenReturn(BusinessPayment.Status.SUCCEEDED);
        when(payment.getCreatedAt()).thenReturn(createdAt);
        when(payment.getUpdatedAt()).thenReturn(createdAt);
        return payment;
    }

    private static BusinessOrder order(UUID id,
                                       UUID operationId,
                                       BusinessOrder.Source source,
                                       BusinessOrder.Status status,
                                       String currency) {
        BusinessOrder order = mock(BusinessOrder.class);
        when(order.getId()).thenReturn(id);
        when(order.getOperationId()).thenReturn(operationId);
        when(order.getSource()).thenReturn(source);
        when(order.getStatus()).thenReturn(status);
        when(order.getCurrency()).thenReturn(currency);
        return order;
    }

    private static BusinessOrderLine line(UUID orderId,
                                          UUID catalogItemId,
                                          String name,
                                          int quantity,
                                          String unitPrice,
                                          String lineTotal) {
        BusinessOrderLine line = mock(BusinessOrderLine.class);
        when(line.getOrderId()).thenReturn(orderId);
        when(line.getCatalogItemId()).thenReturn(catalogItemId);
        when(line.getItemName()).thenReturn(name);
        when(line.getQuantity()).thenReturn(quantity);
        when(line.getUnitPrice()).thenReturn(new BigDecimal(unitPrice));
        when(line.getLineTotal()).thenReturn(new BigDecimal(lineTotal));
        return line;
    }
}
