package cl.helvoca.schedule;

import cl.helvoca.booking.BookingRepository;
import cl.helvoca.booking.BookingStatus;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BusinessScheduleServiceTest {
    @Test
    void listsFreeSlotsInsideConfiguredHours() {
        UUID businessId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        ZoneId zone = ZoneId.of("America/Santiago");
        LocalDate date = LocalDate.now(zone).plusDays(1);

        BusinessRepository businesses = mock(BusinessRepository.class);
        BusinessHourRepository hours = mock(BusinessHourRepository.class);
        BusinessScheduleExceptionRepository exceptions = mock(BusinessScheduleExceptionRepository.class);
        BookingRepository bookings = mock(BookingRepository.class);

        Business business = new Business();
        business.setTimezone(zone.getId());
        BusinessHour hour = new BusinessHour();
        hour.setBusinessId(businessId);
        hour.setDayOfWeek(date.getDayOfWeek().getValue());
        hour.setOpenTime(LocalTime.of(12, 0));
        hour.setCloseTime(LocalTime.of(22, 0));

        when(businesses.findById(businessId)).thenReturn(Optional.of(business));
        when(exceptions.findByBusinessIdAndExceptionDate(businessId, date)).thenReturn(Optional.empty());
        when(hours.countByBusinessId(businessId)).thenReturn(1L);
        when(hours.findAllByBusinessIdAndDayOfWeekOrderByOpenTimeAsc(businessId, date.getDayOfWeek().getValue()))
                .thenReturn(List.of(hour));
        when(bookings.countOverlaps(eq(businessId), eq(serviceId), any(), any(), eq(BookingStatus.CANCELLED), isNull()))
                .thenReturn(0L);

        BusinessScheduleService service = new BusinessScheduleService(businesses, hours, exceptions, bookings);
        BusinessScheduleService.DailyAvailability availability = service.listAvailableSlots(
                businessId, serviceId, 120, date, 3);

        assertTrue(availability.scheduleConfigured());
        assertEquals(3, availability.slots().size());
        assertEquals(LocalTime.of(12, 0), availability.slots().get(0).localStart().toLocalTime());
        assertEquals(LocalTime.of(12, 30), availability.slots().get(1).localStart().toLocalTime());
        assertEquals(LocalTime.of(13, 0), availability.slots().get(2).localStart().toLocalTime());
    }

    @Test
    void closedExceptionProducesNoSlots() {
        UUID businessId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        LocalDate date = LocalDate.now(ZoneId.of("America/Santiago")).plusDays(1);

        BusinessRepository businesses = mock(BusinessRepository.class);
        BusinessHourRepository hours = mock(BusinessHourRepository.class);
        BusinessScheduleExceptionRepository exceptions = mock(BusinessScheduleExceptionRepository.class);
        BookingRepository bookings = mock(BookingRepository.class);

        Business business = new Business();
        business.setTimezone("America/Santiago");
        BusinessScheduleException exception = new BusinessScheduleException();
        exception.setBusinessId(businessId);
        exception.setExceptionDate(date);
        exception.setClosed(true);

        when(businesses.findById(businessId)).thenReturn(Optional.of(business));
        when(exceptions.findByBusinessIdAndExceptionDate(businessId, date)).thenReturn(Optional.of(exception));
        when(hours.countByBusinessId(businessId)).thenReturn(1L);

        BusinessScheduleService service = new BusinessScheduleService(businesses, hours, exceptions, bookings);
        BusinessScheduleService.DailyAvailability availability = service.listAvailableSlots(
                businessId, serviceId, 120, date, 8);

        assertTrue(availability.scheduleConfigured());
        assertTrue(availability.slots().isEmpty());
        verifyNoInteractions(bookings);
    }
}
