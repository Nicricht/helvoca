package cl.helvoca.schedule;

import cl.helvoca.booking.BookingRepository;
import cl.helvoca.booking.BookingStatus;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.ArrayList;
import java.util.List;
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
        Business business = requireBusiness(businessId);
        ZoneId zone = ZoneId.of(business.getTimezone());
        Optional<BusinessScheduleException> exception = exceptions.findByBusinessIdAndExceptionDate(businessId, date);
        boolean scheduleConfigured = hours.countByBusinessId(businessId) > 0 || exception.isPresent();
        if (!scheduleConfigured) {
            return new DailyAvailability(false, zone.getId(), date, List.of());
        }

        List<Window> windows = windowsForDate(businessId, date, exception);
        List<AvailableSlot> result = new ArrayList<>();
        Instant now = Instant.now();
        int limit = Math.max(1, Math.min(maxResults, 24));

        for (Window window : windows) {
            LocalDateTime candidate = LocalDateTime.of(date, window.open());
            LocalDateTime latestStart = LocalDateTime.of(date, window.close()).minusMinutes(durationMinutes);
            while (!candidate.isAfter(latestStart) && result.size() < limit) {
                ZonedDateTime localStart = candidate.atZone(zone);
                ZonedDateTime localEnd = localStart.plusMinutes(durationMinutes);
                Instant startAt = localStart.toInstant();
                Instant endAt = localEnd.toInstant();
                if (startAt.isAfter(now)
                        && bookings.countOverlaps(businessId, serviceId, startAt, endAt, BookingStatus.CANCELLED, null) == 0) {
                    result.add(new AvailableSlot(startAt, endAt, localStart, localEnd));
                }
                candidate = candidate.plusMinutes(SLOT_STEP_MINUTES);
            }
            if (result.size() >= limit) break;
        }

        return new DailyAvailability(true, zone.getId(), date, List.copyOf(result));
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
}
