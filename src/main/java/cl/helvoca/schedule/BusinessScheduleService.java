package cl.helvoca.schedule;

import cl.helvoca.booking.Booking;
import cl.helvoca.booking.BookingRepository;
import cl.helvoca.booking.BookingStatus;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class BusinessScheduleService {
    private static final int SLOT_STEP_MINUTES = 30;

    private final BusinessRepository businesses;
    private final BusinessHourRepository hours;
    private final BusinessScheduleExceptionRepository exceptions;
    private final BookingRepository bookings;

    public BusinessScheduleService(BusinessRepository businesses,
                                   BusinessHourRepository hours,
                                   BusinessScheduleExceptionRepository exceptions,
                                   BookingRepository bookings) {
        this.businesses = businesses;
        this.hours = hours;
        this.exceptions = exceptions;
        this.bookings = bookings;
    }

    @Transactional(readOnly = true)
    public DailyAvailability listAvailableSlots(UUID businessId,
                                                UUID serviceId,
                                                int durationMinutes,
                                                LocalDate date,
                                                int maxResults) {
        return listAvailableSlotsWithLookahead(
                businessId, serviceId, durationMinutes, date, maxResults, 0, maxResults).requested();
    }

    /**
     * Computes the requested day and the first future day with availability in one
     * read-only transaction. Business hours, exceptions and active bookings are
     * loaded once for the entire window, avoiding one DB round-trip per candidate
     * slot and one service call per lookahead day.
     */
    @Transactional(readOnly = true)
    public AvailabilityLookahead listAvailableSlotsWithLookahead(UUID businessId,
                                                                 UUID serviceId,
                                                                 int durationMinutes,
                                                                 LocalDate date,
                                                                 int requestedMaxResults,
                                                                 int lookaheadDays,
                                                                 int lookaheadMaxResults) {
        Business business = requireBusiness(businessId);
        ZoneId zone = ZoneId.of(business.getTimezone());
        int safeLookaheadDays = Math.max(0, Math.min(lookaheadDays, 31));
        LocalDate lastDate = date.plusDays(safeLookaheadDays);

        Map<Integer, List<Window>> weeklyWindows = new HashMap<>();
        for (BusinessHour hour : hours.findAllByBusinessIdOrderByDayOfWeekAscOpenTimeAsc(businessId)) {
            weeklyWindows.computeIfAbsent(hour.getDayOfWeek(), ignored -> new ArrayList<>())
                    .add(new Window(hour.getOpenTime(), hour.getCloseTime()));
        }

        Map<LocalDate, BusinessScheduleException> exceptionByDate = new HashMap<>();
        for (BusinessScheduleException exception :
                exceptions.findAllByBusinessIdAndExceptionDateBetweenOrderByExceptionDateAsc(
                        businessId, date, lastDate)) {
            exceptionByDate.put(exception.getExceptionDate(), exception);
        }

        Instant rangeStart = date.atStartOfDay(zone).toInstant();
        Instant rangeEnd = lastDate.plusDays(1).atStartOfDay(zone).toInstant();
        List<Booking> activeBookings = bookings.findActiveOverlapsInRange(
                businessId, serviceId, rangeStart, rangeEnd, BookingStatus.CANCELLED);

        Instant now = Instant.now();
        DailyAvailability requested = availabilityForDate(
                businessId,
                serviceId,
                durationMinutes,
                date,
                requestedMaxResults,
                zone,
                weeklyWindows,
                exceptionByDate,
                activeBookings,
                now);

        DailyAvailability next = null;
        if (requested.scheduleConfigured() && requested.slots().isEmpty()) {
            for (int offset = 1; offset <= safeLookaheadDays; offset++) {
                LocalDate candidateDate = date.plusDays(offset);
                DailyAvailability candidate = availabilityForDate(
                        businessId,
                        serviceId,
                        durationMinutes,
                        candidateDate,
                        lookaheadMaxResults,
                        zone,
                        weeklyWindows,
                        exceptionByDate,
                        activeBookings,
                        now);
                if (!candidate.slots().isEmpty()) {
                    next = candidate;
                    break;
                }
            }
        }

        return new AvailabilityLookahead(requested, next);
    }

    @Transactional(readOnly = true)
    public boolean isWithinBusinessHours(UUID businessId, Instant startAt, Instant endAt) {
        if (startAt == null || endAt == null || !startAt.isBefore(endAt)) return false;
        Business business = requireBusiness(businessId);
        ZoneId zone = ZoneId.of(business.getTimezone());
        ZonedDateTime localStart = startAt.atZone(zone);
        ZonedDateTime localEnd = endAt.atZone(zone);
        if (!localStart.toLocalDate().equals(localEnd.toLocalDate())) return false;

        LocalDate date = localStart.toLocalDate();
        Optional<BusinessScheduleException> exception = exceptions.findByBusinessIdAndExceptionDate(businessId, date);
        if (exception.isEmpty() && hours.countByBusinessId(businessId) == 0) {
            // Compatibility for existing tenants until they configure business hours.
            return true;
        }

        return windowsForDate(businessId, date, exception).stream()
                .anyMatch(window -> contains(window, localStart.toLocalTime(), localEnd.toLocalTime()));
    }

    private DailyAvailability availabilityForDate(UUID businessId,
                                                  UUID serviceId,
                                                  int durationMinutes,
                                                  LocalDate date,
                                                  int maxResults,
                                                  ZoneId zone,
                                                  Map<Integer, List<Window>> weeklyWindows,
                                                  Map<LocalDate, BusinessScheduleException> exceptionByDate,
                                                  List<Booking> activeBookings,
                                                  Instant now) {
        BusinessScheduleException exception = exceptionByDate.get(date);
        boolean scheduleConfigured = !weeklyWindows.isEmpty() || exception != null;
        if (!scheduleConfigured) {
            return new DailyAvailability(false, zone.getId(), date, List.of());
        }

        List<Window> windows;
        if (exception != null) {
            windows = exception.isClosed()
                    ? List.of()
                    : List.of(new Window(exception.getOpenTime(), exception.getCloseTime()));
        } else {
            windows = weeklyWindows.getOrDefault(date.getDayOfWeek().getValue(), List.of());
        }

        List<AvailableSlot> result = new ArrayList<>();
        int limit = Math.max(1, Math.min(maxResults, 24));

        for (Window window : windows) {
            if (window.open() == null || window.close() == null) continue;
            LocalDateTime candidate = LocalDateTime.of(date, window.open());
            LocalDateTime latestStart = LocalDateTime.of(date, window.close()).minusMinutes(durationMinutes);
            while (!candidate.isAfter(latestStart) && result.size() < limit) {
                ZonedDateTime localStart = candidate.atZone(zone);
                ZonedDateTime localEnd = localStart.plusMinutes(durationMinutes);
                Instant startAt = localStart.toInstant();
                Instant endAt = localEnd.toInstant();
                if (startAt.isAfter(now)
                        && noOverlap(activeBookings, businessId, serviceId, startAt, endAt)) {
                    result.add(new AvailableSlot(startAt, endAt, localStart, localEnd));
                }
                candidate = candidate.plusMinutes(SLOT_STEP_MINUTES);
            }
            if (result.size() >= limit) break;
        }

        return new DailyAvailability(true, zone.getId(), date, List.copyOf(result));
    }

    private static boolean noOverlap(List<Booking> activeBookings,
                                     UUID businessId,
                                     UUID serviceId,
                                     Instant startAt,
                                     Instant endAt) {
        for (Booking booking : activeBookings) {
            if (!businessId.equals(booking.getBusinessId())) continue;
            if (!serviceId.equals(booking.getServiceId())) continue;
            if (booking.getStatus() == BookingStatus.CANCELLED) continue;
            if (booking.getStartAt().isBefore(endAt) && booking.getEndAt().isAfter(startAt)) {
                return false;
            }
        }
        return true;
    }

    private List<Window> windowsForDate(UUID businessId,
                                        LocalDate date,
                                        Optional<BusinessScheduleException> exception) {
        if (exception.isPresent()) {
            BusinessScheduleException value = exception.get();
            if (value.isClosed()) return List.of();
            return List.of(new Window(value.getOpenTime(), value.getCloseTime()));
        }

        return hours.findAllByBusinessIdAndDayOfWeekOrderByOpenTimeAsc(
                        businessId, date.getDayOfWeek().getValue()).stream()
                .map(hour -> new Window(hour.getOpenTime(), hour.getCloseTime()))
                .toList();
    }

    private static boolean contains(Window window, LocalTime start, LocalTime end) {
        return window.open() != null && window.close() != null
                && !start.isBefore(window.open())
                && !end.isAfter(window.close());
    }

    private Business requireBusiness(UUID businessId) {
        return businesses.findById(businessId)
                .orElseThrow(() -> new NotFoundException("Business not found"));
    }

    private record Window(LocalTime open, LocalTime close) {}

    public record AvailableSlot(Instant startAt,
                                Instant endAt,
                                ZonedDateTime localStart,
                                ZonedDateTime localEnd) {}

    public record DailyAvailability(boolean scheduleConfigured,
                                    String timezone,
                                    LocalDate date,
                                    List<AvailableSlot> slots) {}

    public record AvailabilityLookahead(DailyAvailability requested,
                                        DailyAvailability nextAvailable) {}
}
