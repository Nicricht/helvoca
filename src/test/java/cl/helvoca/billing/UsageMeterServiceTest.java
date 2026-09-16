package cl.helvoca.billing;

import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class UsageMeterServiceTest {

    @Test
    void recordScopesInsertToAuthenticatedTenantAndIsIdempotencyAware() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        UUID businessId = UUID.randomUUID();
        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(1);
        UsageMeterService service = new UsageMeterService(jdbc, tenantProvider);

        boolean inserted = service.record(new UsageMeterService.UsageRecord(
                "voice_seconds",
                new BigDecimal("42"),
                "seconds",
                new BigDecimal("0.12340000"),
                null,
                "call_session",
                UUID.randomUUID().toString(),
                "twilio",
                "call:abc:voice",
                Instant.parse("2026-09-15T20:00:00Z")));

        assertTrue(inserted);
        verify(tenantProvider).requireBusinessId();
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).update(anyString(), params.capture());
        assertEquals(businessId, params.getValue().getValue("businessId"));
        assertEquals("VOICE_SECONDS", params.getValue().getValue("meterKey"));
        assertEquals("SECONDS", params.getValue().getValue("unit"));
        assertEquals("CALL_SESSION", params.getValue().getValue("sourceType"));
    }

    @Test
    void recordReturnsFalseWhenDatabaseRejectsDuplicateIdempotencyKey() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        when(tenantProvider.requireBusinessId()).thenReturn(UUID.randomUUID());
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(0);
        UsageMeterService service = new UsageMeterService(jdbc, tenantProvider);

        boolean inserted = service.record(validRecord());

        assertFalse(inserted);
    }

    @Test
    void recordRejectsNegativeUsageBeforeResolvingTenantOrWriting() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        UsageMeterService service = new UsageMeterService(jdbc, tenantProvider);

        UsageMeterService.UsageRecord invalid = new UsageMeterService.UsageRecord(
                "VOICE_SECONDS", new BigDecimal("-1"), "SECONDS", null, null,
                "CALL_SESSION", "call-1", null, "call-1:voice", Instant.now());

        assertThrows(IllegalArgumentException.class, () -> service.record(invalid));
        verifyNoInteractions(jdbc, tenantProvider);
    }

    @Test
    void summarizeRejectsReversedInterval() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        UsageMeterService service = new UsageMeterService(jdbc, tenantProvider);
        Instant now = Instant.now();
        assertThrows(IllegalArgumentException.class, () -> service.summarize(now, now));
        verifyNoInteractions(jdbc, tenantProvider);
    }

    private static UsageMeterService.UsageRecord validRecord() {
        return new UsageMeterService.UsageRecord(
                "OUTBOUND_MESSAGES", BigDecimal.ONE, "COUNT", null, null,
                "OUTBOUND_MESSAGE", UUID.randomUUID().toString(), "mock",
                "msg:test:sent", Instant.parse("2026-09-15T20:00:00Z"));
    }
}
