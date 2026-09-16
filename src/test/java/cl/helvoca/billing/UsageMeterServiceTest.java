package cl.helvoca.billing;

import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;
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
        verify(jdbc).update(anyString(), argThat(params ->
                businessId.equals(params.getValue("businessId"))
                        && "VOICE_SECONDS".equals(params.getValue("meterKey"))
                        && "SECONDS".equals(params.getValue("unit"))
                        && "CALL_SESSION".equals(params.getValue("sourceType"))));
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
    void recordRejectsNegativeUsageBeforeWriting() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        UsageMeterService service = new UsageMeterService(jdbc, tenantProvider);

        UsageMeterService.UsageRecord invalid = new UsageMeterService.UsageRecord(
                "VOICE_SECONDS", new BigDecimal("-1"), "SECONDS", null, null,
                "CALL_SESSION", "call-1", null, "call-1:voice", Instant.now());

        assertThrows(IllegalArgumentException.class, () -> service.record(invalid));
        verifyNoInteractions(jdbc);
    }

    @Test
    void summarizeRejectsReversedInterval() {
        UsageMeterService service = new UsageMeterService(
                mock(NamedParameterJdbcTemplate.class), mock(TenantProvider.class));
        Instant now = Instant.now();
        assertThrows(IllegalArgumentException.class, () -> service.summarize(now, now));
    }

    private static UsageMeterService.UsageRecord validRecord() {
        return new UsageMeterService.UsageRecord(
                "OUTBOUND_MESSAGES", BigDecimal.ONE, "COUNT", null, null,
                "OUTBOUND_MESSAGE", UUID.randomUUID().toString(), "mock",
                "msg:test:sent", Instant.parse("2026-09-15T20:00:00Z"));
    }
}
