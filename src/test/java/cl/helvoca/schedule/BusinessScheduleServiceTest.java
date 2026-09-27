package cl.helvoca.schedule;

import cl.helvoca.booking.Booking;
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
    void listsFreeSlotsInsideConfiguredHoursWithOneConflictQuery() {
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
        BusinessHour hour = hour(businessId, date, LocalTime.of(12, 0), LocalTime.of(22, 0));

        when(businesses.findById(businessId)).thenReturn(Optional.of(business));
        when(hours.findAllByBusinessIdOrderByDayOfWeekAscOpenTimeAsc(businessId)).thenReturn(List.of(hour));
        when(exceptions.findAllByBusinessIdAndExceptionDateBetweenOrderByExceptionDateAsc(
                businessId, date, date)).thenReturn(List.of());
        when(bookings.findActiveOverlapsInRange(
                eq(businessId), eq(serviceId), any(), any(), eq(BookingStatus.CANCELLED)))
                .thenReturn(List.of());

        BusinessScheduleService service = new BusinessScheduleService(businesses, hours, exceptions, bookings);
        BusinessScheduleService.DailyAvailability availability = service.listAvailableSlots(
                businessId, serviceId, 120, date, 3);

        assertTrue(availability.scheduleConfigured());
        assertEquals(3, availability.slots().size());
        assertEquals(LocalTime.of(12, 0), availability.slots().get(0).localStart().toLocalTime());
        assertEquals(LocalTime.of(12, 30), availability.slots().get(1).localStart().toLocalTime());
        assertEquals(LocalTime.of(13, 0), availability.slots().get(2).localStart().toLocalTime());

        verify(bookings, times(1)).findActiveOverlapsInRange(
                eq(businessId), eq(serviceId), any(), any(), eq(BookingStatus.CANCELLED));
        verify(bookings, never()).countOverlaps(any(), any(), any(), any(), any(), any());
    }

    @Test
    void closedExceptionProducesNoSlots() {
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
        BusinessScheduleException exception = new BusinessScheduleException();
        exception.setBusinessId(businessId);
        exception.setExceptionDate(date);
        exception.setClosed(true);

        when(businesses.findById(businessId)).thenReturn(Optional.of(business));
        when(hours.findAllByBusinessIdOrderByDayOfWeekAscOpenTimeAsc(businessId)).thenReturn(List.of());
        when(exceptions.findAllByBusinessIdAndExceptionDateBetweenOrderByExceptionDateAsc(
                businessId, date, date)).thenReturn(List.of(exception));
        when(bookings.findActiveOverlapsInRange(
                eq(businessId), eq(serviceId), any(), any(), eq(BookingStatus.CANCELLED)))
                .thenReturn(List.of());

        BusinessScheduleService service = new BusinessScheduleService(businesses, hours, exceptions, bookings);
        BusinessScheduleService.DailyAvailability availability = service.listAvailableSlots(
                businessId, serviceId, 120, date, 8);

        assertTrue(availability.scheduleConfigured());
        assertTrue(availability.slots().isEmpty());
        verify(bookings, times(1)).findActiveOverlapsInRange(
                eq(businessId), eq(serviceId), any(), any(), eq(BookingStatus.CANCELLED));
        verify(bookings, never()).countOverlaps(any(), any(), any(), any(), any(), any());
    }

    @Test
    void lookaheadLoadsBusinessHoursExceptionsAndBookingsOnlyOnce() {
        UUID businessId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        ZoneId zone = ZoneId.of("America/Santiago");
        LocalDate requestedDate = LocalDate.now(zone).plusDays(2);
        LocalDate nextDate = requestedDate.plusDays(1);

        BusinessRepository businesses = mock(BusinessRepository.class);
        BusinessHourRepository hours = mock(BusinessHourRepository.class);
        BusinessScheduleExceptionRepository exceptions = mock(BusinessScheduleExceptionRepository.class);
        BookingRepository bookings = mock(BookingRepository.class);

        Business business = new Business();
        business.setTimezone(zone.getId());
        BusinessHour nextDayHours = hour(
                businessId, nextDate, LocalTime.of(10, 0), LocalTime.of(12, 0));

        when(businesses.findById(businessId)).thenReturn(Optional.of(business));
        when(hours.findAllByBusinessIdOrderByDayOfWeekAscOpenTimeAsc(businessId))
                .thenReturn(List.of(nextDayHours));
        when(exceptions.findAllByBusinessIdAndExceptionDateBetweenOrderByExceptionDateAsc(
                businessId, requestedDate, requestedDate.plusDays(7))).thenReturn(List.of());
        when(bookings.findActiveOverlapsInRange(
                eq(businessId), eq(serviceId), any(), any(), eq(BookingStatus.CANCELLED)))
                .thenReturn(List.of());

        BusinessScheduleService service = new BusinessScheduleService(businesses, hours, exceptions, bookings);
        BusinessScheduleService.AvailabilityLookahead lookup = service.listAvailableSlotsWithLookahead(
                businessId, serviceId, 30, requestedDate, 8, 7, 3);

        assertTrue(lookup.requested().scheduleConfigured());
        assertTrue(lookup.requested().slots().isEmpty());
        assertNotNull(lookup.nextAvailable());
        assertEquals(nextDate, lookup.nextAvailable().date());
        assertEquals(3, lookup.nextAvailable().slots().size());

        verify(businesses, times(1)).findById(businessId);
        verify(hours, times(1)).findAllByBusinessIdOrderByDayOfWeekAscOpenTimeAsc(businessId);
        verify(exceptions, times(1)).findAllByBusinessIdAndExceptionDateBetweenOrderByExceptionDateAsc(
                businessId, requestedDate, requestedDate.plusDays(7));
        verify(bookings, times(1)).findActiveOverlapsInRange(
                eq(businessId), eq(serviceId), any(), any(), eq(BookingStatus.CANCELLED));
        verify(bookings, never()).countOverlaps(any(), any(), any(), any(), any(), any());
    }

    @Test
    void bulkConflictSnapshotRemovesOverlappingCandidateSlots() {
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
        BusinessHour hour = hour(businessId, date, LocalTime.of(12, 0), LocalTime.of(14, 0));

        Booking occupied = new Booking();
        occupied.setBusinessId(businessId);
        occupied.setServiceId(serviceId);
        occupied.setStatus(BookingStatus.CONFIRMED);
        occupied.setStartAt(date.atTime(12, 30).atZone(zone).toInstant());
        occupied.setEndAt(date.atTime(13, 0).atZone(zone).toInstant());

        when(businesses.findById(businessId)).thenReturn(Optional.of(business));
        when(hours.findAllByBusinessIdOrderByDayOfWeekAscOpenTimeAsc(businessId)).thenReturn(List.of(hour));
        when(exceptions.findAllByBusinessIdAndExceptionDateBetweenOrderByExceptionDateAsc(
                businessId, date, date)).thenReturn(List.of());
        when(bookings.findActiveOverlapsInRange(
                eq(businessId), eq(serviceId), any(), any(), eq(BookingStatus.CANCELLED)))
                .thenReturn(List.of(occupied));

        BusinessScheduleService service = new BusinessScheduleService(businesses, hours, exceptions, bookings);
        BusinessScheduleService.DailyAvailability availability = service.listAvailableSlots(
                businessId, serviceId, 30, date, 8);

        assertTrue(availability.slots().stream()
                .noneMatch(slot -> slot.localStart().toLocalTime().equals(LocalTime.of(12, 30))));
        assertTrue(availability.slots().stream()
                .anyMatch(slot -> slot.localStart().toLocalTime().equals(LocalTime.of(12, 0))));
        assertTrue(availability.slots().stream()
                .anyMatch(slot -> slot.localStart().toLocalTime().equals(LocalTime.of(13, 0))));
    }

    private static BusinessHour hour(UUID businessId,
                                     LocalDate date,
                                     LocalTime open,
                                     LocalTime close) {
        BusinessHour hour = new BusinessHour();
        hour.setBusinessId(businessId);
        hour.setDayOfWeek(date.getDayOfWeek().getValue());
        hour.setOpenTime(open);
        hour.setCloseTime(close);
        return hour;
    }
}
