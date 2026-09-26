package cl.helvoca.operations;

import cl.helvoca.booking.Booking;
import cl.helvoca.booking.BookingRepository;
import cl.helvoca.booking.BookingStatus;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.call.CallSessionRepository;
import cl.helvoca.call.CallStatus;
import cl.helvoca.messaging.MessagingConversation;
import cl.helvoca.messaging.MessagingConversationRepository;
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

class PilotMetricsServiceTest {

    @Test
    void computesCommercialAndOperationalPilotMetrics() {
        UUID businessId = UUID.randomUUID();
        BusinessRepository businesses = mock(BusinessRepository.class);
        CallSessionRepository calls = mock(CallSessionRepository.class);
        BookingRepository bookings = mock(BookingRepository.class);
        MessagingConversationRepository conversations = mock(MessagingConversationRepository.class);
        BusinessOrderRepository orders = mock(BusinessOrderRepository.class);
        BusinessPaymentRepository payments = mock(BusinessPaymentRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);

        Business business = new Business();
        business.setName("Piloto Demo");
        business.setTimezone("America/Santiago");

        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(businesses.findById(businessId)).thenReturn(Optional.of(business));

        when(calls.countByBusinessIdAndCertificationFalseAndTelephonyProviderNotAndStartedAtGreaterThanEqualAndStartedAtLessThan(
                eq(businessId), eq("simulator"), any(Instant.class), any(Instant.class)))
                .thenReturn(10L);
        when(calls.countByBusinessIdAndCertificationFalseAndTelephonyProviderNotAndStatusInAndStartedAtGreaterThanEqualAndStartedAtLessThan(
                eq(businessId), eq("simulator"), anyList(), any(Instant.class), any(Instant.class)))
                .thenReturn(2L);
        when(calls.countByBusinessIdAndCertificationFalseAndTelephonyProviderNotAndResolutionAndStartedAtGreaterThanEqualAndStartedAtLessThan(
                eq(businessId), eq("simulator"), eq("HUMAN_TRANSFERRED"), any(Instant.class), any(Instant.class)))
                .thenReturn(1L);

        Booking confirmed = mock(Booking.class);
        when(confirmed.getStatus()).thenReturn(BookingStatus.CONFIRMED);
        when(confirmed.getCreatedAt()).thenReturn(Instant.now());
        Booking cancelled = mock(Booking.class);
        when(cancelled.getStatus()).thenReturn(BookingStatus.CANCELLED);
        when(cancelled.getCreatedAt()).thenReturn(Instant.now());
        when(bookings.findAllByBusinessIdOrderByStartAtDesc(businessId))
                .thenReturn(List.of(confirmed, cancelled));

        MessagingConversation whatsapp = new MessagingConversation();
        whatsapp.setLastMessageAt(Instant.now());
        when(conversations.findAllByBusinessIdAndChannelOrderByLastMessageAtDesc(
                businessId, "whatsapp")).thenReturn(List.of(whatsapp));

        BusinessOrder firstOrder = new BusinessOrder();
        firstOrder.setStatus(BusinessOrder.Status.CONFIRMED);
        BusinessOrder secondOrder = new BusinessOrder();
        secondOrder.setStatus(BusinessOrder.Status.COMPLETED);
        when(orders.findAllByBusinessIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                eq(businessId), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(firstOrder, secondOrder));

        BusinessPayment paid = payment(
                BusinessPayment.Status.SUCCEEDED, "1000", "CLP");
        BusinessPayment pending = payment(
                BusinessPayment.Status.REQUIRES_ACTION, "2000", "CLP");
        BusinessPayment failed = payment(
                BusinessPayment.Status.FAILED, "3000", "CLP");
        when(payments.findAllByBusinessIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                eq(businessId), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(paid, pending, failed));

        PilotMetricsService service = new PilotMetricsService(
                businesses, calls, bookings, conversations, orders, payments, tenant);

        PilotMetricsService.Metrics result = service.metrics();
        PilotMetricsService.Window today = result.today();

        assertEquals("Piloto Demo", result.businessName());
        assertEquals(10, today.calls());
        assertEquals(1, today.whatsappConversations());
        assertEquals(1, today.bookings());
        assertEquals(2, today.orders());
        assertEquals(3, today.paymentAttempts());
        assertEquals(1, today.successfulPayments());
        assertEquals(1, today.pendingPayments());
        assertEquals(1, today.failedPayments());
        assertEquals(0, today.refundedPayments());
        assertEquals(1, today.humanTransfers());
        assertEquals(2, today.callFailures());
        assertEquals(new BigDecimal("1000"), today.confirmedRevenueByCurrency().get("CLP"));
        assertEquals(new BigDecimal("50.0"), today.paidOrderConversionPct());
        assertEquals(new BigDecimal("33.3"), today.paymentSuccessRatePct());
        assertEquals(new BigDecimal("20.0"), today.callFailureRatePct());
        assertEquals(new BigDecimal("10.0"), today.humanTransferRatePct());

        assertEquals(today.orders(), result.last7Days().orders());
        assertEquals(today.successfulPayments(), result.last7Days().successfulPayments());
    }

    private static BusinessPayment payment(BusinessPayment.Status status,
                                           String amount,
                                           String currency) {
        BusinessPayment payment = new BusinessPayment();
        payment.setStatus(status);
        payment.setAmount(new BigDecimal(amount));
        payment.setCurrency(currency);
        return payment;
    }
}
