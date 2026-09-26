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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PilotMetricsService {
    private static final String SIMULATOR_PROVIDER = "simulator";
    private static final String HUMAN_TRANSFERRED = "HUMAN_TRANSFERRED";

    private final BusinessRepository businesses;
    private final CallSessionRepository calls;
    private final BookingRepository bookings;
    private final MessagingConversationRepository conversations;
    private final BusinessOrderRepository orders;
    private final BusinessPaymentRepository payments;
    private final TenantProvider tenantProvider;

    public PilotMetricsService(BusinessRepository businesses,
                               CallSessionRepository calls,
                               BookingRepository bookings,
                               MessagingConversationRepository conversations,
                               BusinessOrderRepository orders,
                               BusinessPaymentRepository payments,
                               TenantProvider tenantProvider) {
        this.businesses = businesses;
        this.calls = calls;
        this.bookings = bookings;
        this.conversations = conversations;
        this.orders = orders;
        this.payments = payments;
        this.tenantProvider = tenantProvider;
    }

    @Transactional(readOnly = true)
    public Metrics metrics() {
        UUID businessId = tenantProvider.requireBusinessId();
        Business business = businesses.findById(businessId).orElseThrow();
        ZoneId zone = ZoneId.of(business.getTimezone());
        ZonedDateTime now = ZonedDateTime.now(zone);

        Instant todayStart = now.toLocalDate().atStartOfDay(zone).toInstant();
        Instant tomorrowStart = now.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant();
        Instant weekStart = now.toLocalDate().minusDays(6).atStartOfDay(zone).toInstant();

        List<Booking> allBookings = bookings.findAllByBusinessIdOrderByStartAtDesc(businessId);
        List<MessagingConversation> whatsapp = conversations
                .findAllByBusinessIdAndChannelOrderByLastMessageAtDesc(businessId, "whatsapp");

        Window today = window(
                businessId, "TODAY", "Hoy", todayStart, tomorrowStart, allBookings, whatsapp);
        Window last7Days = window(
                businessId, "LAST_7_DAYS", "Últimos 7 días", weekStart, tomorrowStart, allBookings, whatsapp);

        return new Metrics(
                business.getName(),
                business.getTimezone(),
                now.toOffsetDateTime().toString(),
                today,
                last7Days);
    }

    private Window window(UUID businessId,
                          String code,
                          String label,
                          Instant start,
                          Instant end,
                          List<Booking> allBookings,
                          List<MessagingConversation> whatsapp) {
        long callCount = calls
                .countByBusinessIdAndCertificationFalseAndTelephonyProviderNotAndStartedAtGreaterThanEqualAndStartedAtLessThan(
                        businessId, SIMULATOR_PROVIDER, start, end);
        long callFailures = calls
                .countByBusinessIdAndCertificationFalseAndTelephonyProviderNotAndStatusInAndStartedAtGreaterThanEqualAndStartedAtLessThan(
                        businessId,
                        SIMULATOR_PROVIDER,
                        List.of(CallStatus.FAILED, CallStatus.NO_ANSWER),
                        start,
                        end);
        long humanTransfers = calls
                .countByBusinessIdAndCertificationFalseAndTelephonyProviderNotAndResolutionAndStartedAtGreaterThanEqualAndStartedAtLessThan(
                        businessId, SIMULATOR_PROVIDER, HUMAN_TRANSFERRED, start, end);

        long whatsappConversations = whatsapp.stream()
                .filter(item -> between(item.getLastMessageAt(), start, end))
                .count();

        long bookingCount = allBookings.stream()
                .filter(item -> item.getStatus() != BookingStatus.CANCELLED)
                .filter(item -> between(item.getCreatedAt(), start, end))
                .count();

        List<BusinessOrder> windowOrders = orders
                .findAllByBusinessIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                        businessId, start, end);
        long orderCount = windowOrders.stream()
                .filter(item -> item.getStatus() != BusinessOrder.Status.CANCELLED)
                .count();

        List<BusinessPayment> windowPayments = payments
                .findAllByBusinessIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                        businessId, start, end);
        long paymentAttempts = windowPayments.size();
        long successfulPayments = windowPayments.stream()
                .filter(item -> item.getStatus() == BusinessPayment.Status.SUCCEEDED)
                .count();
        long pendingPayments = windowPayments.stream()
                .filter(item -> item.getStatus() == BusinessPayment.Status.REQUIRES_ACTION
                        || item.getStatus() == BusinessPayment.Status.PENDING)
                .count();
        long failedPayments = windowPayments.stream()
                .filter(item -> item.getStatus() == BusinessPayment.Status.FAILED
                        || item.getStatus() == BusinessPayment.Status.CANCELLED
                        || item.getStatus() == BusinessPayment.Status.EXPIRED)
                .count();
        long refundedPayments = windowPayments.stream()
                .filter(item -> item.getStatus() == BusinessPayment.Status.REFUNDED)
                .count();

        Map<String, BigDecimal> confirmedRevenueByCurrency = new LinkedHashMap<>();
        windowPayments.stream()
                .filter(item -> item.getStatus() == BusinessPayment.Status.SUCCEEDED)
                .filter(item -> item.getAmount() != null)
                .forEach(item -> {
                    String currency = item.getCurrency() == null || item.getCurrency().isBlank()
                            ? "N/A"
                            : item.getCurrency().trim().toUpperCase();
                    confirmedRevenueByCurrency.merge(currency, item.getAmount(), BigDecimal::add);
                });

        BigDecimal paidOrderConversionPct = percentage(successfulPayments, orderCount);
        BigDecimal paymentSuccessRatePct = percentage(successfulPayments, paymentAttempts);
        BigDecimal callFailureRatePct = percentage(callFailures, callCount);
        BigDecimal humanTransferRatePct = percentage(humanTransfers, callCount);

        return new Window(
                code,
                label,
                start,
                end,
                callCount,
                whatsappConversations,
                bookingCount,
                orderCount,
                paymentAttempts,
                successfulPayments,
                pendingPayments,
                failedPayments,
                refundedPayments,
                humanTransfers,
                callFailures,
                Map.copyOf(confirmedRevenueByCurrency),
                paidOrderConversionPct,
                paymentSuccessRatePct,
                callFailureRatePct,
                humanTransferRatePct);
    }

    private static BigDecimal percentage(long numerator, long denominator) {
        if (denominator <= 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 1, RoundingMode.HALF_UP);
    }

    private static boolean between(Instant value, Instant start, Instant end) {
        return value != null && !value.isBefore(start) && value.isBefore(end);
    }

    public record Metrics(
            String businessName,
            String timezone,
            String localNow,
            Window today,
            Window last7Days) {}

    public record Window(
            String code,
            String label,
            Instant from,
            Instant to,
            long calls,
            long whatsappConversations,
            long bookings,
            long orders,
            long paymentAttempts,
            long successfulPayments,
            long pendingPayments,
            long failedPayments,
            long refundedPayments,
            long humanTransfers,
            long callFailures,
            Map<String, BigDecimal> confirmedRevenueByCurrency,
            BigDecimal paidOrderConversionPct,
            BigDecimal paymentSuccessRatePct,
            BigDecimal callFailureRatePct,
            BigDecimal humanTransferRatePct) {}
}
