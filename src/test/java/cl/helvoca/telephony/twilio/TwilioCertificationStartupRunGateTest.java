package cl.helvoca.telephony.twilio;

import cl.helvoca.security.TenantDatabaseContext;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void disabledFlagFailsClosedWithoutResolvingDatabase() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ObjectProvider<JdbcTemplate> provider = providerFor(jdbc);
        TwilioCertificationStartupRunGate gate = new TwilioCertificationStartupRunGate(
                false,
                "deploy-20260914-001",
                "outbound-test",
                provider, new TenantDatabaseContext());

        assertFalse(gate.authorizeOnce());
        verify(provider, never()).getObject();
        verify(jdbc, never()).update(anyString(), eq("deploy-20260914-001"), eq("outbound-test"));
    }

    @Test
    void missingRunIdFailsClosedWithoutResolvingDatabase() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ObjectProvider<JdbcTemplate> provider = providerFor(jdbc);
        TwilioCertificationStartupRunGate gate = new TwilioCertificationStartupRunGate(
                true,
                "",
                "outbound-test",
                provider, new TenantDatabaseContext());

        assertFalse(gate.authorizeOnce());
        verify(provider, never()).getObject();
        verify(jdbc, never()).update(anyString(), eq(""), eq("outbound-test"));
    }

    @Test
    void freshRunIdIsAuthorizedExactlyOnceByDatabaseClaim() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), eq("deploy-20260914-001"), eq("outbound-test"))).thenReturn(1);
        ObjectProvider<JdbcTemplate> provider = providerFor(jdbc);
        TwilioCertificationStartupRunGate gate = new TwilioCertificationStartupRunGate(
                true,
                "deploy-20260914-001",
                "outbound-test",
                provider, new TenantDatabaseContext());

        assertTrue(gate.authorizeOnce());
        verify(provider).getObject();
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
                providerFor(jdbc), new TenantDatabaseContext());

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
                providerFor(jdbc), new TenantDatabaseContext());

        assertFalse(gate.authorizeOnce());
    }

    @Test
    void databaseClaimUsesSystemRoleAndRestoresDeniedContext() {
        TenantDatabaseContext context = new TenantDatabaseContext();
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), eq("deploy-20260914-001"), eq("outbound-test")))
                .thenAnswer(invocation -> {
                    assertEquals(TenantDatabaseContext.Mode.SYSTEM, context.currentOrDenied().mode());
                    return 1;
                });
        TwilioCertificationStartupRunGate gate = new TwilioCertificationStartupRunGate(
                true,
                "deploy-20260914-001",
                "outbound-test",
                providerFor(jdbc),
                context);

        assertEquals(TenantDatabaseContext.Mode.DENIED, context.currentOrDenied().mode());
        assertTrue(gate.authorizeOnce());
        assertEquals(TenantDatabaseContext.Mode.DENIED, context.currentOrDenied().mode());
        verify(jdbc).update(anyString(), eq("deploy-20260914-001"), eq("outbound-test"));
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<JdbcTemplate> providerFor(JdbcTemplate jdbc) {
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(jdbc);
        return provider;
    }
}
