package cl.helvoca.retention;

import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LegalHoldServiceTest {

    @Test
    void checksOnlyActiveHoldForCurrentTenantAndExactTarget() {
        UUID businessId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), any(), any(), any()))
                .thenReturn(true);

        LegalHoldService service = new LegalHoldService(jdbc, tenantProvider);

        assertTrue(service.hasActiveHold(LegalHoldService.TargetType.CALL_SESSION, targetId));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForObject(
                sql.capture(),
                eq(Boolean.class),
                eq(businessId),
                eq("CALL_SESSION"),
                eq(targetId));

        assertTrue(sql.getValue().contains("business_id = ?"));
        assertTrue(sql.getValue().contains("target_type = ?"));
        assertTrue(sql.getValue().contains("target_id = ?"));
        assertTrue(sql.getValue().contains("released_at IS NULL"));
    }

    @Test
    void failsClosedBeforeDatabaseAccessWhenTenantCannotBeResolved() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        when(tenantProvider.requireBusinessId()).thenThrow(new IllegalStateException("missing tenant"));

        LegalHoldService service = new LegalHoldService(jdbc, tenantProvider);

        assertThrows(
                IllegalStateException.class,
                () -> service.hasActiveHold(
                        LegalHoldService.TargetType.CUSTOMER,
                        UUID.randomUUID()));

        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsMissingTargetBeforeTenantResolution() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        LegalHoldService service = new LegalHoldService(jdbc, tenantProvider);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.hasActiveHold(null, UUID.randomUUID()));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.hasActiveHold(LegalHoldService.TargetType.CUSTOMER, null));

        verifyNoInteractions(jdbc, tenantProvider);
    }
}
