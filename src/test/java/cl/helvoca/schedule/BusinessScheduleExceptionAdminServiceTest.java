package cl.helvoca.schedule;

import cl.helvoca.audit.AuditService;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BusinessScheduleExceptionAdminServiceTest {

    @Test
    void upsertClosedDateIsTenantScopedAndAuditsWithoutReason() {
        UUID businessId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 12, 25);
        BusinessScheduleExceptionRepository repository = mock(BusinessScheduleExceptionRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);
        AuditService audit = mock(AuditService.class);

        BusinessScheduleException existing = new BusinessScheduleException();
        existing.setBusinessId(businessId);
        existing.setExceptionDate(date);
        existing.setClosed(false);
        existing.setOpenTime(LocalTime.of(10, 0));
        existing.setCloseTime(LocalTime.of(14, 0));
        existing.setReason("Motivo anterior sensible");

        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(repository.findByBusinessIdAndExceptionDate(businessId, date)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(BusinessScheduleException.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BusinessScheduleExceptionAdminService service =
                new BusinessScheduleExceptionAdminService(repository, tenant, audit);

        var result = service.upsert(
                date,
                new BusinessScheduleExceptionAdminService.Update(
                        true, null, null, "Dirección privada que no debe auditarse"));

        assertTrue(result.closed());
        assertNull(result.openTime());
        assertNull(result.closeTime());
        assertEquals("Dirección privada que no debe auditarse", result.reason());
        verify(repository).findByBusinessIdAndExceptionDate(businessId, date);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> before = ArgumentCaptor.forClass(Map.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> after = ArgumentCaptor.forClass(Map.class);

        verify(audit).humanSuccess(
                eq(businessId),
                eq("BUSINESS_SCHEDULE_EXCEPTION_UPDATE"),
                eq("BUSINESS_SCHEDULE_EXCEPTION"),
                isNull(),
                before.capture(),
                after.capture());

        assertFalse(before.getValue().containsKey("reason"));
        assertFalse(after.getValue().containsKey("reason"));
        assertFalse(before.getValue().containsValue("Motivo anterior sensible"));
        assertFalse(after.getValue().containsValue("Dirección privada que no debe auditarse"));
        assertEquals(Boolean.TRUE, after.getValue().get("closed"));
    }

    @Test
    void upsertSpecialHoursPersistsOpeningWindow() {
        UUID businessId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 12, 31);
        BusinessScheduleExceptionRepository repository = mock(BusinessScheduleExceptionRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);
        AuditService audit = mock(AuditService.class);

        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(repository.findByBusinessIdAndExceptionDate(businessId, date)).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(BusinessScheduleException.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BusinessScheduleExceptionAdminService service =
                new BusinessScheduleExceptionAdminService(repository, tenant, audit);

        var result = service.upsert(
                date,
                new BusinessScheduleExceptionAdminService.Update(
                        false, LocalTime.of(9, 30), LocalTime.of(13, 0), "Horario fin de año"));

        assertFalse(result.closed());
        assertEquals(LocalTime.of(9, 30), result.openTime());
        assertEquals(LocalTime.of(13, 0), result.closeTime());

        ArgumentCaptor<BusinessScheduleException> saved = ArgumentCaptor.forClass(BusinessScheduleException.class);
        verify(repository).saveAndFlush(saved.capture());
        assertEquals(businessId, saved.getValue().getBusinessId());
        assertEquals(date, saved.getValue().getExceptionDate());
        assertEquals("Horario fin de año", saved.getValue().getReason());
        verify(audit).humanSuccess(
                eq(businessId),
                eq("BUSINESS_SCHEDULE_EXCEPTION_CREATE"),
                eq("BUSINESS_SCHEDULE_EXCEPTION"),
                isNull(),
                isNull(),
                anyMap());
    }

    @Test
    void rejectsMalformedSpecialHoursBeforeTenantOrRepositoryAccess() {
        BusinessScheduleExceptionRepository repository = mock(BusinessScheduleExceptionRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);
        AuditService audit = mock(AuditService.class);
        BusinessScheduleExceptionAdminService service =
                new BusinessScheduleExceptionAdminService(repository, tenant, audit);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.upsert(
                        LocalDate.of(2026, 10, 1),
                        new BusinessScheduleExceptionAdminService.Update(
                                false, LocalTime.of(18, 0), LocalTime.of(9, 0), null)));

        assertEquals("Opening time must be before closing time", ex.getMessage());
        verifyNoInteractions(tenant, repository, audit);
    }
}
