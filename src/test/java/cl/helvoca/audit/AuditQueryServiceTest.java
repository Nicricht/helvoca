package cl.helvoca.audit;

import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuditQueryServiceTest {

    @Test
    void returnsOnlyRecentAuditForAuthenticatedTenant() {
        UUID businessId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        AuditLogRepository repository = mock(AuditLogRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        AuditLog log = auditLog(resourceId, actorId);

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(repository.findTop200ByBusinessIdOrderByCreatedAtDesc(businessId)).thenReturn(List.of(log));

        List<AuditLogResponse> result = new AuditQueryService(repository, tenantProvider).recent();

        assertEquals(1, result.size());
        AuditLogResponse item = result.getFirst();
        assertEquals("BOOKING_RESCHEDULE", item.action());
        assertEquals("Carolina Soto", item.actorName());
        assertEquals("OPERATOR", item.actorRole());
        assertEquals(resourceId, item.resourceId());
        assertEquals("2026-09-18T15:00:00Z", item.beforeState().get("startAt"));
        assertEquals("2026-09-18T16:00:00Z", item.afterState().get("startAt"));

        verify(repository).findTop200ByBusinessIdOrderByCreatedAtDesc(businessId);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void searchesOnlyAuthenticatedTenantWithNormalizedFiltersAndBoundedPage() {
        UUID businessId = UUID.randomUUID();
        AuditLogRepository repository = mock(AuditLogRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        Instant from = Instant.parse("2026-09-01T00:00:00Z");
        Instant to = Instant.parse("2026-10-01T00:00:00Z");

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(repository.search(
                eq(businessId),
                eq("Carolina"),
                eq("BOOKING_RESCHEDULE"),
                eq("BOOKING"),
                eq(from),
                eq(to),
                any(Pageable.class)))
                .thenReturn(List.of());

        List<AuditLogResponse> result = new AuditQueryService(repository, tenantProvider)
                .search("  Carolina  ", "booking_reschedule", "booking", from, to);

        assertTrue(result.isEmpty());
        var pageable = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(repository).search(
                eq(businessId),
                eq("Carolina"),
                eq("BOOKING_RESCHEDULE"),
                eq("BOOKING"),
                eq(from),
                eq(to),
                pageable.capture());
        assertEquals(0, pageable.getValue().getPageNumber());
        assertEquals(200, pageable.getValue().getPageSize());
        verifyNoMoreInteractions(repository);
    }

    @Test
    void rejectsInvalidDateRangeBeforeQueryingAuditRows() {
        UUID businessId = UUID.randomUUID();
        AuditLogRepository repository = mock(AuditLogRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        when(tenantProvider.requireBusinessId()).thenReturn(businessId);

        Instant from = Instant.parse("2026-09-20T00:00:00Z");
        Instant to = Instant.parse("2026-09-18T00:00:00Z");

        assertThrows(IllegalArgumentException.class, () ->
                new AuditQueryService(repository, tenantProvider).search(null, null, null, from, to));

        verify(tenantProvider).requireBusinessId();
        verifyNoInteractions(repository);
    }

    private static AuditLog auditLog(UUID resourceId, UUID actorId) {
        AuditLog log = mock(AuditLog.class);
        when(log.getId()).thenReturn(UUID.randomUUID());
        when(log.getAction()).thenReturn("BOOKING_RESCHEDULE");
        when(log.getResourceType()).thenReturn("BOOKING");
        when(log.getResourceId()).thenReturn(resourceId);
        when(log.getResult()).thenReturn("SUCCESS");
        when(log.getActorType()).thenReturn("HUMAN");
        when(log.getActorUserId()).thenReturn(actorId);
        when(log.getActorName()).thenReturn("Carolina Soto");
        when(log.getActorEmail()).thenReturn("carolina@example.com");
        when(log.getActorRole()).thenReturn("OPERATOR");
        when(log.getBeforeState()).thenReturn(Map.of("startAt", "2026-09-18T15:00:00Z"));
        when(log.getAfterState()).thenReturn(Map.of("startAt", "2026-09-18T16:00:00Z"));
        when(log.getCreatedAt()).thenReturn(Instant.parse("2026-09-18T18:00:00Z"));
        return log;
    }
}
