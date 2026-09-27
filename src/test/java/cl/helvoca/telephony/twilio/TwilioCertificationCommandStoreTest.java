package cl.helvoca.telephony.twilio;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class TwilioCertificationCommandStoreTest {

    @Test
    void validatesRunIdsTokensAndProviderCallSids() {
        assertTrue(TwilioCertificationCommandStore.validRunId("latency-20260927-001"));
        assertFalse(TwilioCertificationCommandStore.validRunId("short"));
        assertFalse(TwilioCertificationCommandStore.validRunId("bad token"));

        assertTrue(TwilioCertificationCommandStore.validToken(
                "11111111-1111-1111-1111-111111111111"));
        assertFalse(TwilioCertificationCommandStore.validToken("not-a-token"));

        assertTrue(TwilioCertificationCommandStore.validCallSid(
                "CA0123456789abcdef0123456789abcdef"));
        assertFalse(TwilioCertificationCommandStore.validCallSid("CA-short"));
    }

    @Test
    void enqueueStoresOnlyRunIdAndRequester() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        TwilioCertificationCommandStore store = new TwilioCertificationCommandStore(jdbc);

        assertTrue(store.enqueue("latency-api-20260927-001", "platform-user"));

        verify(jdbc).update(anyString(), any(Object[].class));
    }

    @Test
    void duplicateEnqueueReturnsFalse() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);
        TwilioCertificationCommandStore store = new TwilioCertificationCommandStore(jdbc);

        assertFalse(store.enqueue("latency-api-20260927-002", "platform-user"));
    }

    @Test
    void invalidEnqueueAndFindFailClosedBeforeDatabase() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TwilioCertificationCommandStore store = new TwilioCertificationCommandStore(jdbc);

        assertFalse(store.enqueue("bad", "platform-user"));
        assertTrue(store.find("bad").isEmpty());
        verifyNoInteractions(jdbc);
    }

    @Test
    void blankRequesterIsStoredAsUnknown() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        TwilioCertificationCommandStore store = new TwilioCertificationCommandStore(jdbc);

        assertTrue(store.enqueue("latency-api-20260927-030", "   "));
        verify(jdbc).update(anyString(), aryEq(new Object[]{"latency-api-20260927-030", "unknown"}));
    }

    @Test
    void findReturnsSanitizedCommandStatusWithoutCallbackToken() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Instant now = Instant.now();
        var status = new TwilioCertificationCommandStore.CommandStatus(
                "latency-api-20260927-003",
                "PENDING",
                "platform-user",
                now,
                null,
                null,
                null,
                null,
                null);
        when(jdbc.query(anyString(),
                org.mockito.ArgumentMatchers.<RowMapper<TwilioCertificationCommandStore.CommandStatus>>any(),
                any(Object[].class))).thenReturn(List.of(status));
        TwilioCertificationCommandStore store = new TwilioCertificationCommandStore(jdbc);

        var found = store.find("latency-api-20260927-003").orElseThrow();

        assertEquals("platform-user", found.requestedBy());
        assertNull(found.providerCallSid());
    }

    @Test
    @SuppressWarnings("unchecked")
    void findMapsDatabaseRowIncludingNullableTimestamps() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ResultSet rs = mock(ResultSet.class);
        Instant requested = Instant.parse("2026-09-27T20:00:00Z");
        Instant claimed = Instant.parse("2026-09-27T20:00:01Z");
        Instant expires = Instant.parse("2026-09-27T20:05:01Z");

        when(rs.getString("run_id")).thenReturn("latency-api-20260927-031");
        when(rs.getString("status")).thenReturn("CLAIMED");
        when(rs.getString("requested_by")).thenReturn("platform-user");
        when(rs.getTimestamp("requested_at")).thenReturn(Timestamp.from(requested));
        when(rs.getTimestamp("claimed_at")).thenReturn(Timestamp.from(claimed));
        when(rs.getTimestamp("expires_at")).thenReturn(Timestamp.from(expires));
        when(rs.getString("provider_call_sid")).thenReturn(null);
        when(rs.getTimestamp("completed_at")).thenReturn(null);
        when(rs.getString("failure_reason")).thenReturn(null);

        when(jdbc.query(anyString(),
                org.mockito.ArgumentMatchers.<RowMapper<TwilioCertificationCommandStore.CommandStatus>>any(),
                any(Object[].class))).thenAnswer(invocation -> {
                    RowMapper<TwilioCertificationCommandStore.CommandStatus> mapper = invocation.getArgument(1);
                    return List.of(mapper.mapRow(rs, 0));
                });

        TwilioCertificationCommandStore store = new TwilioCertificationCommandStore(jdbc);
        var found = store.find("latency-api-20260927-031").orElseThrow();

        assertEquals(requested, found.requestedAt());
        assertEquals(claimed, found.claimedAt());
        assertEquals(expires, found.expiresAt());
        assertNull(found.completedAt());
    }

    @Test
    void validFindWithNoRowsReturnsEmpty() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(),
                org.mockito.ArgumentMatchers.<RowMapper<TwilioCertificationCommandStore.CommandStatus>>any(),
                any(Object[].class))).thenReturn(List.of());
        TwilioCertificationCommandStore store = new TwilioCertificationCommandStore(jdbc);

        assertTrue(store.find("latency-api-20260927-032").isEmpty());
    }

    @Test
    void emptyQueueReturnsNoClaim() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<String>>any(), any(), any()))
                .thenReturn(List.of());
        TwilioCertificationCommandStore store = new TwilioCertificationCommandStore(jdbc);

        assertTrue(store.claimNext().isEmpty());
    }

    @Test
    void pendingCommandIsClaimedWithGeneratedToken() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<String>>any(), any(), any()))
                .thenReturn(List.of("latency-20260927-003"));
        TwilioCertificationCommandStore store = new TwilioCertificationCommandStore(jdbc);

        var claimed = store.claimNext().orElseThrow();

        assertEquals("latency-20260927-003", claimed.runId());
        assertTrue(TwilioCertificationCommandStore.validToken(claimed.callbackToken()));
    }

    @Test
    void invalidIdentifiersDoNotWriteDatabase() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TwilioCertificationCommandStore store = new TwilioCertificationCommandStore(jdbc);

        assertFalse(store.consumeIngressToken("bad", "bad"));
        store.recordProviderCall("short", "bad", "bad");
        store.markFailed("short", "reason");

        verifyNoInteractions(jdbc);
    }

    @Test
    void validIngressTokenIsSingleAtomicUpdate() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        TwilioCertificationCommandStore store = new TwilioCertificationCommandStore(jdbc);

        assertTrue(store.consumeIngressToken(
                "11111111-1111-1111-1111-111111111111",
                "CA0123456789abcdef0123456789abcdef"));

        verify(jdbc, times(1)).update(anyString(), any(Object[].class));
    }

    @Test
    void validProviderCallAndFailurePathsWriteExpectedRows() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TwilioCertificationCommandStore store = new TwilioCertificationCommandStore(jdbc);
        String runId = "latency-20260927-004";
        String token = "11111111-1111-1111-1111-111111111111";
        String callSid = "CA0123456789abcdef0123456789abcdef";

        store.recordProviderCall(runId, token, callSid);
        store.markFailed(runId, "provider unavailable");

        verify(jdbc, times(2)).update(anyString(), any(Object[].class));
    }

    @Test
    void failedAtomicConsumeReturnsFalse() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);
        TwilioCertificationCommandStore store = new TwilioCertificationCommandStore(jdbc);

        assertFalse(store.consumeIngressToken(
                "11111111-1111-1111-1111-111111111111",
                "CA0123456789abcdef0123456789abcdef"));
    }
}
