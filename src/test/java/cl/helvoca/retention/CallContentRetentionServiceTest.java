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

class CallContentRetentionServiceTest {

    @Test
    void purgesOnlyExpiredCallContentWithoutActiveLegalHoldInsideCurrentTenant() {
        UUID businessId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-22T12:00:00Z");
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(4, 2, 3);

        CallContentRetentionService service = new CallContentRetentionService(
                jdbc,
                tenantProvider,
                Clock.fixed(now, ZoneOffset.UTC));

        CallContentRetentionService.Result result = service.purgeExpiredContentForCurrentTenant();

        assertEquals(Instant.parse("2026-06-24T12:00:00Z"), result.cutoff());
        assertEquals(4, result.transcriptsDeleted());
        assertEquals(2, result.summariesDeleted());
        assertEquals(3, result.actionsDeleted());

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc, times(3)).update(sql.capture(), args.capture());

        List<String> statements = sql.getAllValues();
        assertTrue(statements.stream().allMatch(value -> value.stripLeading().toUpperCase().startsWith("DELETE")));
        assertTrue(statements.stream().allMatch(value -> value.contains("business_id = ?")));
        assertTrue(statements.stream().allMatch(value -> value.contains("ended_at IS NOT NULL")));
        assertTrue(statements.stream().allMatch(value -> value.contains("ended_at < ?")));
        assertTrue(statements.stream().allMatch(value -> value.contains("retention_legal_hold")));
        assertTrue(statements.stream().allMatch(value -> value.contains("h.business_id = c.business_id")));
        assertTrue(statements.stream().allMatch(value -> value.contains("h.target_type = 'CALL_SESSION'")));
        assertTrue(statements.stream().allMatch(value -> value.contains("h.target_id = c.id")));
        assertTrue(statements.stream().allMatch(value -> value.contains("h.released_at IS NULL")));
        assertTrue(args.getAllValues().stream().allMatch(values ->
                values.length == 2
                        && businessId.equals(values[0])
                        && Instant.parse("2026-06-24T12:00:00Z").equals(values[1])));
    }

    @Test
    void failsClosedBeforeDeletionWhenTenantCannotBeResolved() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        when(tenantProvider.requireBusinessId()).thenThrow(new IllegalStateException("missing tenant"));

        CallContentRetentionService service = new CallContentRetentionService(
                jdbc,
                tenantProvider,
                Clock.fixed(Instant.parse("2026-09-22T12:00:00Z"), ZoneOffset.UTC));

        assertThrows(IllegalStateException.class, service::purgeExpiredContentForCurrentTenant);
        verifyNoInteractions(jdbc);
    }
}
