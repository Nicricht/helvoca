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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class BookingPaymentService {
    public enum PaymentState { UNPRICED, UNPAID, PARTIALLY_PAID, PAID }
    public enum RevenueState { NOT_REALIZED, PARTIALLY_REALIZED, REALIZED }

    public record ManualPaymentRequest(
            BigDecimal amount,
            String currency,
            BusinessPayment.PaymentMethod method,
            String note) {}

    public record BookingPaymentSummary(
            UUID bookingId,
            BookingStatus bookingStatus,
            BigDecimal totalAmount,
            BigDecimal paidAmount,
            BigDecimal remainingAmount,
            String currency,
            PaymentState paymentState,
            RevenueState revenueState,
            BigDecimal providerVerifiedAmount,
            BigDecimal manualRecordedAmount,
            BigDecimal realizedRevenueAmount,
            long succeededPayments) {}

    public record ManualPaymentResponse(
            UUID paymentId,
            boolean idempotentReplay,
            BookingPaymentSummary summary) {}

    private final BookingRepository bookings;
    private final BusinessOperationRepository operations;
    private final BusinessPaymentRepository payments;
    private final TenantProvider tenant;
    private final AuditService audit;
    private final JdbcTemplate jdbc;

    public BookingPaymentService(BookingRepository bookings,
                                 BusinessOperationRepository operations,
                                 BusinessPaymentRepository payments,
                                 TenantProvider tenant,
                                 AuditService audit,
                                 JdbcTemplate jdbc) {
        this.bookings = bookings;
        this.operations = operations;
        this.payments = payments;
        this.tenant = tenant;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public BookingPaymentSummary summary(UUID bookingId) {
        UUID businessId = tenant.requireBusinessId();
        Booking booking = requireBooking(bookingId, businessId);
        BusinessOperation target = requireBookingOperation(booking, businessId);
        return summarize(booking, target, paymentsFor(businessId, target.getId()));
    }

    @Transactional
    public ManualPaymentResponse recordManual(UUID bookingId,
                                              ManualPaymentRequest request,
                                              String idempotencyKey) {
        UUID businessId = tenant.requireBusinessId();
        String key = normalizeIdempotencyKey(idempotencyKey);
        advisoryLock(businessId, key);

        Booking booking = requireBooking(bookingId, businessId);
        if (booking.getStatus() == BookingStatus.CANCELLED || booking.getStatus() == BookingStatus.NO_SHOW) {
            throw new ConflictException("Cancelled or no-show bookings cannot receive a new manual payment");
        }
        BusinessOperation target = requireBookingOperation(booking, businessId);

        BusinessPayment existing = payments.findByBusinessIdAndIdempotencyKey(businessId, key).orElse(null);
        if (existing != null) {
            if (!Objects.equals(existing.getTargetOperationId(), target.getId())
                    || existing.getVerificationMethod() != BusinessPayment.VerificationMethod.MANUAL_BUSINESS) {
                throw new ConflictException("Idempotency-Key is already used by another payment");
            }
            return new ManualPaymentResponse(
                    existing.getId(),
                    true,
                    summarize(booking, target, paymentsFor(businessId, target.getId())));
        }

        BigDecimal total = target.getTotal();
        if (total == null || total.signum() <= 0) {
            throw new ConflictException("Booking does not have an authoritative amount to pay");
        }
        String authoritativeCurrency = normalizeCurrency(target.getCurrency());
        if (authoritativeCurrency == null) {
            throw new ConflictException("Booking does not have an authoritative currency");
        }
        if (request == null || request.amount() == null || request.amount().signum() <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }
        String requestedCurrency = normalizeCurrency(request.currency());
        if (!authoritativeCurrency.equals(requestedCurrency)) {
            throw new ConflictException("Manual payment currency does not match booking currency");
        }
        if (request.method() == null || request.method() == BusinessPayment.PaymentMethod.ONLINE) {
            throw new IllegalArgumentException("Manual payment method must be CASH, CARD, TRANSFER or OTHER");
        }

        List<BusinessPayment> beforePayments = paymentsFor(businessId, target.getId());
        BigDecimal paidBefore = succeededAmount(beforePayments, authoritativeCurrency);
        BigDecimal remaining = total.subtract(paidBefore);
        if (remaining.signum() <= 0) {
            throw new ConflictException("Booking is already fully paid");
        }
        if (request.amount().compareTo(remaining) > 0) {
            throw new ConflictException("Manual payment exceeds the remaining booking balance");
        }

        BusinessOperation paymentOperation = new BusinessOperation();
        paymentOperation.setBusinessId(businessId);
        paymentOperation.setCustomerId(booking.getCustomerId());
        paymentOperation.setType(BusinessOperation.Type.PAYMENT);
        paymentOperation.setStatus(BusinessOperation.Status.CONFIRMED);
        paymentOperation.setSource(BusinessOrder.Source.MANUAL);
        paymentOperation.setRevision(1);
        paymentOperation.setTotal(request.amount());
        paymentOperation.setCurrency(authoritativeCurrency);
        paymentOperation.setMetadata(Map.of(
                "targetOperationId", target.getId().toString(),
                "bookingId", bookingId.toString(),
                "paymentStatus", BusinessPayment.Status.SUCCEEDED.name(),
                "verificationMethod", BusinessPayment.VerificationMethod.MANUAL_BUSINESS.name(),
                "paymentMethod", request.method().name(),
                "confirmationPending", false));
        paymentOperation = operations.saveAndFlush(paymentOperation);

        BusinessPayment payment = new BusinessPayment();
        payment.setOperationId(paymentOperation.getId());
        payment.setBusinessId(businessId);
        payment.setCustomerId(booking.getCustomerId());
        payment.setTargetOperationId(target.getId());
        payment.setProvider("manual-business");
        payment.setExternalId("MANUAL-" + paymentOperation.getId());
        payment.setIdempotencyKey(key);
        payment.setAmount(request.amount());
        payment.setCurrency(authoritativeCurrency);
        payment.setStatus(BusinessPayment.Status.SUCCEEDED);
        payment.setVerificationMethod(BusinessPayment.VerificationMethod.MANUAL_BUSINESS);
        payment.setPaymentMethod(request.method());
        payment.setVerifiedAt(Instant.now());
        payment.setSource(BusinessOrder.Source.MANUAL);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("bookingId", bookingId.toString());
        metadata.put("recordedFrom", "BOOKING");
        if (request.note() != null && !request.note().isBlank()) metadata.put("note", request.note().trim());
        payment.setMetadata(metadata);
        payment = payments.saveAndFlush(payment);

        audit.humanSuccess(
                businessId,
                "BOOKING_PAYMENT_MANUAL",
                "PAYMENT",
                payment.getId(),
                null,
                Map.of(
                        "bookingId", bookingId.toString(),
                        "amount", payment.getAmount().toPlainString(),
                        "currency", payment.getCurrency(),
                        "verificationMethod", payment.getVerificationMethod().name(),
                        "paymentMethod", payment.getPaymentMethod().name()));

        return new ManualPaymentResponse(
                payment.getId(),
                false,
                summarize(booking, target, paymentsFor(businessId, target.getId())));
    }

    private Booking requireBooking(UUID bookingId, UUID businessId) {
        return bookings.findByIdAndBusinessId(bookingId, businessId)
                .orElseThrow(() -> new NotFoundException("Booking not found"));
    }

    private BusinessOperation requireBookingOperation(Booking booking, UUID businessId) {
        if (booking.getOperationId() == null) {
            throw new ConflictException("Booking is missing its payment target operation");
        }
        BusinessOperation operation = operations.findByIdAndBusinessId(booking.getOperationId(), businessId)
                .orElseThrow(() -> new ConflictException("Booking payment target operation is missing"));
        if (operation.getType() != BusinessOperation.Type.BOOKING) {
            throw new ConflictException("Booking points to an invalid payment target");
        }
        return operation;
    }

    private List<BusinessPayment> paymentsFor(UUID businessId, UUID targetOperationId) {
        return payments.findAllByBusinessIdAndTargetOperationIdOrderByCreatedAtAsc(
                businessId, targetOperationId);
    }

    private BookingPaymentSummary summarize(Booking booking,
                                            BusinessOperation target,
                                            List<BusinessPayment> related) {
        BigDecimal total = target.getTotal();
        String currency = normalizeCurrency(target.getCurrency());
        if (total == null || total.signum() <= 0 || currency == null) {
            return new BookingPaymentSummary(
                    booking.getId(),
                    booking.getStatus(),
                    total,
                    BigDecimal.ZERO,
                    total,
                    currency,
                    PaymentState.UNPRICED,
                    RevenueState.NOT_REALIZED,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    0);
        }

        BigDecimal paid = BigDecimal.ZERO;
        BigDecimal provider = BigDecimal.ZERO;
        BigDecimal manual = BigDecimal.ZERO;
        long count = 0;
        for (BusinessPayment payment : related) {
            if (payment == null
                    || payment.getStatus() != BusinessPayment.Status.SUCCEEDED
                    || payment.getAmount() == null
                    || !currency.equals(normalizeCurrency(payment.getCurrency()))) {
                continue;
            }
            count++;
            paid = paid.add(payment.getAmount());
            if (payment.getVerificationMethod() == BusinessPayment.VerificationMethod.MANUAL_BUSINESS) {
                manual = manual.add(payment.getAmount());
            } else {
                provider = provider.add(payment.getAmount());
            }
        }

        BigDecimal remaining = total.subtract(paid).max(BigDecimal.ZERO);
        PaymentState paymentState = paid.signum() == 0
                ? PaymentState.UNPAID
                : remaining.signum() == 0 ? PaymentState.PAID : PaymentState.PARTIALLY_PAID;

        BigDecimal realized = booking.getStatus() == BookingStatus.COMPLETED ? paid.min(total) : BigDecimal.ZERO;
        RevenueState revenueState = booking.getStatus() != BookingStatus.COMPLETED || paid.signum() == 0
                ? RevenueState.NOT_REALIZED
                : remaining.signum() == 0 ? RevenueState.REALIZED : RevenueState.PARTIALLY_REALIZED;

        return new BookingPaymentSummary(
                booking.getId(),
                booking.getStatus(),
                total,
                paid,
                remaining,
                currency,
                paymentState,
                revenueState,
                provider,
                manual,
                realized,
                count);
    }

    private static BigDecimal succeededAmount(List<BusinessPayment> related, String currency) {
        return related.stream()
                .filter(Objects::nonNull)
                .filter(payment -> payment.getStatus() == BusinessPayment.Status.SUCCEEDED)
                .filter(payment -> payment.getAmount() != null)
                .filter(payment -> currency.equals(normalizeCurrency(payment.getCurrency())))
                .map(BusinessPayment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String normalizeCurrency(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return normalized.length() == 3 ? normalized : null;
    }

    private static String normalizeIdempotencyKey(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Idempotency-Key is required");
        String normalized = value.trim();
        if (normalized.length() > 180) throw new IllegalArgumentException("Idempotency-Key is too long");
        return normalized;
    }

    private void advisoryLock(UUID businessId, String idempotencyKey) {
        jdbc.execute("SELECT pg_advisory_xact_lock(" + businessId.hashCode() + "," + idempotencyKey.hashCode() + ")");
    }
}
