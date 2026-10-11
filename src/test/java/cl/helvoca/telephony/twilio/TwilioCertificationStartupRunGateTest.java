package cl.helvoca.telephony.twilio;

import cl.helvoca.security.TenantDatabaseContext;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
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


    @Test
    void sqlClaimIsConflictSafeAndTrimsTheRunId() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), eq("deploy-20260914-001"), eq("inbound-certification")))
                .thenReturn(1);
        TwilioCertificationStartupRunGate gate = new TwilioCertificationStartupRunGate(
                true,
                "  deploy-20260914-001  ",
                " INBOUND-CERTIFICATION ",
                providerFor(jdbc),
                new TenantDatabaseContext());

        assertTrue(gate.authorizeOnce());
        verify(jdbc).update(
                argThat(sql -> sql.contains("INSERT INTO twilio_certification_run")
                        && sql.contains("ON CONFLICT (run_id) DO NOTHING")),
                eq("deploy-20260914-001"),
                eq("inbound-certification"));
    }

    @Test
    void databaseExceptionFailsClosedAndRestoresPreviousTenantScope() {
        TenantDatabaseContext context = new TenantDatabaseContext();
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UUID tenant = UUID.randomUUID();
        when(jdbc.update(anyString(), eq("deploy-20260914-001"), eq("outbound-test")))
                .thenAnswer(invocation -> {
                    assertEquals(TenantDatabaseContext.Mode.SYSTEM, context.currentOrDenied().mode());
                    throw new IllegalStateException("database unavailable");
                });
        TwilioCertificationStartupRunGate gate = new TwilioCertificationStartupRunGate(
                true, "deploy-20260914-001", "outbound-test", providerFor(jdbc), context);

        try (TenantDatabaseContext.Scope ignored = context.useTenant(tenant)) {
            assertFalse(gate.authorizeOnce());
            assertEquals(TenantDatabaseContext.Mode.TENANT, context.currentOrDenied().mode());
            assertEquals(tenant, context.currentOrDenied().businessId());
        }
        assertEquals(TenantDatabaseContext.Mode.DENIED, context.currentOrDenied().mode());
    }

    @Test
    void failureToResolveJdbcProviderRestoresDeniedScope() {
        TenantDatabaseContext context = new TenantDatabaseContext();
        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenAnswer(invocation -> {
            assertEquals(TenantDatabaseContext.Mode.SYSTEM, context.currentOrDenied().mode());
            throw new IllegalStateException("datasource unavailable");
        });
        TwilioCertificationStartupRunGate gate = new TwilioCertificationStartupRunGate(
                true, "deploy-20260914-001", "outbound-test", provider, context);

        assertFalse(gate.authorizeOnce());
        assertEquals(TenantDatabaseContext.Mode.DENIED, context.currentOrDenied().mode());
    }

    @Test
    void proxyDoesNotInvokeRunnerWhenCertificationIsDisabled() throws Exception {
        TwilioCertificationStartupRunner runner = mock(TwilioCertificationStartupRunner.class);
        ApplicationArguments arguments = mock(ApplicationArguments.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ObjectProvider<JdbcTemplate> provider = providerFor(jdbc);
        TwilioCertificationStartupRunGate gate = new TwilioCertificationStartupRunGate(
                false, "deploy-20260914-001", "outbound-test",
                provider, new TenantDatabaseContext());

        ApplicationRunner proxy = (ApplicationRunner) gate.postProcessAfterInitialization(runner, "certificationRunner");
        proxy.run(arguments);

        verify(runner, never()).run(arguments);
        verify(provider, never()).getObject();
    }

    @Test
    void proxyInvokesRunnerOnlyAfterSuccessfulDatabaseClaim() throws Exception {
        TwilioCertificationStartupRunner runner = mock(TwilioCertificationStartupRunner.class);
        ApplicationArguments arguments = mock(ApplicationArguments.class);
        TenantDatabaseContext context = new TenantDatabaseContext();
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), eq("deploy-20260914-001"), eq("outbound-test")))
                .thenReturn(1);
        TwilioCertificationStartupRunGate gate = new TwilioCertificationStartupRunGate(
                true, "deploy-20260914-001", "outbound-test", providerFor(jdbc), context);

        ApplicationRunner proxy = (ApplicationRunner) gate.postProcessAfterInitialization(runner, "certificationRunner");
        proxy.run(arguments);

        verify(runner).run(arguments);
        assertEquals(TenantDatabaseContext.Mode.DENIED, context.currentOrDenied().mode());
    }

    @Test
    void proxyBlocksRunnerWhenRunIdWasAlreadyClaimed() throws Exception {
        TwilioCertificationStartupRunner runner = mock(TwilioCertificationStartupRunner.class);
        ApplicationArguments arguments = mock(ApplicationArguments.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), eq("deploy-20260914-001"), eq("outbound-test")))
                .thenReturn(0);
        TwilioCertificationStartupRunGate gate = new TwilioCertificationStartupRunGate(
                true, "deploy-20260914-001", "outbound-test",
                providerFor(jdbc), new TenantDatabaseContext());

        ApplicationRunner proxy = (ApplicationRunner) gate.postProcessAfterInitialization(runner, "certificationRunner");
        proxy.run(arguments);

        verify(runner, never()).run(arguments);
    }

    @Test
    void rejectsRunIdLengthsOutsideAllowedRange() {
        assertTrue(TwilioCertificationStartupRunGate.validRunId("a".repeat(8)));
        assertTrue(TwilioCertificationStartupRunGate.validRunId("a".repeat(128)));
        assertFalse(TwilioCertificationStartupRunGate.validRunId("a".repeat(7)));
        assertFalse(TwilioCertificationStartupRunGate.validRunId("a".repeat(129)));
        assertFalse(TwilioCertificationStartupRunGate.validRunId("abc\ninvalid"));
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<JdbcTemplate> providerFor(JdbcTemplate jdbc) {
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(jdbc);
        return provider;
    }
}
