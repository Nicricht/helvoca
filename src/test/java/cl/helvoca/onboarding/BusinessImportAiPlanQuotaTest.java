package cl.helvoca.onboarding;

import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BusinessImportAiPlanQuotaTest {
    private static final UUID BUSINESS = UUID.randomUUID();
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final TenantProvider tenant = mock(TenantProvider.class);
    private final BusinessImportAiPlanQuota quota = new BusinessImportAiPlanQuota(jdbc, tenant);

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void row(String businessStatus, String subscriptionStatus,
                     Instant from, Instant until, Object active, String kind,
                     String meter, BigDecimal limit, String unit, Object hard,
                     BigDecimal used) throws Exception {
        when(tenant.requireBusinessId()).thenReturn(BUSINESS);
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("business_status")).thenReturn(businessStatus);
        when(rs.getString("subscription_status")).thenReturn(subscriptionStatus);
        when(rs.getString("plan_code")).thenReturn("BASIC");
        when(rs.getTimestamp("current_period_start")).thenReturn(from == null ? null : Timestamp.from(from));
        when(rs.getTimestamp("current_period_end")).thenReturn(until == null ? null : Timestamp.from(until));
        when(rs.getObject("plan_active")).thenReturn(active);
        when(rs.getString("kind")).thenReturn(kind);
        when(rs.getString("meter_key")).thenReturn(meter);
        when(rs.getBigDecimal("limit_value")).thenReturn(limit);
        when(rs.getString("unit")).thenReturn(unit);
        when(rs.getObject("hard_limit")).thenReturn(hard);
        doAnswer(invocation -> {
            RowMapper mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(rs, 0));
        }).when(jdbc).query(anyString(), any(RowMapper.class), eq(BUSINESS));
        when(jdbc.queryForObject(anyString(), eq(BigDecimal.class), eq(BUSINESS),
                any(Timestamp.class), any(Timestamp.class))).thenReturn(used);
    }

    private void valid(BigDecimal allowance, BigDecimal used) throws Exception {
        row("ACTIVE", "ACTIVE", Instant.now().minusSeconds(3600),
                Instant.now().plusSeconds(3600), true, "USAGE",
                "AI_IMPORT_REQUESTS", allowance, "REQUESTS", true, used);
    }

    private void enabled() {
        ReflectionTestUtils.setField(quota, "enabled", true);
    }

    @Test
    void missingSubscriptionFailsClosed() {
        enabled();
        when(tenant.requireBusinessId()).thenReturn(BUSINESS);
        when(jdbc.query(anyString(), any(RowMapper.class), eq(BUSINESS))).thenReturn(List.of());
        var state = quota.current();
        assertEquals("UNAVAILABLE", state.status());
        assertNull(state.planCode());
        assertEquals(BigDecimal.ZERO, state.remaining());
        assertFalse(quota.reserve(UUID.randomUUID()));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void noPaidAccessWhenMasterSwitchOff() throws Exception {
        valid(BigDecimal.valueOf(3), BigDecimal.ZERO);
        var state = quota.current();
        assertEquals("DISABLED", state.status());
        assertFalse(state.masterEnabled());
        assertFalse(quota.reserve(UUID.randomUUID()));
    }

    @Test
    void activeSubscriptionHasCorrectCycleAndReadOnlyBalance() throws Exception {
        enabled();
        valid(BigDecimal.valueOf(3), BigDecimal.ONE);
        var state = quota.current();
        assertEquals("AVAILABLE", state.status());
        assertEquals("BASIC", state.planCode());
        assertEquals(BigDecimal.valueOf(3), state.limit());
        assertEquals(BigDecimal.ONE, state.used());
        assertEquals(BigDecimal.valueOf(2), state.remaining());
        assertTrue(state.masterEnabled());
        assertNotNull(state.currentPeriodStart());
        assertNotNull(state.currentPeriodEnd());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void trialCanUseOnlyWithinItsActualCycle() throws Exception {
        enabled();
        row("ACTIVE", "TRIALING", Instant.now().minusSeconds(5),
                Instant.now().plusSeconds(30), true, "USAGE",
                "AI_IMPORT_REQUESTS", BigDecimal.ONE, "REQUESTS", true, BigDecimal.ZERO);
        assertEquals("AVAILABLE", quota.current().status());
    }

    @Test
    void suspendedBusinessAndInactiveSubscriptionAlwaysRefused() throws Exception {
        enabled();
        Instant start = Instant.now().minusSeconds(30);
        Instant end = Instant.now().plusSeconds(30);
        row("SUSPENDED", "ACTIVE", start, end, true, "USAGE",
                "AI_IMPORT_REQUESTS", BigDecimal.TEN, "REQUESTS", true, BigDecimal.ZERO);
        assertEquals("BUSINESS_INACTIVE", quota.current().status());
        row("ACTIVE", "PAST_DUE", start, end, true, "USAGE",
                "AI_IMPORT_REQUESTS", BigDecimal.TEN, "REQUESTS", true, BigDecimal.ZERO);
        assertEquals("SUBSCRIPTION_INACTIVE", quota.current().status());
        assertFalse(quota.reserve(UUID.randomUUID()));
    }

    @Test
    void invalidMissingFutureAndExpiredPeriodsAreDenied() throws Exception {
        enabled();
        Instant now = Instant.now();
        row("ACTIVE", "ACTIVE", null, now.plusSeconds(10), true, "USAGE",
                "AI_IMPORT_REQUESTS", BigDecimal.ONE, "REQUESTS", true, BigDecimal.ZERO);
        assertEquals("PERIOD_EXPIRED", quota.current().status());
        row("ACTIVE", "ACTIVE", now.minusSeconds(10), null, true, "USAGE",
                "AI_IMPORT_REQUESTS", BigDecimal.ONE, "REQUESTS", true, BigDecimal.ZERO);
        assertEquals("PERIOD_EXPIRED", quota.current().status());
        row("ACTIVE", "ACTIVE", now.plusSeconds(10), now.plusSeconds(20), true, "USAGE",
                "AI_IMPORT_REQUESTS", BigDecimal.ONE, "REQUESTS", true, BigDecimal.ZERO);
        assertEquals("PERIOD_EXPIRED", quota.current().status());
        row("ACTIVE", "ACTIVE", now.minusSeconds(20), now.minusSeconds(10), true, "USAGE",
                "AI_IMPORT_REQUESTS", BigDecimal.ONE, "REQUESTS", true, BigDecimal.ZERO);
        assertEquals("PERIOD_EXPIRED", quota.current().status());
    }

    @Test
    void rejectsInactivePlansAndMalformedEntitlements() throws Exception {
        enabled();
        Instant start = Instant.now().minusSeconds(100);
        Instant end = Instant.now().plusSeconds(100);
        Object[][] invalid = {
                {false, "USAGE", "AI_IMPORT_REQUESTS", BigDecimal.ONE, "REQUESTS", true},
                {true, "CAPACITY", "AI_IMPORT_REQUESTS", BigDecimal.ONE, "REQUESTS", true},
                {true, "USAGE", "WRONG", BigDecimal.ONE, "REQUESTS", true},
                {true, "USAGE", "AI_IMPORT_REQUESTS", BigDecimal.ONE, "SECONDS", true},
                {true, "USAGE", "AI_IMPORT_REQUESTS", BigDecimal.ONE, "REQUESTS", false},
                {true, "USAGE", "AI_IMPORT_REQUESTS", null, "REQUESTS", true},
                {true, "USAGE", "AI_IMPORT_REQUESTS", BigDecimal.valueOf(-1), "REQUESTS", true}
        };
        for (Object[] candidate : invalid) {
            row("ACTIVE", "ACTIVE", start, end, candidate[0], (String) candidate[1],
                    (String) candidate[2], (BigDecimal) candidate[3],
                    (String) candidate[4], candidate[5], BigDecimal.ZERO);
            assertEquals("UNAVAILABLE", quota.current().status());
        }
    }

    @Test
    void zeroAllowanceIsNotAdvertisedAndExhaustionUsesRemainingNotOverage() throws Exception {
        enabled();
        valid(BigDecimal.ZERO, BigDecimal.ZERO);
        assertEquals("NOT_INCLUDED", quota.current().status());
        assertFalse(quota.reserve(UUID.randomUUID()));
        valid(BigDecimal.ONE, BigDecimal.ONE);
        assertEquals("LIMIT_REACHED", quota.current().status());
        assertFalse(quota.reserve(UUID.randomUUID()));
        valid(BigDecimal.ONE, BigDecimal.valueOf(2));
        assertEquals(BigDecimal.ZERO, quota.current().remaining());
    }

    @Test
    void reversedSubscriptionPeriodCannotProduceOrReserveUsage() throws Exception {
        enabled();
        Instant now = Instant.now();
        row("ACTIVE", "ACTIVE", now.plusSeconds(90), now.minusSeconds(90),
                true, "USAGE", "AI_IMPORT_REQUESTS", BigDecimal.ONE,
                "REQUESTS", true, BigDecimal.ZERO);

        var snapshot = quota.current();
        assertEquals("PERIOD_EXPIRED", snapshot.status());
        assertEquals(BigDecimal.ZERO, snapshot.used());
        assertFalse(quota.reserve(UUID.randomUUID()));
        verify(jdbc, never()).queryForObject(anyString(), eq(BigDecimal.class),
                any(UUID.class), any(Timestamp.class), any(Timestamp.class));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void databaseUnknownUsageDefaultsToZeroWithoutInventingInvoiceCosts() throws Exception {
        enabled();
        valid(BigDecimal.ONE, null);
        assertEquals("AVAILABLE", quota.current().status());
    }

    @Test
    void createsExactlyOneTenantScopedIdempotentReservation() throws Exception {
        enabled();
        valid(BigDecimal.ONE, BigDecimal.ZERO);
        when(jdbc.update(contains("INSERT INTO public.usage_meter_event"), eq(BUSINESS),
                anyString(), anyString(), any(Timestamp.class))).thenReturn(1, 0);
        UUID attempt = UUID.randomUUID();
        assertTrue(quota.reserve(attempt));
        assertFalse(quota.reserve(attempt));
        verify(jdbc, times(2)).update(contains("INSERT INTO public.usage_meter_event"),
                eq(BUSINESS), eq(attempt.toString()), eq("BUSINESS_IMPORT_AI:" + attempt),
                any(Timestamp.class));
        assertThrows(IllegalArgumentException.class, () -> quota.reserve(null));
    }
}
