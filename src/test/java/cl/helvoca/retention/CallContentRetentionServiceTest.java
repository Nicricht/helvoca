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
    void purgesOnlyExpiredCallContentInsideCurrentTenant() {
        UUID businessId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-22T12:00:00Z");
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(jdbc.update(anyString(), any(), any())).thenReturn(4, 2, 3);

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
        ArgumentCaptor<Object> tenant = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> cutoff = ArgumentCaptor.forClass(Object.class);
        verify(jdbc, times(3)).update(sql.capture(), tenant.capture(), cutoff.capture());

        List<String> statements = sql.getAllValues();
        assertTrue(statements.stream().allMatch(value -> value.stripLeading().toUpperCase().startsWith("DELETE")));
        assertTrue(statements.stream().allMatch(value -> value.contains("business_id = ?")));
        assertTrue(statements.stream().allMatch(value -> value.contains("ended_at IS NOT NULL")));
        assertTrue(statements.stream().allMatch(value -> value.contains("ended_at < ?")));
        assertTrue(tenant.getAllValues().stream().allMatch(businessId::equals));
        assertTrue(cutoff.getAllValues().stream()
                .allMatch(Instant.parse("2026-06-24T12:00:00Z")::equals));
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
