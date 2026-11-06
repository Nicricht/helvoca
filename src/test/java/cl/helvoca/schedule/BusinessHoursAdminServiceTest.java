package cl.helvoca.schedule;

import cl.helvoca.audit.AuditService;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BusinessHoursAdminServiceTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static BusinessHoursRequest.Interval interval(int day, int open, int close) {
        return new BusinessHoursRequest.Interval(day, LocalTime.of(open, 0), LocalTime.of(close, 0));
    }

    private static BusinessHour hour(int day, int open, int close) {
        BusinessHour value = new BusinessHour();
        value.setBusinessId(TENANT);
        value.setDayOfWeek(day);
        value.setOpenTime(LocalTime.of(open, 0));
        value.setCloseTime(LocalTime.of(close, 0));
        return value;
    }

    private static BusinessHoursAdminService service(BusinessHourRepository hours,
                                                      TenantProvider tenant, BusinessRepository businesses,
                                                      AuditService audit) {
        when(tenant.requireBusinessId()).thenReturn(TENANT);
        when(businesses.lockByIdForUpdate(TENANT)).thenReturn(Optional.of(new Business()));
        when(hours.save(any(BusinessHour.class))).thenAnswer(i -> i.getArgument(0));
        return new BusinessHoursAdminService(hours, tenant, audit, businesses);
    }

    @Test
    void replaceScopesHoursToAuthenticatedTenantAndLocksBeforeChanging() {
        BusinessHourRepository hours = mock(BusinessHourRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);
        BusinessRepository businesses = mock(BusinessRepository.class);
        AuditService audit = mock(AuditService.class);
        var subject = service(hours, tenant, businesses, audit);

        var result = subject.replace(new BusinessHoursRequest(List.of(
                interval(1, 9, 13), interval(1, 14, 18), interval(2, 9, 18))));

        assertEquals(3, result.size());
        var order = inOrder(businesses, hours);
        order.verify(businesses).lockByIdForUpdate(TENANT);
        order.verify(hours).deleteAllByBusinessId(TENANT);
        verify(hours, times(3)).save(argThat(h -> TENANT.equals(h.getBusinessId())));
        verify(audit).success(TENANT, "BUSINESS_HOURS_REPLACE", "BUSINESS", TENANT);
    }

    @Test
    void overlappingIntervalsAreRejectedBeforeDatabaseMutation() {
        BusinessHourRepository hours = mock(BusinessHourRepository.class);
        BusinessRepository businesses = mock(BusinessRepository.class);
        var subject = service(hours, mock(TenantProvider.class), businesses, mock(AuditService.class));
        var request = new BusinessHoursRequest(List.of(interval(1, 9, 13), interval(1, 12, 18)));

        assertThrows(IllegalArgumentException.class, () -> subject.replace(request));
        verifyNoInteractions(businesses);
        verify(hours, never()).deleteAllByBusinessId(any());
        verify(hours, never()).save(any());
    }

    @Test
    void mergeSelectedPreservesOtherDaysWithFreshSnapshotAndRejectsInventedChanges() {
        BusinessHourRepository hours = mock(BusinessHourRepository.class);
        BusinessRepository businesses = mock(BusinessRepository.class);
        AuditService audit = mock(AuditService.class);
        var subject = service(hours, mock(TenantProvider.class), businesses, audit);
        when(hours.findAllByBusinessIdOrderByDayOfWeekAscOpenTimeAsc(TENANT)).thenReturn(
                List.of(hour(1, 8, 12), hour(2, 10, 19)));
        var request = new BusinessHoursSelectedRequest(
                List.of(interval(1, 9, 18)), List.of(interval(1, 8, 12)));

        var saved = subject.mergeSelected(request);

        assertEquals(2, saved.size());
        assertEquals(new BusinessHourResponse(1, LocalTime.of(9, 0), LocalTime.of(18, 0)), saved.get(0));
        assertEquals(new BusinessHourResponse(2, LocalTime.of(10, 0), LocalTime.of(19, 0)), saved.get(1));
        verify(hours).deleteAllByBusinessId(TENANT);
        verify(hours).save(argThat(h -> h.getDayOfWeek() == 2
                && h.getOpenTime().equals(LocalTime.of(10, 0))));
        verify(audit).success(TENANT, "BUSINESS_HOURS_REPLACE", "BUSINESS", TENANT);
        verify(businesses, times(2)).lockByIdForUpdate(TENANT);
    }

    @Test
    void staleSelectedDayIsConflictAndNeverMutates() {
        BusinessHourRepository hours = mock(BusinessHourRepository.class);
        var subject = service(hours, mock(TenantProvider.class), mock(BusinessRepository.class),
                mock(AuditService.class));
        when(hours.findAllByBusinessIdOrderByDayOfWeekAscOpenTimeAsc(TENANT)).thenReturn(
                List.of(hour(1, 10, 19), hour(2, 9, 18)));

        var error = assertThrows(ResponseStatusException.class, () -> subject.mergeSelected(
                new BusinessHoursSelectedRequest(List.of(interval(1, 9, 18)), List.of(interval(1, 8, 12)))));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(hours, never()).deleteAllByBusinessId(any());
        verify(hours, never()).save(any());
    }

    @Test
    void refusesEmptyDuplicateAndOutOfScopeExpectedDaysBeforeRead() {
        BusinessHourRepository hours = mock(BusinessHourRepository.class);
        BusinessRepository businesses = mock(BusinessRepository.class);
        var subject = service(hours, mock(TenantProvider.class), businesses, mock(AuditService.class));
        assertThrows(IllegalArgumentException.class, () -> subject.mergeSelected(
                new BusinessHoursSelectedRequest(List.of(), List.of())));
        assertThrows(IllegalArgumentException.class, () -> subject.mergeSelected(
                new BusinessHoursSelectedRequest(List.of(interval(1, 9, 12), interval(1, 13, 18)), List.of())));
        assertThrows(IllegalArgumentException.class, () -> subject.mergeSelected(
                new BusinessHoursSelectedRequest(List.of(interval(1, 9, 18)), List.of(interval(2, 10, 19)))));
        verifyNoInteractions(businesses);
        verify(hours, never()).deleteAllByBusinessId(any());
    }

    @Test
    void absentTenantBusinessIsRejectedWithoutWriting() {
        BusinessHourRepository hours = mock(BusinessHourRepository.class);
        BusinessRepository businesses = mock(BusinessRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);
        var subject = service(hours, tenant, businesses, mock(AuditService.class));
        when(businesses.lockByIdForUpdate(TENANT)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> subject.mergeSelected(
                new BusinessHoursSelectedRequest(List.of(interval(1, 9, 18)), List.of())));
        verify(hours, never()).deleteAllByBusinessId(any());
    }

    @Test
    void rejectsPreexistingOverlappingWeekWithoutAnyWrite() {
        BusinessHourRepository hours = mock(BusinessHourRepository.class);
        var subject = service(hours, mock(TenantProvider.class), mock(BusinessRepository.class),
                mock(AuditService.class));
        when(hours.findAllByBusinessIdOrderByDayOfWeekAscOpenTimeAsc(TENANT)).thenReturn(
                List.of(hour(2, 8, 12), hour(2, 10, 18)));
        assertThrows(IllegalArgumentException.class, () -> subject.mergeSelected(
                new BusinessHoursSelectedRequest(List.of(interval(1, 9, 18)), List.of())));
        verify(hours, never()).deleteAllByBusinessId(any());
    }

    @Test
    void acceptsExistingSelectedDayWithSeveralNonOverlappingIntervals() {
        BusinessHourRepository hours = mock(BusinessHourRepository.class);
        var subject = service(hours, mock(TenantProvider.class), mock(BusinessRepository.class),
                mock(AuditService.class));
        when(hours.findAllByBusinessIdOrderByDayOfWeekAscOpenTimeAsc(TENANT)).thenReturn(
                List.of(hour(1, 9, 12), hour(1, 14, 18)));
        var saved = subject.mergeSelected(new BusinessHoursSelectedRequest(
                List.of(interval(1, 8, 19)), List.of(interval(1, 14, 18), interval(1, 9, 12))));
        assertEquals(List.of(new BusinessHourResponse(1, LocalTime.of(8, 0), LocalTime.of(19, 0))), saved);
        verify(hours).deleteAllByBusinessId(TENANT);
    }

    @Test
    void listRemainsReadOnlyAndTenantScoped() {
        BusinessHourRepository hours = mock(BusinessHourRepository.class);
        var subject = service(hours, mock(TenantProvider.class), mock(BusinessRepository.class),
                mock(AuditService.class));
        when(hours.findAllByBusinessIdOrderByDayOfWeekAscOpenTimeAsc(TENANT)).thenReturn(List.of(hour(2, 9, 18)));
        assertEquals(1, subject.list().size());
        verify(hours).findAllByBusinessIdOrderByDayOfWeekAscOpenTimeAsc(TENANT);
        verify(hours, never()).deleteAllByBusinessId(any());
    }
}
