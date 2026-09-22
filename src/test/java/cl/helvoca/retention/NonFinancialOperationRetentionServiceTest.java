package cl.helvoca.retention;

import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NonFinancialOperationRetentionServiceTest {

    @Test
    void purgesOnlyTerminalOldNonFinancialOperationsWithoutBlockingDependenciesOrActiveLegalHold() {
        UUID businessId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-22T12:00:00Z");
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(5);

        NonFinancialOperationRetentionService service =
                new NonFinancialOperationRetentionService(
                        jdbc,
                        tenantProvider,
                        Clock.fixed(now, ZoneOffset.UTC));

        NonFinancialOperationRetentionService.Result result =
                service.purgeExpiredUnprojectedOperationsForCurrentTenant();

        assertEquals(Instant.parse("2024-09-22T12:00:00Z"), result.cutoff());
        assertEquals(5, result.operationsDeleted());

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(sql.capture(), args.capture());

        String statement = sql.getValue();
        assertTrue(statement.stripLeading().toUpperCase().startsWith("DELETE"));
        assertTrue(statement.contains("o.business_id = ?"));
        assertTrue(statement.contains("o.type <> 'PAYMENT'"));
        assertTrue(statement.contains("o.status IN ('COMPLETED', 'CANCELLED', 'EXPIRED', 'FAILED')"));
        assertTrue(statement.contains("o.updated_at < ?"));
        assertTrue(statement.contains("business_order"));
        assertTrue(statement.contains("business_quote"));
        assertTrue(statement.contains("business_lead"));
        assertTrue(statement.contains("business_request"));
        assertTrue(statement.contains("booking"));
        assertTrue(statement.contains("business_delivery"));
        assertTrue(statement.contains("business_payment"));
        assertTrue(statement.contains("target_operation_id"));
        assertTrue(statement.contains("outbound_message"));
        assertTrue(statement.contains("persistent_job"));
        assertTrue(statement.contains("retention_legal_hold"));
        assertTrue(statement.contains("h.business_id = o.business_id"));
        assertTrue(statement.contains("h.target_type = 'BUSINESS_OPERATION'"));
        assertTrue(statement.contains("h.target_id = o.id"));
        assertTrue(statement.contains("h.released_at IS NULL"));
        assertArrayEquals(
                new Object[]{businessId, Instant.parse("2024-09-22T12:00:00Z")},
                args.getValue());
    }

    @Test
    void failsClosedBeforeDeletionWhenTenantCannotBeResolved() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        when(tenantProvider.requireBusinessId()).thenThrow(new IllegalStateException("missing tenant"));

        NonFinancialOperationRetentionService service =
                new NonFinancialOperationRetentionService(
                        jdbc,
                        tenantProvider,
                        Clock.fixed(Instant.parse("2026-09-22T12:00:00Z"), ZoneOffset.UTC));

        assertThrows(
                IllegalStateException.class,
                service::purgeExpiredUnprojectedOperationsForCurrentTenant);
        verifyNoInteractions(jdbc);
    }
}
