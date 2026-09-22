package cl.helvoca.retention;

import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CallSessionRetentionServiceTest {

    @Test
    void purgesOnlyOldEndedUnreferencedSessionsForCurrentTenant() {
        UUID businessId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-22T12:00:00Z");
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(3, 2);

        CallSessionRetentionService service = new CallSessionRetentionService(
                jdbc,
                tenantProvider,
                Clock.fixed(now, ZoneOffset.UTC));

        CallSessionRetentionService.Result result = service.purgeExpiredSessionsForCurrentTenant();

        assertEquals(Instant.parse("2026-03-26T12:00:00Z"), result.cutoff());
        assertEquals(3, result.actionsDeleted());
        assertEquals(2, result.sessionsDeleted());

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc, times(2)).update(sql.capture(), args.capture());

        List<String> statements = sql.getAllValues();
        assertTrue(statements.get(0).stripLeading().toUpperCase().startsWith("DELETE FROM CALL_ACTION"));
        assertTrue(statements.get(1).stripLeading().toUpperCase().startsWith("DELETE FROM CALL_SESSION"));

        for (String statement : statements) {
            assertTrue(statement.contains("c.business_id = ?"));
            assertTrue(statement.contains("c.ended_at IS NOT NULL"));
            assertTrue(statement.contains("c.ended_at < ?"));
            assertTrue(statement.contains("business_request"));
            assertTrue(statement.contains("unanswered_question"));
        }

        Object[] expectedArgs = {
                businessId,
                Instant.parse("2026-03-26T12:00:00Z")
        };
        assertArrayEquals(expectedArgs, args.getAllValues().get(0));
        assertArrayEquals(expectedArgs, args.getAllValues().get(1));
    }

    @Test
    void failsClosedBeforeDeletionWhenTenantCannotBeResolved() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        when(tenantProvider.requireBusinessId()).thenThrow(new IllegalStateException("missing tenant"));

        CallSessionRetentionService service = new CallSessionRetentionService(
                jdbc,
                tenantProvider,
                Clock.fixed(Instant.parse("2026-09-22T12:00:00Z"), ZoneOffset.UTC));

        assertThrows(IllegalStateException.class, service::purgeExpiredSessionsForCurrentTenant);
        verifyNoInteractions(jdbc);
    }
}
