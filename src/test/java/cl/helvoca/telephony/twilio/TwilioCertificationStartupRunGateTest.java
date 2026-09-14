package cl.helvoca.telephony.twilio;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TwilioCertificationStartupRunGateTest {

    @Test
    void requiresValidUniqueRunId() {
        assertTrue(TwilioCertificationStartupRunGate.validRunId("deploy-20260914-001"));
        assertTrue(TwilioCertificationStartupRunGate.validRunId("9c7f6f6e-630a-4b42-a513-8c57c547991c"));
        assertFalse(TwilioCertificationStartupRunGate.validRunId(null));
        assertFalse(TwilioCertificationStartupRunGate.validRunId(""));
        assertFalse(TwilioCertificationStartupRunGate.validRunId("short"));
        assertFalse(TwilioCertificationStartupRunGate.validRunId("invalid token with spaces"));
    }

    @Test
    void disabledFlagFailsClosedWithoutTouchingDatabase() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TwilioCertificationStartupRunGate gate = new TwilioCertificationStartupRunGate(
                false,
                "deploy-20260914-001",
                "outbound-test",
                jdbc);

        assertFalse(gate.authorizeOnce());
        verify(jdbc, never()).update(anyString(), eq("deploy-20260914-001"), eq("outbound-test"));
    }

    @Test
    void missingRunIdFailsClosedWithoutTouchingDatabase() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TwilioCertificationStartupRunGate gate = new TwilioCertificationStartupRunGate(
                true,
                "",
                "outbound-test",
                jdbc);

        assertFalse(gate.authorizeOnce());
        verify(jdbc, never()).update(anyString(), eq(""), eq("outbound-test"));
    }

    @Test
    void freshRunIdIsAuthorizedExactlyOnceByDatabaseClaim() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), eq("deploy-20260914-001"), eq("outbound-test"))).thenReturn(1);
        TwilioCertificationStartupRunGate gate = new TwilioCertificationStartupRunGate(
                true,
                "deploy-20260914-001",
                "outbound-test",
                jdbc);

        assertTrue(gate.authorizeOnce());
        verify(jdbc).update(anyString(), eq("deploy-20260914-001"), eq("outbound-test"));
    }

    @Test
    void consumedRunIdIsRejected() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), eq("deploy-20260914-001"), eq("outbound-test"))).thenReturn(0);
        TwilioCertificationStartupRunGate gate = new TwilioCertificationStartupRunGate(
                true,
                "deploy-20260914-001",
                "outbound-test",
                jdbc);

        assertFalse(gate.authorizeOnce());
    }

    @Test
    void databaseFailureFailsClosed() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), eq("deploy-20260914-001"), eq("outbound-test")))
                .thenThrow(new IllegalStateException("database unavailable"));
        TwilioCertificationStartupRunGate gate = new TwilioCertificationStartupRunGate(
                true,
                "deploy-20260914-001",
                "outbound-test",
                jdbc);

        assertFalse(gate.authorizeOnce());
    }
}
