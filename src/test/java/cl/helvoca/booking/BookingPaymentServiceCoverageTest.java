package cl.helvoca.booking;

import cl.helvoca.audit.AuditService;
import cl.helvoca.common.ConflictException;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.operations.BusinessOperation;
import cl.helvoca.operations.BusinessOperationRepository;
import cl.helvoca.operations.BusinessOrder;
import cl.helvoca.payment.BusinessPayment;
import cl.helvoca.payment.BusinessPaymentRepository;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BookingPaymentServiceCoverageTest {

    @Test
    void rejectsCancelledBookingAndInvalidIdempotencyKeysBeforeWriting() {
        Fixture cancelled = fixture(BookingStatus.CANCELLED, new BigDecimal("15000"), "CLP");
        assertThrows(ConflictException.class, () -> cancelled.service.recordManual(
                cancelled.bookingId, request("15000", "CLP", BusinessPayment.PaymentMethod.CASH), "cancelled"));

        Fixture normal = fixture(BookingStatus.CONFIRMED, new BigDecimal("15000"), "CLP");
        assertThrows(IllegalArgumentException.class, () -> normal.service.recordManual(
                normal.bookingId, request("15000", "CLP", BusinessPayment.PaymentMethod.CASH), " "));
        assertThrows(IllegalArgumentException.class, () -> normal.service.recordManual(
                normal.bookingId, request("15000", "CLP", BusinessPayment.PaymentMethod.CASH), "x".repeat(181)));
        verify(normal.operations, never()).saveAndFlush(any(BusinessOperation.class));
    }

    @Test
    void validIdempotentReplayReturnsExistingPaymentWithoutSecondWrite() {
        Fixture f = fixture(BookingStatus.COMPLETED, new BigDecimal("15000"), "CLP");
        BusinessPayment existing = successfulPayment(
                f.operationId, "15000", "CLP", BusinessPayment.VerificationMethod.MANUAL_BUSINESS);
        existing.setId(UUID.randomUUID());
        existing.setIdempotencyKey("cash-replay");

        when(f.payments.findByBusinessIdAndIdempotencyKey(f.businessId, "cash-replay"))
                .thenReturn(Optional.of(existing));
        when(f.payments.findAllByBusinessIdAndTargetOperationIdOrderByCreatedAtAsc(
                f.businessId, f.operationId)).thenReturn(List.of(existing));

        BookingPaymentService.ManualPaymentResponse response = f.service.recordManual(
                f.bookingId,
                request("15000", "CLP", BusinessPayment.PaymentMethod.CASH),
                "cash-replay");

        assertTrue(response.idempotentReplay());
        assertEquals(existing.getId(), response.paymentId());
        verify(f.operations, never()).saveAndFlush(any(BusinessOperation.class));
        verify(f.payments, never()).saveAndFlush(any(BusinessPayment.class));
    }

    @Test
    void idempotencyKeyCannotBeReusedForAnotherTargetOrProviderPayment() {
        Fixture f = fixture(BookingStatus.COMPLETED, new BigDecimal("15000"), "CLP");

        BusinessPayment otherTarget = successfulPayment(
                UUID.randomUUID(), "15000", "CLP", BusinessPayment.VerificationMethod.MANUAL_BUSINESS);
        when(f.payments.findByBusinessIdAndIdempotencyKey(f.businessId, "reused"))
                .thenReturn(Optional.of(otherTarget));
        assertThrows(ConflictException.class, () -> f.service.recordManual(
                f.bookingId, request("15000", "CLP", BusinessPayment.PaymentMethod.CASH), "reused"));

        BusinessPayment provider = successfulPayment(
                f.operationId, "15000", "CLP", BusinessPayment.VerificationMethod.PROVIDER);
        when(f.payments.findByBusinessIdAndIdempotencyKey(f.businessId, "provider-key"))
                .thenReturn(Optional.of(provider));
        assertThrows(ConflictException.class, () -> f.service.recordManual(
                f.bookingId, request("15000", "CLP", BusinessPayment.PaymentMethod.CASH), "provider-key"));
    }

    @Test
    void manualPaymentRequiresAuthoritativePositiveAmountAndCurrency() {
        Fixture nullTotal = fixture(BookingStatus.COMPLETED, null, "CLP");
        when(nullTotal.payments.findByBusinessIdAndIdempotencyKey(nullTotal.businessId, "null-total"))
                .thenReturn(Optional.empty());
        assertThrows(ConflictException.class, () -> nullTotal.service.recordManual(
                nullTotal.bookingId, request("1", "CLP", BusinessPayment.PaymentMethod.CASH), "null-total"));

        Fixture zeroTotal = fixture(BookingStatus.COMPLETED, BigDecimal.ZERO, "CLP");
        when(zeroTotal.payments.findByBusinessIdAndIdempotencyKey(zeroTotal.businessId, "zero-total"))
                .thenReturn(Optional.empty());
        assertThrows(ConflictException.class, () -> zeroTotal.service.recordManual(
                zeroTotal.bookingId, request("1", "CLP", BusinessPayment.PaymentMethod.CASH), "zero-total"));

        Fixture badCurrency = fixture(BookingStatus.COMPLETED, new BigDecimal("15000"), "PESOS");
        when(badCurrency.payments.findByBusinessIdAndIdempotencyKey(badCurrency.businessId, "bad-currency"))
                .thenReturn(Optional.empty());
        assertThrows(ConflictException.class, () -> badCurrency.service.recordManual(
                badCurrency.bookingId, request("15000", "CLP", BusinessPayment.PaymentMethod.CASH), "bad-currency"));
    }

    @Test
    void manualPaymentRejectsInvalidRequestCurrencyAndMethod() {
        Fixture f = fixture(BookingStatus.COMPLETED, new BigDecimal("15000"), "CLP");
        when(f.payments.findByBusinessIdAndIdempotencyKey(eq(f.businessId), anyString()))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> f.service.recordManual(f.bookingId, null, "null-request"));
        assertThrows(IllegalArgumentException.class, () -> f.service.recordManual(
                f.bookingId,
                new BookingPaymentService.ManualPaymentRequest(null, "CLP", BusinessPayment.PaymentMethod.CASH, null),
                "null-amount"));
        assertThrows(IllegalArgumentException.class, () -> f.service.recordManual(
                f.bookingId, request("0", "CLP", BusinessPayment.PaymentMethod.CASH), "zero-amount"));
        assertThrows(ConflictException.class, () -> f.service.recordManual(
                f.bookingId, request("15000", "USD", BusinessPayment.PaymentMethod.CASH), "wrong-currency"));
        assertThrows(IllegalArgumentException.class, () -> f.service.recordManual(
                f.bookingId,
                new BookingPaymentService.ManualPaymentRequest(
                        new BigDecimal("15000"), "CLP", null, null),
                "null-method"));
        assertThrows(IllegalArgumentException.class, () -> f.service.recordManual(
                f.bookingId, request("15000", "CLP", BusinessPayment.PaymentMethod.ONLINE), "online-method"));
    }

    @Test
    void manualPaymentRejectsAlreadyPaidAndSupportsPartialRealizedRevenue() {
        Fixture alreadyPaid = fixture(BookingStatus.COMPLETED, new BigDecimal("15000"), "CLP");
        BusinessPayment full = successfulPayment(
                alreadyPaid.operationId, "15000", "CLP", BusinessPayment.VerificationMethod.PROVIDER);
        when(alreadyPaid.payments.findByBusinessIdAndIdempotencyKey(alreadyPaid.businessId, "already-paid"))
                .thenReturn(Optional.empty());
        when(alreadyPaid.payments.findAllByBusinessIdAndTargetOperationIdOrderByCreatedAtAsc(
                alreadyPaid.businessId, alreadyPaid.operationId)).thenReturn(List.of(full));
        assertThrows(ConflictException.class, () -> alreadyPaid.service.recordManual(
                alreadyPaid.bookingId,
                request("1000", "CLP", BusinessPayment.PaymentMethod.CASH),
                "already-paid"));

        Fixture partial = fixture(BookingStatus.COMPLETED, new BigDecimal("15000"), "CLP");
        BusinessPayment first = successfulPayment(
                partial.operationId, "5000", "CLP", BusinessPayment.VerificationMethod.PROVIDER);
        BusinessPayment second = successfulPayment(
                partial.operationId, "5000", "CLP", BusinessPayment.VerificationMethod.MANUAL_BUSINESS);
        when(partial.payments.findByBusinessIdAndIdempotencyKey(partial.businessId, "partial"))
                .thenReturn(Optional.empty());
        when(partial.payments.findAllByBusinessIdAndTargetOperationIdOrderByCreatedAtAsc(
                partial.businessId, partial.operationId))
                .thenReturn(List.of(first), List.of(first, second));
        stubSuccessfulWrites(partial);

        BookingPaymentService.ManualPaymentResponse response = partial.service.recordManual(
                partial.bookingId,
                new BookingPaymentService.ManualPaymentRequest(
                        new BigDecimal("5000"), "clp", BusinessPayment.PaymentMethod.TRANSFER, "  transferencia  "),
                "partial");

        assertEquals(BookingPaymentService.PaymentState.PARTIALLY_PAID, response.summary().paymentState());
        assertEquals(BookingPaymentService.RevenueState.PARTIALLY_REALIZED, response.summary().revenueState());
        assertMoney("10000", response.summary().paidAmount());
        assertMoney("5000", response.summary().remainingAmount());
        assertMoney("5000", response.summary().providerVerifiedAmount());
        assertMoney("5000", response.summary().manualRecordedAmount());
    }

    @Test
    void summaryIgnoresInvalidPaymentEvidenceAndHandlesUnpricedAndUnpaidBookings() {
        Fixture unpriced = fixture(BookingStatus.CONFIRMED, null, "CLP");
        when(unpriced.payments.findAllByBusinessIdAndTargetOperationIdOrderByCreatedAtAsc(
                unpriced.businessId, unpriced.operationId)).thenReturn(List.of());
        assertEquals(BookingPaymentService.PaymentState.UNPRICED,
                unpriced.service.summary(unpriced.bookingId).paymentState());

        Fixture unpaid = fixture(BookingStatus.COMPLETED, new BigDecimal("15000"), "CLP");
        BusinessPayment failed = successfulPayment(
                unpaid.operationId, "1000", "CLP", BusinessPayment.VerificationMethod.PROVIDER);
        failed.setStatus(BusinessPayment.Status.FAILED);
        BusinessPayment noAmount = successfulPayment(
                unpaid.operationId, "1000", "CLP", BusinessPayment.VerificationMethod.PROVIDER);
        noAmount.setAmount(null);
        BusinessPayment wrongCurrency = successfulPayment(
                unpaid.operationId, "1000", "USD", BusinessPayment.VerificationMethod.PROVIDER);
        when(unpaid.payments.findAllByBusinessIdAndTargetOperationIdOrderByCreatedAtAsc(
                unpaid.businessId, unpaid.operationId))
                .thenReturn(java.util.Arrays.asList(null, failed, noAmount, wrongCurrency));

        BookingPaymentService.BookingPaymentSummary summary = unpaid.service.summary(unpaid.bookingId);
        assertEquals(BookingPaymentService.PaymentState.UNPAID, summary.paymentState());
        assertEquals(BookingPaymentService.RevenueState.NOT_REALIZED, summary.revenueState());
        assertMoney("0", summary.paidAmount());
    }

    @Test
    void bookingAndPaymentTargetMustExistAndBeCorrectType() {
        Fixture missingBooking = fixture(BookingStatus.CONFIRMED, new BigDecimal("15000"), "CLP");
        when(missingBooking.bookings.findByIdAndBusinessId(
                missingBooking.bookingId, missingBooking.businessId)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> missingBooking.service.summary(missingBooking.bookingId));

        Fixture missingOperationId = fixture(BookingStatus.CONFIRMED, new BigDecimal("15000"), "CLP");
        when(missingOperationId.booking.getOperationId()).thenReturn(null);
        assertThrows(ConflictException.class, () -> missingOperationId.service.summary(missingOperationId.bookingId));

        Fixture missingOperation = fixture(BookingStatus.CONFIRMED, new BigDecimal("15000"), "CLP");
        when(missingOperation.operations.findByIdAndBusinessId(
                missingOperation.operationId, missingOperation.businessId)).thenReturn(Optional.empty());
        assertThrows(ConflictException.class, () -> missingOperation.service.summary(missingOperation.bookingId));

        Fixture wrongType = fixture(BookingStatus.CONFIRMED, new BigDecimal("15000"), "CLP");
        wrongType.operation.setType(BusinessOperation.Type.ORDER);
        assertThrows(ConflictException.class, () -> wrongType.service.summary(wrongType.bookingId));
    }

    private static Fixture fixture(BookingStatus status, BigDecimal total, String currency) {
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
                : status == BookingStatus.NO_SHOW || status == BookingStatus.CANCELLED
                    ? BusinessOperation.Status.CANCELLED
                    : BusinessOperation.Status.CONFIRMED);
        operation.setTotal(total);
        operation.setCurrency(currency);

        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(bookings.findByIdAndBusinessId(bookingId, businessId)).thenReturn(Optional.of(booking));
        when(operations.findByIdAndBusinessId(operationId, businessId)).thenReturn(Optional.of(operation));

        return new Fixture(
                businessId, bookingId, operationId, booking, operation,
                bookings, operations, payments, audit,
                new BookingPaymentService(bookings, operations, payments, tenant, audit, jdbc));
    }

    private static void stubSuccessfulWrites(Fixture f) {
        when(f.operations.saveAndFlush(any(BusinessOperation.class))).thenAnswer(invocation -> {
            BusinessOperation operation = invocation.getArgument(0);
            if (operation.getId() == null) operation.setId(UUID.randomUUID());
            return operation;
        });
        when(f.payments.saveAndFlush(any(BusinessPayment.class))).thenAnswer(invocation -> {
            BusinessPayment payment = invocation.getArgument(0);
            if (payment.getId() == null) payment.setId(UUID.randomUUID());
            return payment;
        });
    }

    private static BookingPaymentService.ManualPaymentRequest request(
            String amount, String currency, BusinessPayment.PaymentMethod method) {
        return new BookingPaymentService.ManualPaymentRequest(
                new BigDecimal(amount), currency, method, null);
    }

    private static BusinessPayment successfulPayment(
            UUID target, String amount, String currency, BusinessPayment.VerificationMethod verification) {
        BusinessPayment payment = new BusinessPayment();
        payment.setTargetOperationId(target);
        payment.setAmount(new BigDecimal(amount));
        payment.setCurrency(currency);
        payment.setStatus(BusinessPayment.Status.SUCCEEDED);
        payment.setVerificationMethod(verification);
        payment.setPaymentMethod(
                verification == BusinessPayment.VerificationMethod.MANUAL_BUSINESS
                        ? BusinessPayment.PaymentMethod.CASH
                        : BusinessPayment.PaymentMethod.ONLINE);
        return payment;
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }

    private record Fixture(
            UUID businessId,
            UUID bookingId,
            UUID operationId,
            Booking booking,
            BusinessOperation operation,
            BookingRepository bookings,
            BusinessOperationRepository operations,
            BusinessPaymentRepository payments,
            AuditService audit,
            BookingPaymentService service) {}
}
