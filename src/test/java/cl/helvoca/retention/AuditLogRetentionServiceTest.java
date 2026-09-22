package cl.helvoca.retention;

import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuditLogRetentionServiceTest {

    @Test
    void invokesOnlyNarrowDatabaseRetentionFunctionAfterTenantResolution() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        UUID businessId = UUID.randomUUID();

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(jdbc.queryForObject(
                "SELECT public.purge_expired_audit_log_for_current_tenant()",
                Integer.class)).thenReturn(6);

        AuditLogRetentionService service = new AuditLogRetentionService(jdbc, tenantProvider);

        AuditLogRetentionService.Result result = service.purgeExpiredForCurrentTenant();

        assertEquals(6, result.auditLogsDeleted());
        verify(tenantProvider).requireBusinessId();
        verify(jdbc).queryForObject(
                "SELECT public.purge_expired_audit_log_for_current_tenant()",
                Integer.class);
        verifyNoMoreInteractions(jdbc, tenantProvider);
    }

    @Test
    void failsClosedBeforeDatabaseFunctionWhenTenantCannotBeResolved() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);

        when(tenantProvider.requireBusinessId()).thenThrow(new IllegalStateException("missing tenant"));

        AuditLogRetentionService service = new AuditLogRetentionService(jdbc, tenantProvider);

        assertThrows(IllegalStateException.class, service::purgeExpiredForCurrentTenant);
        verifyNoInteractions(jdbc);
    }
}
