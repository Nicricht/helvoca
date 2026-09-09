package cl.helvoca.schedule;

import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SchedulePolicyServiceTest {
    @Test
    void acceptsBookingInsideConfiguredPeriod() {
        UUID businessId = UUID.randomUUID();
        BusinessRepository businesses = mock(BusinessRepository.class);
        BusinessHourRepository hours = mock(BusinessHourRepository.class);
        BusinessScheduleExceptionRepository exceptions = mock(BusinessScheduleExceptionRepository.class);
        Business business = business();
        when(businesses.findById(businessId)).thenReturn(Optional.of(business));
        when(exceptions.findByBusinessIdAndExceptionDate(eq(businessId), any(LocalDate.class))).thenReturn(Optional.empty());
        when(hours.countByBusinessId(businessId)).thenReturn(1L);

        BusinessHour period = new BusinessHour();
        period.setBusinessId(businessId);
        period.setDayOfWeek(1);
        period.setOpenTime(LocalTime.of(9, 0));
        period.setCloseTime(LocalTime.of(18, 0));
        when(hours.findAllByBusinessIdAndDayOfWeekOrderByOpenTimeAsc(businessId, 1)).thenReturn(List.of(period));

        ZoneId zone = ZoneId.of("America/Santiago");
        Instant start = ZonedDateTime.of(2026, 9, 14, 10, 0, 0, 0, zone).toInstant();
        Instant end = ZonedDateTime.of(2026, 9, 14, 11, 0, 0, 0, zone).toInstant();

        assertTrue(new SchedulePolicyService(businesses, hours, exceptions).isOpen(businessId, start, end));
    }

    @Test
    void closedExceptionOverridesRegularHours() {
        UUID businessId = UUID.randomUUID();
        BusinessRepository businesses = mock(BusinessRepository.class);
        BusinessHourRepository hours = mock(BusinessHourRepository.class);
        BusinessScheduleExceptionRepository exceptions = mock(BusinessScheduleExceptionRepository.class);
        when(businesses.findById(businessId)).thenReturn(Optional.of(business()));

        LocalDate date = LocalDate.of(2026, 9, 14);
        BusinessScheduleException holiday = new BusinessScheduleException();
        holiday.setBusinessId(businessId);
        holiday.setExceptionDate(date);
        holiday.setClosed(true);
        when(exceptions.findByBusinessIdAndExceptionDate(businessId, date)).thenReturn(Optional.of(holiday));

        ZoneId zone = ZoneId.of("America/Santiago");
        Instant start = ZonedDateTime.of(2026, 9, 14, 10, 0, 0, 0, zone).toInstant();
        Instant end = ZonedDateTime.of(2026, 9, 14, 11, 0, 0, 0, zone).toInstant();

        assertFalse(new SchedulePolicyService(businesses, hours, exceptions).isOpen(businessId, start, end));
        verify(hours, never()).findAllByBusinessIdAndDayOfWeekOrderByOpenTimeAsc(any(), anyInt());
    }

    private static Business business() {
        Business business = new Business();
        business.setName("Helvoca Demo");
        business.setTimezone("America/Santiago");
        business.setLanguage("es");
        return business;
    }
}
