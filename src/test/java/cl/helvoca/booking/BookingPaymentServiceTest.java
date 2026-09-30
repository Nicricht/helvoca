package cl.helvoca.booking;

import cl.helvoca.audit.AuditService;
import cl.helvoca.common.ConflictException;
import cl.helvoca.operations.BusinessOperation;
import cl.helvoca.operations.BusinessOperationRepository;
import cl.helvoca.payment.BusinessPayment;
import cl.helvoca.payment.BusinessPaymentRepository;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BookingPaymentServiceTest {
    @Test
    void manualPaymentIsIdempotentAndKeepsManualEvidenceSeparate() {
        Fixture f = fixture(BookingStatus.COMPLETED, new BigDecimal("15000"));
        when(f.operations.saveAndFlush(any(BusinessOperation.class))).thenAnswer(inv -> {
            BusinessOperation op = inv.getArgument(0);
            op.setId(UUID.randomUUID());
            return op;
        });
        when(f.payments.saveAndFlush(any(BusinessPayment.class))).thenAnswer(inv -> {
            BusinessPayment p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });
        when(f.payments.findAllByBusinessIdAndTargetOperationIdOrderByCreatedAtAsc(
                f.businessId, f.operationId))
                .thenReturn(List.of(), List.of(manualPayment(f.operationId, "15000")));
        when(f.payments.findByBusinessIdAndIdempotencyKey(f.businessId, "cash-1"))
                .thenReturn(Optional.empty());

        BookingPaymentService.ManualPaymentResponse result = f.service.recordManual(
                f.bookingId,
                new BookingPaymentService.ManualPaymentRequest(
                        new BigDecimal("15000"), "CLP", BusinessPayment.PaymentMethod.CASH, null),
                "cash-1");

        assertFalse(result.idempotentReplay());
        assertEquals(BookingPaymentService.PaymentState.PAID, result.summary().paymentState());
        assertEquals(BookingPaymentService.RevenueState.REALIZED, result.summary().revenueState());
        assertEquals(0, new BigDecimal("15000").compareTo(result.summary().manualRecordedAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.summary().providerVerifiedAmount()));

        ArgumentCaptor<BusinessPayment> captor = ArgumentCaptor.forClass(BusinessPayment.class);
        verify(f.payments).saveAndFlush(captor.capture());
        assertEquals(BusinessPayment.VerificationMethod.MANUAL_BUSINESS, captor.getValue().getVerificationMethod());
        assertEquals(BusinessPayment.PaymentMethod.CASH, captor.getValue().getPaymentMethod());
        assertNotNull(captor.getValue().getVerifiedAt());
    }

    @Test
    void rejectsOverpaymentAndPaymentForNoShow() {
        Fixture paid = fixture(BookingStatus.COMPLETED, new BigDecimal("15000"));
        when(paid.payments.findAllByBusinessIdAndTargetOperationIdOrderByCreatedAtAsc(
                paid.businessId, paid.operationId)).thenReturn(List.of());
        when(paid.payments.findByBusinessIdAndIdempotencyKey(paid.businessId, "over"))
                .thenReturn(Optional.empty());

        assertThrows(ConflictException.class, () -> paid.service.recordManual(
                paid.bookingId,
                new BookingPaymentService.ManualPaymentRequest(
                        new BigDecimal("16000"), "CLP", BusinessPayment.PaymentMethod.CARD, null),
                "over"));

        Fixture noShow = fixture(BookingStatus.NO_SHOW, new BigDecimal("15000"));
        assertThrows(ConflictException.class, () -> noShow.service.recordManual(
                noShow.bookingId,
                new BookingPaymentService.ManualPaymentRequest(
                        new BigDecimal("15000"), "CLP", BusinessPayment.PaymentMethod.CASH, null),
                "no-show"));
    }

    @Test
    void providerVerifiedPrepaymentDoesNotBecomeRealizedRevenueUntilServiceCompletes() {
        Fixture f = fixture(BookingStatus.CONFIRMED, new BigDecimal("15000"));
        BusinessPayment provider = providerPayment(f.operationId, "15000");
        when(f.payments.findAllByBusinessIdAndTargetOperationIdOrderByCreatedAtAsc(
                f.businessId, f.operationId)).thenReturn(List.of(provider));

        BookingPaymentService.BookingPaymentSummary summary = f.service.summary(f.bookingId);

        assertEquals(BookingPaymentService.PaymentState.PAID, summary.paymentState());
        assertEquals(BookingPaymentService.RevenueState.NOT_REALIZED, summary.revenueState());
        assertEquals(0, new BigDecimal("15000").compareTo(summary.providerVerifiedAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(summary.realizedRevenueAmount()));
    }

    private static Fixture fixture(BookingStatus status, BigDecimal total) {
        UUID businessId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        BookingRepository bookings = mock(BookingRepository.class);
        BusinessOperationRepository operations = mock(BusinessOperationRepository.class);
        BusinessPaymentRepository payments = mock(BusinessPaymentRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);
        AuditService audit = mock(AuditService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);

        Booking booking = mock(Booking.class);
        when(booking.getId()).thenReturn(bookingId);
        when(booking.getBusinessId()).thenReturn(businessId);
        when(booking.getOperationId()).thenReturn(operationId);
        when(booking.getCustomerId()).thenReturn(UUID.randomUUID());
        when(booking.getStatus()).thenReturn(status);

        BusinessOperation operation = new BusinessOperation();
        operation.setId(operationId);
        operation.setBusinessId(businessId);
        operation.setType(BusinessOperation.Type.BOOKING);
        operation.setStatus(status == BookingStatus.COMPLETED
                ? BusinessOperation.Status.COMPLETED
                : BusinessOperation.Status.CONFIRMED);
        operation.setTotal(total);
        operation.setCurrency("CLP");

        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(bookings.findByIdAndBusinessId(bookingId, businessId)).thenReturn(Optional.of(booking));
        when(operations.findByIdAndBusinessId(operationId, businessId)).thenReturn(Optional.of(operation));

        return new Fixture(
                businessId, bookingId, operationId, operations, payments,
                new BookingPaymentService(bookings, operations, payments, tenant, audit, jdbc));
    }

    private static BusinessPayment manualPayment(UUID target, String amount) {
        BusinessPayment p = new BusinessPayment();
        p.setTargetOperationId(target);
        p.setAmount(new BigDecimal(amount));
        p.setCurrency("CLP");
        p.setStatus(BusinessPayment.Status.SUCCEEDED);
        p.setVerificationMethod(BusinessPayment.VerificationMethod.MANUAL_BUSINESS);
        p.setPaymentMethod(BusinessPayment.PaymentMethod.CASH);
        p.setVerifiedAt(Instant.now());
        return p;
    }

    private static BusinessPayment providerPayment(UUID target, String amount) {
        BusinessPayment p = manualPayment(target, amount);
        p.setVerificationMethod(BusinessPayment.VerificationMethod.PROVIDER);
        p.setPaymentMethod(BusinessPayment.PaymentMethod.ONLINE);
        return p;
    }

    private record Fixture(
            UUID businessId,
            UUID bookingId,
            UUID operationId,
            BusinessOperationRepository operations,
            BusinessPaymentRepository payments,
            BookingPaymentService service) {}
}
