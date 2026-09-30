package cl.helvoca.operations;

import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.payment.BusinessPayment;
import cl.helvoca.payment.BusinessPaymentRepository;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SalesAnalyticsBookingRevenueContractTest {

    @Test
    void completedPaidBookingIsExposedAsRealizedServiceRevenueWithProviderEvidence() throws Exception {
        UUID businessId = UUID.randomUUID();
        UUID bookingOperationId = UUID.randomUUID();

        BusinessRepository businesses = mock(BusinessRepository.class);
        BusinessPaymentRepository payments = mock(BusinessPaymentRepository.class);
        BusinessOrderRepository orders = mock(BusinessOrderRepository.class);
        BusinessOrderLineRepository lines = mock(BusinessOrderLineRepository.class);
        BusinessOperationRepository operations = mock(BusinessOperationRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);

        when(tenant.requireBusinessId()).thenReturn(businessId);
        Business business = new Business();
        business.setTimezone("America/Santiago");
        when(businesses.findById(businessId)).thenReturn(Optional.of(business));

        BusinessPayment payment = paidBooking(
                bookingOperationId,
                new BigDecimal("15000"),
                BusinessPayment.VerificationMethod.PROVIDER,
                BusinessOrder.Source.WHATSAPP);

        when(payments.findAllByBusinessIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                eq(businessId), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(payment), List.of());
        when(orders.findAllByBusinessIdAndOperationIdIn(eq(businessId), anyCollection()))
                .thenReturn(List.of());

        BusinessOperation booking = new BusinessOperation();
        booking.setId(bookingOperationId);
        booking.setBusinessId(businessId);
        booking.setType(BusinessOperation.Type.BOOKING);
        booking.setStatus(BusinessOperation.Status.COMPLETED);
        booking.setSource(BusinessOrder.Source.WHATSAPP);
        booking.setTotal(new BigDecimal("15000"));
        booking.setCurrency("CLP");
        when(operations.findByIdAndBusinessId(bookingOperationId, businessId))
                .thenReturn(Optional.of(booking));

        SalesAnalyticsService service = SalesAnalyticsService.class
                .getConstructor(
                        BusinessRepository.class,
                        BusinessPaymentRepository.class,
                        BusinessOrderRepository.class,
                        BusinessOrderLineRepository.class,
                        BusinessOperationRepository.class,
                        TenantProvider.class)
                .newInstance(businesses, payments, orders, lines, operations, tenant);

        SalesAnalyticsService.AnalyticsResponse result = service.analytics(7);

        assertEquals(0, result.paidOrders());
        assertEquals(1L, invoke(result, "paidBookings"));
        assertMoney("15000", invoke(result, "bookingRevenue"));
        assertMoney("15000", invoke(result, "providerVerifiedBookingRevenue"));
        assertMoney("0", invoke(result, "manualRecordedBookingRevenue"));
        assertEquals(1L, invoke(result, "recepVozPaidBookings"));
        assertMoney("15000", invoke(result, "recepVozBookingRevenue"));
    }

    @Test
    void cancelledOrNoShowBookingOperationNeverBecomesServiceRevenue() throws Exception {
        UUID businessId = UUID.randomUUID();
        UUID bookingOperationId = UUID.randomUUID();

        BusinessRepository businesses = mock(BusinessRepository.class);
        BusinessPaymentRepository payments = mock(BusinessPaymentRepository.class);
        BusinessOrderRepository orders = mock(BusinessOrderRepository.class);
        BusinessOrderLineRepository lines = mock(BusinessOrderLineRepository.class);
        BusinessOperationRepository operations = mock(BusinessOperationRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);

        when(tenant.requireBusinessId()).thenReturn(businessId);
        Business business = new Business();
        business.setTimezone("America/Santiago");
        when(businesses.findById(businessId)).thenReturn(Optional.of(business));

        BusinessPayment payment = paidBooking(
                bookingOperationId,
                new BigDecimal("15000"),
                BusinessPayment.VerificationMethod.MANUAL_BUSINESS,
                BusinessOrder.Source.MANUAL);
        when(payments.findAllByBusinessIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                eq(businessId), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(payment), List.of());
        when(orders.findAllByBusinessIdAndOperationIdIn(eq(businessId), anyCollection()))
                .thenReturn(List.of());

        BusinessOperation noShow = new BusinessOperation();
        noShow.setId(bookingOperationId);
        noShow.setBusinessId(businessId);
        noShow.setType(BusinessOperation.Type.BOOKING);
        noShow.setStatus(BusinessOperation.Status.CANCELLED);
        noShow.setSource(BusinessOrder.Source.MANUAL);
        noShow.setTotal(new BigDecimal("15000"));
        noShow.setCurrency("CLP");
        when(operations.findByIdAndBusinessId(bookingOperationId, businessId))
                .thenReturn(Optional.of(noShow));

        SalesAnalyticsService service = SalesAnalyticsService.class
                .getConstructor(
                        BusinessRepository.class,
                        BusinessPaymentRepository.class,
                        BusinessOrderRepository.class,
                        BusinessOrderLineRepository.class,
                        BusinessOperationRepository.class,
                        TenantProvider.class)
                .newInstance(businesses, payments, orders, lines, operations, tenant);

        SalesAnalyticsService.AnalyticsResponse result = service.analytics(7);

        assertEquals(0L, invoke(result, "paidBookings"));
        assertMoney("0", invoke(result, "bookingRevenue"));
        assertMoney("0", invoke(result, "manualRecordedBookingRevenue"));
    }

    private static BusinessPayment paidBooking(UUID targetOperationId,
                                               BigDecimal amount,
                                               BusinessPayment.VerificationMethod verification,
                                               BusinessOrder.Source source) {
        BusinessPayment payment = mock(BusinessPayment.class);
        Instant now = Instant.now().minusSeconds(300);
        when(payment.getTargetOperationId()).thenReturn(targetOperationId);
        when(payment.getAmount()).thenReturn(amount);
        when(payment.getCurrency()).thenReturn("CLP");
        when(payment.getSource()).thenReturn(source);
        when(payment.getStatus()).thenReturn(BusinessPayment.Status.SUCCEEDED);
        when(payment.getVerificationMethod()).thenReturn(verification);
        when(payment.getCreatedAt()).thenReturn(now);
        when(payment.getUpdatedAt()).thenReturn(now);
        return payment;
    }

    private static Object invoke(Object target, String method) throws Exception {
        Method accessor = target.getClass().getMethod(method);
        return accessor.invoke(target);
    }

    private static void assertMoney(String expected, Object actual) {
        assertInstanceOf(BigDecimal.class, actual);
        assertEquals(0, new BigDecimal(expected).compareTo((BigDecimal) actual));
    }
}
