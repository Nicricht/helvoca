package cl.helvoca.audit;

import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class AuditQueryServiceTest {

    @Test
    void returnsOnlyRecentAuditForAuthenticatedTenant() {
        UUID businessId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        AuditLogRepository repository = mock(AuditLogRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        AuditLog log = mock(AuditLog.class);

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(repository.findTop200ByBusinessIdOrderByCreatedAtDesc(businessId)).thenReturn(List.of(log));
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
}
