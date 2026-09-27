package cl.helvoca.telephony.twilio;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

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
        when(jdbc.update(anyString(), any(), any(), any())).thenReturn(1);
        TwilioCertificationCommandStore store = new TwilioCertificationCommandStore(jdbc);

        assertTrue(store.consumeIngressToken(
                "11111111-1111-1111-1111-111111111111",
                "CA0123456789abcdef0123456789abcdef"));

        verify(jdbc, times(1)).update(anyString(), any(), any(), any());
    }

    @Test
    void failedAtomicConsumeReturnsFalse() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(), any(), any())).thenReturn(0);
        TwilioCertificationCommandStore store = new TwilioCertificationCommandStore(jdbc);

        assertFalse(store.consumeIngressToken(
                "11111111-1111-1111-1111-111111111111",
                "CA0123456789abcdef0123456789abcdef"));
    }
}
