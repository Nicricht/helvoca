package cl.helvoca.schedule;

import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

@Service
public class SchedulePolicyService {
    private final BusinessRepository businesses;
    private final BusinessHourRepository hours;
    private final BusinessScheduleExceptionRepository exceptions;

    public SchedulePolicyService(BusinessRepository businesses,
                                 BusinessHourRepository hours,
                                 BusinessScheduleExceptionRepository exceptions) {
        this.businesses = businesses;
        this.hours = hours;
        this.exceptions = exceptions;
    }

    @Transactional(readOnly = true)
    public boolean isOpen(UUID businessId, Instant startAt, Instant endAt) {
        if (startAt == null || endAt == null || !startAt.isBefore(endAt)) return false;
        Business business = businesses.findById(businessId)
                .orElseThrow(() -> new NotFoundException("Business not found"));
        ZoneId zone = ZoneId.of(business.getTimezone());
        ZonedDateTime start = startAt.atZone(zone);
        ZonedDateTime end = endAt.atZone(zone);
        LocalDate date = start.toLocalDate();
        if (!date.equals(end.toLocalDate())) return false;

        var exception = exceptions.findByBusinessIdAndExceptionDate(businessId, date);
        if (exception.isPresent()) {
            BusinessScheduleException value = exception.get();
            if (value.isClosed()) return false;
            return contains(value.getOpenTime(), value.getCloseTime(), start.toLocalTime(), end.toLocalTime());
        }

        if (hours.countByBusinessId(businessId) == 0) {
            // Backwards-compatible until the tenant explicitly configures its schedule.
            return true;
        }

        int day = start.getDayOfWeek().getValue();
        return hours.findAllByBusinessIdAndDayOfWeekOrderByOpenTimeAsc(businessId, day).stream()
                .anyMatch(period -> contains(period.getOpenTime(), period.getCloseTime(),
                        start.toLocalTime(), end.toLocalTime()));
    }

    private static boolean contains(LocalTime open, LocalTime close, LocalTime start, LocalTime end) {
        return open != null && close != null && !start.isBefore(open) && !end.isAfter(close);
    }
}
