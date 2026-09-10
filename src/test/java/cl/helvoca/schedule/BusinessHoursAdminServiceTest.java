package cl.helvoca.schedule;

import cl.helvoca.audit.AuditService;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BusinessHoursAdminServiceTest {
    @Test
    void replaceScopesHoursToAuthenticatedTenant() {
        BusinessHourRepository hours = mock(BusinessHourRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);
        AuditService audit = mock(AuditService.class);
        UUID businessId = UUID.randomUUID();
        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(hours.save(any(BusinessHour.class))).thenAnswer(i -> i.getArgument(0));
        BusinessHoursAdminService service = new BusinessHoursAdminService(hours, tenant, audit);

        var result = service.replace(new BusinessHoursRequest(List.of(
                new BusinessHoursRequest.Interval(1, LocalTime.of(9, 0), LocalTime.of(13, 0)),
                new BusinessHoursRequest.Interval(1, LocalTime.of(14, 0), LocalTime.of(18, 0)),
                new BusinessHoursRequest.Interval(2, LocalTime.of(9, 0), LocalTime.of(18, 0))
        )));

        assertEquals(3, result.size());
        verify(hours).deleteAllByBusinessId(businessId);
        verify(hours, times(3)).save(argThat(h -> businessId.equals(h.getBusinessId())));
        verify(audit).success(businessId, "BUSINESS_HOURS_REPLACE", "BUSINESS", businessId);
    }

    @Test
    void overlappingIntervalsAreRejectedBeforeDatabaseMutation() {
        BusinessHourRepository hours = mock(BusinessHourRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);
        BusinessHoursAdminService service = new BusinessHoursAdminService(hours, tenant, mock(AuditService.class));
        when(tenant.requireBusinessId()).thenReturn(UUID.randomUUID());

        var request = new BusinessHoursRequest(List.of(
                new BusinessHoursRequest.Interval(1, LocalTime.of(9, 0), LocalTime.of(13, 0)),
                new BusinessHoursRequest.Interval(1, LocalTime.of(12, 0), LocalTime.of(18, 0))
        ));

        assertThrows(IllegalArgumentException.class, () -> service.replace(request));
        verify(hours, never()).deleteAllByBusinessId(any());
        verify(hours, never()).save(any());
    }
}
