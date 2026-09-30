package cl.helvoca.operations;

import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.payment.BusinessPayment;
import cl.helvoca.payment.BusinessPaymentRepository;
import cl.helvoca.security.TenantProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class SalesAnalyticsService {
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final Map<String, String> CHANNEL_LABELS = Map.of(
            "WHATSAPP", "WhatsApp",
            "VOICE", "Llamadas",
            "MANUAL", "Gestión manual",
            "API", "API");
    private static final Map<String, String> WEEKDAY_LABELS = Map.of(
            "MONDAY", "los lunes",
            "TUESDAY", "los martes",
            "WEDNESDAY", "los miércoles",
            "THURSDAY", "los jueves",
            "FRIDAY", "los viernes",
            "SATURDAY", "los sábados",
            "SUNDAY", "los domingos");

    private final BusinessRepository businesses;
    private final BusinessPaymentRepository payments;
    private final BusinessOrderRepository orders;
    private final BusinessOrderLineRepository lines;
    private final BusinessOperationRepository operations;
    private final TenantProvider tenant;

    public SalesAnalyticsService(BusinessRepository businesses,
                                 BusinessPaymentRepository payments,
                                 BusinessOrderRepository orders,
                                 BusinessOrderLineRepository lines,
                                 TenantProvider tenant) {
        this(businesses, payments, orders, lines, null, tenant);
    }

    @Autowired
    public SalesAnalyticsService(BusinessRepository businesses,
                                 BusinessPaymentRepository payments,
                                 BusinessOrderRepository orders,
                                 BusinessOrderLineRepository lines,
                                 BusinessOperationRepository operations,
                                 TenantProvider tenant) {
        this.businesses = businesses;
        this.payments = payments;
        this.orders = orders;
        this.lines = lines;
        this.operations = operations;
        this.tenant = tenant;
    }

    @Transactional(readOnly = true)
    public AnalyticsResponse analytics(int requestedDays) {
        int days = Math.max(1, Math.min(requestedDays, 365));
        UUID businessId = tenant.requireBusinessId();
        Business business = businesses.findById(businessId).orElseThrow();
        ZoneId zone = safeZone(business.getTimezone());

        ZonedDateTime localNow = ZonedDateTime.now(zone);
        ZonedDateTime currentStartLocal = localNow.toLocalDate().minusDays(days - 1L).atStartOfDay(zone);
        ZonedDateTime currentEndLocal = localNow.toLocalDate().plusDays(1L).atStartOfDay(zone);
        ZonedDateTime previousStartLocal = currentStartLocal.minusDays(days);

        PeriodData current = paidPeriod(
                businessId,
                currentStartLocal.toInstant(),
                currentEndLocal.toInstant(),
                true);
        PeriodData previous = paidPeriod(
                businessId,
                previousStartLocal.toInstant(),
                currentStartLocal.toInstant(),
                false);

        String primaryCurrency = current.currencyTotals.size() == 1
                ? current.currencyTotals.keySet().iterator().next()
                : null;
        BigDecimal totalRevenue = primaryCurrency == null
                ? null
                : current.currencyTotals.get(primaryCurrency);

        BigDecimal averageTicket = primaryCurrency == null || current.payments.isEmpty()
                ? null
                : totalRevenue.divide(BigDecimal.valueOf(current.payments.size()), 2, RoundingMode.HALF_UP);

        Double revenueChangePercent = comparisonPercent(primaryCurrency, totalRevenue, previous.currencyTotals);

        List<DailyPoint> daily = dailySeries(
                currentStartLocal,
                days,
                zone,
                primaryCurrency,
                current.payments);

        List<ProductStat> topProducts = productStats(
                current.ordersByOperation.values(),
                current.orderLines,
                primaryCurrency);

        List<ChannelStat> channels = channelStats(current.ordersByOperation.values());
        Peak peak = peak(current.payments, zone);

        Impact impact = recepVozImpact(current.payments, current.ordersByOperation, primaryCurrency);
        BookingRevenue bookingRevenue = bookingRevenue(
                current.bookingPayments,
                current.completedBookingsByOperation);
        String bookingCurrency = bookingRevenue.currencyTotals.size() == 1
                ? bookingRevenue.currencyTotals.keySet().iterator().next()
                : null;
        BigDecimal bookingRevenueTotal = bookingRevenue.currencyTotals.isEmpty()
                ? BigDecimal.ZERO
                : bookingCurrency == null ? null : bookingRevenue.currencyTotals.get(bookingCurrency);
        BigDecimal providerVerifiedBookingRevenue = evidenceAmount(
                bookingRevenue.providerTotals, bookingCurrency);
        BigDecimal manualRecordedBookingRevenue = evidenceAmount(
                bookingRevenue.manualTotals, bookingCurrency);
        BigDecimal recepVozBookingRevenue = evidenceAmount(
                bookingRevenue.recepVozTotals, bookingCurrency);

        List<Insight> insights = buildInsights(topProducts, channels, peak, revenueChangePercent);

        return new AnalyticsResponse(
                days,
                zone.getId(),
                primaryCurrency,
                totalRevenue,
                current.payments.size(),
                current.unitsSold,
                averageTicket,
                revenueChangePercent,
                current.currencyTotals.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> new CurrencyTotal(entry.getKey(), entry.getValue()))
                        .toList(),
                daily,
                topProducts,
                channels,
                peak.weekday,
                peak.hour,
                impact.orders,
                impact.revenue,
                bookingCurrency,
                bookingRevenue.paidBookingTargets,
                bookingRevenueTotal,
                providerVerifiedBookingRevenue,
                manualRecordedBookingRevenue,
                bookingRevenue.recepVozBookingTargets,
                recepVozBookingRevenue,
                bookingRevenue.currencyTotals.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> new CurrencyTotal(entry.getKey(), entry.getValue()))
                        .toList(),
                insights);
    }

    private PeriodData paidPeriod(UUID businessId, Instant start, Instant end, boolean includeLines) {
        List<BusinessPayment> candidates = payments
                .findAllByBusinessIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                        businessId, start, end);

        List<BusinessPayment> succeededCandidates = candidates.stream()
                .filter(payment -> payment != null
                        && payment.getStatus() == BusinessPayment.Status.SUCCEEDED
                        && payment.getTargetOperationId() != null
                        && payment.getAmount() != null
                        && payment.getAmount().signum() > 0
                        && payment.getCurrency() != null
                        && !payment.getCurrency().isBlank())
                .sorted(Comparator.comparing(
                        SalesAnalyticsService::paymentMoment,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        if (succeededCandidates.isEmpty()) {
            return PeriodData.empty();
        }

        Map<UUID, BusinessPayment> latestSucceededByTarget = new LinkedHashMap<>();
        succeededCandidates.forEach(payment ->
                latestSucceededByTarget.putIfAbsent(payment.getTargetOperationId(), payment));

        List<UUID> operationIds = List.copyOf(latestSucceededByTarget.keySet());
        Map<UUID, BusinessOrder> ordersByOperation = new LinkedHashMap<>();
        for (BusinessOrder order : orders.findAllByBusinessIdAndOperationIdIn(businessId, operationIds)) {
            if (order == null
                    || order.getOperationId() == null
                    || order.getStatus() == BusinessOrder.Status.CANCELLED) {
                continue;
            }
            ordersByOperation.put(order.getOperationId(), order);
        }

        Map<UUID, BusinessOperation> completedBookingsByOperation = new LinkedHashMap<>();
        if (operations != null) {
            for (BusinessOperation operation : operations.findAllById(operationIds)) {
                if (operation == null
                        || operation.getId() == null
                        || !businessId.equals(operation.getBusinessId())
                        || operation.getType() != BusinessOperation.Type.BOOKING
                        || operation.getStatus() != BusinessOperation.Status.COMPLETED) {
                    continue;
                }
                completedBookingsByOperation.put(operation.getId(), operation);
            }
        }

        List<BusinessPayment> acceptedPayments = latestSucceededByTarget.entrySet().stream()
                .filter(entry -> ordersByOperation.containsKey(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();

        List<BusinessPayment> acceptedBookingPayments = succeededCandidates.stream()
                .filter(payment -> completedBookingsByOperation.containsKey(payment.getTargetOperationId()))
                .sorted(Comparator.comparing(
                        SalesAnalyticsService::paymentMoment,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (BusinessPayment payment : acceptedPayments) {
            String currency = payment.getCurrency().trim().toUpperCase(Locale.ROOT);
            totals.merge(currency, payment.getAmount(), BigDecimal::add);
        }

        List<BusinessOrderLine> orderLines = List.of();
        long unitsSold = 0;
        if (includeLines && !ordersByOperation.isEmpty()) {
            List<UUID> orderIds = ordersByOperation.values().stream()
                    .map(BusinessOrder::getId)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            if (!orderIds.isEmpty()) {
                orderLines = lines.findAllByOrderIdInOrderByCreatedAtAsc(orderIds);
                unitsSold = orderLines.stream()
                        .filter(java.util.Objects::nonNull)
                        .map(BusinessOrderLine::getQuantity)
                        .filter(java.util.Objects::nonNull)
                        .filter(quantity -> quantity > 0)
                        .mapToLong(Integer::longValue)
                        .sum();
            }
        }

        return new PeriodData(
                acceptedPayments,
                acceptedBookingPayments,
                ordersByOperation,
                completedBookingsByOperation,
                orderLines,
                totals,
                unitsSold);
    }

    private static Instant paymentMoment(BusinessPayment payment) {
        if (payment == null) return null;
        return payment.getUpdatedAt() != null ? payment.getUpdatedAt() : payment.getCreatedAt();
    }

    private static ZoneId safeZone(String timezone) {
        try {
            if (timezone == null || timezone.isBlank()) return ZoneId.of("UTC");
            return ZoneId.of(timezone);
        } catch (Exception ignored) {
            return ZoneId.of("UTC");
        }
    }

    private static Double comparisonPercent(String primaryCurrency,
                                            BigDecimal currentRevenue,
                                            Map<String, BigDecimal> previousTotals) {
        if (primaryCurrency == null || currentRevenue == null || previousTotals.size() != 1) return null;
        BigDecimal previous = previousTotals.get(primaryCurrency);
        if (previous == null || previous.signum() == 0) return null;
        BigDecimal delta = currentRevenue.subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous, 1, RoundingMode.HALF_UP);
        return delta.doubleValue();
    }

    private static List<DailyPoint> dailySeries(ZonedDateTime currentStartLocal,
                                                int days,
                                                ZoneId zone,
                                                String primaryCurrency,
                                                List<BusinessPayment> payments) {
        Map<String, Long> orderCounts = new LinkedHashMap<>();
        Map<String, BigDecimal> revenues = new LinkedHashMap<>();

        for (BusinessPayment payment : payments) {
            Instant instant = payment.getCreatedAt() != null ? payment.getCreatedAt() : paymentMoment(payment);
            if (instant == null) continue;
            String key = instant.atZone(zone).toLocalDate().format(DAY_FORMAT);
            orderCounts.merge(key, 1L, Long::sum);
            if (primaryCurrency != null
                    && primaryCurrency.equalsIgnoreCase(payment.getCurrency())) {
                revenues.merge(key, payment.getAmount(), BigDecimal::add);
            }
        }

        List<DailyPoint> result = new ArrayList<>();
        for (int offset = 0; offset < days; offset++) {
            String key = currentStartLocal.toLocalDate().plusDays(offset).format(DAY_FORMAT);
            result.add(new DailyPoint(
                    key,
                    orderCounts.getOrDefault(key, 0L),
                    primaryCurrency == null ? null : revenues.getOrDefault(key, BigDecimal.ZERO)));
        }
        return List.copyOf(result);
    }

    private static List<ProductStat> productStats(Collection<BusinessOrder> orders,
                                                  List<BusinessOrderLine> lines,
                                                  String primaryCurrency) {
        if (lines.isEmpty()) return List.of();

        Map<UUID, BusinessOrder> ordersById = new LinkedHashMap<>();
        for (BusinessOrder order : orders) {
            if (order != null && order.getId() != null) ordersById.put(order.getId(), order);
        }

        Map<String, ProductAccumulator> stats = new LinkedHashMap<>();
        for (BusinessOrderLine line : lines) {
            if (line == null || line.getOrderId() == null) continue;
            BusinessOrder order = ordersById.get(line.getOrderId());
            if (order == null) continue;

            String name = line.getItemName() == null || line.getItemName().isBlank()
                    ? "Producto sin nombre"
                    : line.getItemName().trim();
            String key = line.getCatalogItemId() != null
                    ? line.getCatalogItemId().toString()
                    : name.toLowerCase(Locale.ROOT);

            ProductAccumulator accumulator = stats.computeIfAbsent(
                    key,
                    ignored -> new ProductAccumulator(line.getCatalogItemId(), name));
            int quantity = line.getQuantity() == null ? 0 : Math.max(0, line.getQuantity());
            accumulator.units += quantity;

            if (primaryCurrency != null
                    && primaryCurrency.equalsIgnoreCase(order.getCurrency())
                    && line.getLineTotal() != null) {
                accumulator.revenue = accumulator.revenue.add(line.getLineTotal());
            }
        }

        return stats.values().stream()
                .sorted(Comparator.comparingLong((ProductAccumulator value) -> value.units).reversed()
                        .thenComparing(value -> value.name))
                .limit(8)
                .map(value -> new ProductStat(
                        value.catalogItemId,
                        value.name,
                        value.units,
                        primaryCurrency == null ? null : value.revenue,
                        primaryCurrency))
                .toList();
    }

    private static List<ChannelStat> channelStats(Collection<BusinessOrder> orders) {
        Map<String, Long> counts = new LinkedHashMap<>();
        long total = 0;
        for (BusinessOrder order : orders) {
            if (order == null) continue;
            String channel = order.getSource() == null ? "UNKNOWN" : order.getSource().name();
            counts.merge(channel, 1L, Long::sum);
            total++;
        }
        final long denominator = total;
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> new ChannelStat(
                        entry.getKey(),
                        entry.getValue(),
                        denominator == 0 ? 0.0 : round1(entry.getValue() * 100.0 / denominator)))
                .toList();
    }

    private static Peak peak(List<BusinessPayment> payments, ZoneId zone) {
        Map<DayOfWeek, Long> weekdays = new LinkedHashMap<>();
        Map<Integer, Long> hours = new LinkedHashMap<>();
        for (BusinessPayment payment : payments) {
            Instant instant = payment.getCreatedAt() != null ? payment.getCreatedAt() : paymentMoment(payment);
            if (instant == null) continue;
            ZonedDateTime local = instant.atZone(zone);
            weekdays.merge(local.getDayOfWeek(), 1L, Long::sum);
            hours.merge(local.getHour(), 1L, Long::sum);
        }

        DayOfWeek weekday = weekdays.entrySet().stream()
                .max(Map.Entry.<DayOfWeek, Long>comparingByValue()
                        .thenComparing(entry -> entry.getKey().getValue()))
                .map(Map.Entry::getKey)
                .orElse(null);
        Integer hour = hours.entrySet().stream()
                .max(Map.Entry.<Integer, Long>comparingByValue()
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .orElse(null);

        return new Peak(weekday == null ? null : weekday.name(), hour);
    }

    private static Impact recepVozImpact(List<BusinessPayment> payments,
                                         Map<UUID, BusinessOrder> ordersByOperation,
                                         String primaryCurrency) {
        long count = 0;
        BigDecimal revenue = BigDecimal.ZERO;
        for (BusinessPayment payment : payments) {
            BusinessOrder order = ordersByOperation.get(payment.getTargetOperationId());
            if (order == null || order.getSource() == null) continue;
            if (order.getSource() == BusinessOrder.Source.VOICE
                    || order.getSource() == BusinessOrder.Source.WHATSAPP) {
                count++;
                if (primaryCurrency != null
                        && primaryCurrency.equalsIgnoreCase(payment.getCurrency())) {
                    revenue = revenue.add(payment.getAmount());
                }
            }
        }
        return new Impact(count, primaryCurrency == null ? null : revenue);
    }

    private static BookingRevenue bookingRevenue(
            List<BusinessPayment> bookingPayments,
            Map<UUID, BusinessOperation> completedBookingsByOperation) {
        Map<UUID, BigDecimal> paidByTarget = new LinkedHashMap<>();
        Map<String, BigDecimal> currencyTotals = new LinkedHashMap<>();
        Map<String, BigDecimal> providerTotals = new LinkedHashMap<>();
        Map<String, BigDecimal> manualTotals = new LinkedHashMap<>();
        Map<String, BigDecimal> recepVozTotals = new LinkedHashMap<>();
        java.util.Set<UUID> paidTargets = new java.util.LinkedHashSet<>();
        java.util.Set<UUID> recepVozTargets = new java.util.LinkedHashSet<>();

        for (BusinessPayment payment : bookingPayments) {
            BusinessOperation booking = completedBookingsByOperation.get(payment.getTargetOperationId());
            if (booking == null || booking.getTotal() == null || booking.getTotal().signum() <= 0) continue;

            String operationCurrency = normalizedCurrency(booking.getCurrency());
            String paymentCurrency = normalizedCurrency(payment.getCurrency());
            if (operationCurrency == null || !operationCurrency.equals(paymentCurrency)) continue;

            BigDecimal paid = paidByTarget.getOrDefault(booking.getId(), BigDecimal.ZERO);
            BigDecimal remaining = booking.getTotal().subtract(paid);
            if (remaining.signum() <= 0) continue;

            BigDecimal contribution = payment.getAmount().min(remaining);
            if (contribution.signum() <= 0) continue;

            paidByTarget.put(booking.getId(), paid.add(contribution));
            paidTargets.add(booking.getId());
            currencyTotals.merge(operationCurrency, contribution, BigDecimal::add);

            if (payment.getVerificationMethod() == BusinessPayment.VerificationMethod.MANUAL_BUSINESS) {
                manualTotals.merge(operationCurrency, contribution, BigDecimal::add);
            } else {
                providerTotals.merge(operationCurrency, contribution, BigDecimal::add);
            }

            if (booking.getSource() == BusinessOrder.Source.VOICE
                    || booking.getSource() == BusinessOrder.Source.WHATSAPP) {
                recepVozTargets.add(booking.getId());
                recepVozTotals.merge(operationCurrency, contribution, BigDecimal::add);
            }
        }

        return new BookingRevenue(
                currencyTotals,
                providerTotals,
                manualTotals,
                recepVozTotals,
                paidTargets.size(),
                recepVozTargets.size());
    }

    private static String normalizedCurrency(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return normalized.length() == 3 ? normalized : null;
    }

    private static BigDecimal evidenceAmount(Map<String, BigDecimal> totals, String primaryCurrency) {
        if (totals.isEmpty()) return BigDecimal.ZERO;
        if (primaryCurrency == null) return null;
        return totals.getOrDefault(primaryCurrency, BigDecimal.ZERO);
    }

    private static List<Insight> buildInsights(List<ProductStat> products,
                                               List<ChannelStat> channels,
                                               Peak peak,
                                               Double revenueChangePercent) {
        List<Insight> insights = new ArrayList<>();
        if (!products.isEmpty()) {
            ProductStat top = products.getFirst();
            insights.add(new Insight(
                    "TOP_PRODUCT",
                    top.name() + " es el producto más pedido del período con " + top.units() + " unidades."));
        }
        if (!channels.isEmpty()) {
            ChannelStat channel = channels.getFirst();
            insights.add(new Insight(
                    "TOP_CHANNEL",
                    channelLabel(channel.channel()) + " concentra la mayor cantidad de pedidos pagados (" +
                            formatPercent(channel.sharePercent()) + "%)."));
        }
        if (peak.weekday != null && peak.hour != null) {
            insights.add(new Insight(
                    "PEAK_TIME",
                    "La mayor actividad del período ocurre " + weekdayLabel(peak.weekday) +
                            " alrededor de las " + String.format(Locale.ROOT, "%02d:00", peak.hour) + "."));
        }
        if (revenueChangePercent != null) {
            String direction = revenueChangePercent >= 0 ? "subieron" : "bajaron";
            insights.add(new Insight(
                    "REVENUE_TREND",
                    "Los cobros confirmados " + direction + " " +
                            formatPercent(Math.abs(revenueChangePercent)) +
                            "% frente al período anterior comparable."));
        }
        return List.copyOf(insights);
    }

    private static String channelLabel(String value) {
        return CHANNEL_LABELS.getOrDefault(value, "Otros canales");
    }

    private static String weekdayLabel(String value) {
        return WEEKDAY_LABELS.getOrDefault(value, "en el período");
    }

    private static String formatPercent(double value) {
        return BigDecimal.valueOf(round1(value)).stripTrailingZeros().toPlainString();
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    public record AnalyticsResponse(
            int days,
            String timezone,
            String primaryCurrency,
            BigDecimal totalRevenue,
            long paidOrders,
            long unitsSold,
            BigDecimal averageTicket,
            Double revenueChangePercent,
            List<CurrencyTotal> currencyTotals,
            List<DailyPoint> salesOverTime,
            List<ProductStat> topProducts,
            List<ChannelStat> channels,
            String peakWeekday,
            Integer peakHour,
            long recepVozOrders,
            BigDecimal recepVozRevenue,
            String bookingCurrency,
            long paidBookings,
            BigDecimal bookingRevenue,
            BigDecimal providerVerifiedBookingRevenue,
            BigDecimal manualRecordedBookingRevenue,
            long recepVozPaidBookings,
            BigDecimal recepVozBookingRevenue,
            List<CurrencyTotal> bookingCurrencyTotals,
            List<Insight> insights) {}

    public record CurrencyTotal(String currency, BigDecimal amount) {}
    public record DailyPoint(String date, long paidOrders, BigDecimal revenue) {}
    public record ProductStat(UUID catalogItemId, String name, long units, BigDecimal revenue, String currency) {}
    public record ChannelStat(String channel, long orders, double sharePercent) {}
    public record Insight(String type, String text) {}

    private record Peak(String weekday, Integer hour) {}
    private record Impact(long orders, BigDecimal revenue) {}

    private record BookingRevenue(
            Map<String, BigDecimal> currencyTotals,
            Map<String, BigDecimal> providerTotals,
            Map<String, BigDecimal> manualTotals,
            Map<String, BigDecimal> recepVozTotals,
            long paidBookingTargets,
            long recepVozBookingTargets) {}

    private record PeriodData(
            List<BusinessPayment> payments,
            List<BusinessPayment> bookingPayments,
            Map<UUID, BusinessOrder> ordersByOperation,
            Map<UUID, BusinessOperation> completedBookingsByOperation,
            List<BusinessOrderLine> orderLines,
            Map<String, BigDecimal> currencyTotals,
            long unitsSold) {
        static PeriodData empty() {
            return new PeriodData(List.of(), List.of(), Map.of(), Map.of(), List.of(), Map.of(), 0);
        }
    }

    private static final class ProductAccumulator {
        private final UUID catalogItemId;
        private final String name;
        private long units;
        private BigDecimal revenue = BigDecimal.ZERO;

        private ProductAccumulator(UUID catalogItemId, String name) {
            this.catalogItemId = catalogItemId;
            this.name = name;
        }
    }
}
