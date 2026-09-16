package cl.helvoca.billing;

import cl.helvoca.tenant.TenantProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UsageMeterServiceTest {
    @Test
    void recordsUsageForCurrentTenantAndKeepsIdempotencyInDatabase() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        UUID businessId = UUID.randomUUID();
        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(1);

        UsageMeterService service = new UsageMeterService(jdbc, tenantProvider);
        boolean inserted = service.record(new UsageMeterService.UsageRecord(
                "voice_seconds",
                new BigDecimal("42.75"),
                "seconds",
                new BigDecimal("0.01234567"),
                null,
                "call_session",
                "CALL-123",
                "twilio",
                "CALL_SESSION:CALL-123:VOICE_SECONDS",
                Instant.parse("2026-09-16T12:00:00Z")
        ));

        assertTrue(inserted);
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).update(anyString(), params.capture());
        assertEquals(businessId, params.getValue().getValue("businessId"));
        assertEquals("VOICE_SECONDS", params.getValue().getValue("meterKey"));
        assertEquals("SECONDS", params.getValue().getValue("unit"));
        assertEquals("CALL_SESSION", params.getValue().getValue("sourceType"));
        assertEquals("TWILIO", params.getValue().getValue("provider"));
    }

    @Test
    void rejectsInvalidUsageBeforeTouchingTenantOrDatabase() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        UsageMeterService service = new UsageMeterService(jdbc, tenantProvider);

        assertThrows(IllegalArgumentException.class, () -> service.record(
                new UsageMeterService.UsageRecord(
                        "bad key",
                        BigDecimal.ONE,
                        "COUNT",
                        null,
                        null,
                        "TEST",
                        "1",
                        null,
                        "idem-1",
                        Instant.now()
                )
        ));

        verifyNoInteractions(jdbc, tenantProvider);
    }

    @Test
    void rejectsNegativeCostsBeforeTouchingTenantOrDatabase() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        UsageMeterService service = new UsageMeterService(jdbc, tenantProvider);

        assertThrows(IllegalArgumentException.class, () -> service.record(
                new UsageMeterService.UsageRecord(
                        "API_CALLS",
                        BigDecimal.ONE,
                        "COUNT",
                        new BigDecimal("-0.01"),
                        null,
                        "TEST",
                        "1",
                        null,
                        "idem-1",
                        Instant.now()
                )
        ));

        verifyNoInteractions(jdbc, tenantProvider);
    }

    @Test
    void rejectsReversedSummaryInterval() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        UsageMeterService service = new UsageMeterService(jdbc, tenantProvider);

        Instant from = Instant.parse("2026-09-16T13:00:00Z");
        Instant to = Instant.parse("2026-09-16T12:00:00Z");

        assertThrows(IllegalArgumentException.class, () -> service.summarize(from, to));
        verifyNoInteractions(jdbc, tenantProvider);
    }

    @Test
    void summarizesUsageWithinRequestedWindowForCurrentTenant() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        UUID businessId = UUID.randomUUID();
        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(jdbc.query(
                anyString(),
                any(SqlParameterSource.class),
                ArgumentMatchers.<RowMapper<UsageMeterService.UsageSummary>>any()))
                .thenReturn(List.of(new UsageMeterService.UsageSummary(
                        "VOICE_SECONDS",
                        "SECONDS",
                        new BigDecimal("120.000000"),
                        new BigDecimal("0.03000000"),
                        null,
                        2L
                )));

        UsageMeterService service = new UsageMeterService(jdbc, tenantProvider);
        Instant from = Instant.parse("2026-09-16T10:00:00Z");
        Instant to = Instant.parse("2026-09-16T12:00:00Z");

        List<UsageMeterService.UsageSummary> result = service.summarize(from, to);

        assertEquals(1, result.size());
        assertEquals("VOICE_SECONDS", result.getFirst().meterKey());
        assertEquals(2L, result.getFirst().eventCount());
        verify(tenantProvider).requireBusinessId();
        verify(jdbc).query(
                anyString(),
                any(SqlParameterSource.class),
                ArgumentMatchers.<RowMapper<UsageMeterService.UsageSummary>>any());
    }
}
