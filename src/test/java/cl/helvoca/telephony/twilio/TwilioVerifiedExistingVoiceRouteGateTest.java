package cl.helvoca.telephony.twilio;

import cl.helvoca.operations.ControlledPilotExternalEffectGuard;
import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.security.TenantDatabaseContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Synthetic identifiers only. Neither provider requests nor DB connections. */
class TwilioVerifiedExistingVoiceRouteGateTest {
    private static final UUID BUSINESS = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    private static final UUID PHONE = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final String ACCOUNT = "AC" + "a".repeat(32);

    private JdbcTemplate jdbc;
    private ControlledPilotExternalEffectGuard pilot;
    private TenantDatabaseContext scope;
    private PhoneNumber number;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        pilot = mock(ControlledPilotExternalEffectGuard.class);
        scope = new TenantDatabaseContext();
        number = mock(PhoneNumber.class);
        when(number.getBusinessId()).thenReturn(BUSINESS);
        when(number.getId()).thenReturn(PHONE);
        when(number.getProvider()).thenReturn("TWILIO");
        when(number.isActive()).thenReturn(true);
        when(number.getTwilioBindingSource()).thenReturn("VERIFIED_EXISTING");
        when(number.getTwilioAccountSid()).thenReturn(ACCOUNT);
        when(number.getExternalId()).thenReturn("PN" + "b".repeat(32));
        when(number.getPhoneNumber()).thenReturn("+12025550123");
        when(number.getVoiceVerifiedAt()).thenReturn(Instant.parse("2026-10-12T00:00:00Z"));
    }

    private TwilioVerifiedExistingVoiceRouteGate gate(boolean enabled, String tenant, String phone, String account) {
        return new TwilioVerifiedExistingVoiceRouteGate(jdbc, pilot, scope, enabled, tenant, phone, account);
    }

    private TwilioVerifiedExistingVoiceRouteGate pinned(boolean enabled) {
        return gate(enabled, BUSINESS.toString(), PHONE.toString(), ACCOUNT);
    }

    private void runningPilot() {
        when(pilot.evaluate(BUSINESS, ControlledPilotExternalEffectGuard.Effect.VOICE))
                .thenReturn(new ControlledPilotExternalEffectGuard.Decision(
                        true, true, "ALLOWED", "RUNNING", ControlledPilotExternalEffectGuard.Effect.VOICE));
        when(pilot.globalExternalEffectsEnabled()).thenReturn(true);
    }

    @Test
    void flagDisabledBlocksEvenCompleteVerifiedCarrierAndPlatformEvidence() {
        assertFalse(scope.callAsSystem(() -> pinned(false).allows(number)));
        verifyNoInteractions(jdbc, pilot);
    }

    @Test
    void neverAllowsAnyUnscopedOrTenantScopedInvocation() {
        var route = pinned(true);
        assertFalse(route.allows(number));
        scope.runAsTenant(BUSINESS, () -> assertFalse(route.allows(number)));
        verifyNoInteractions(jdbc, pilot);
    }

    @Test
    void mustPinExactBusinessPhoneAndAccountBeforeReadingDatabase() {
        assertFalse(scope.callAsSystem(() -> gate(true, "", "", "").allows(number)));
        assertFalse(scope.callAsSystem(() -> gate(true, UUID.randomUUID().toString(),
                PHONE.toString(), ACCOUNT).allows(number)));
        assertFalse(scope.callAsSystem(() -> gate(true, BUSINESS.toString(),
                UUID.randomUUID().toString(), ACCOUNT).allows(number)));
        assertFalse(scope.callAsSystem(() -> gate(true, BUSINESS.toString(),
                PHONE.toString(), "AC" + "c".repeat(32)).allows(number)));
        verifyNoInteractions(jdbc, pilot);
    }

    @Test
    void rejectsLegacyManagedInactiveMissingEvidenceOrNullRow() {
        var route = pinned(true);
        assertFalse(scope.callAsSystem(() -> route.allows(null)));
        when(number.getTwilioBindingSource()).thenReturn(null);
        assertFalse(scope.callAsSystem(() -> route.allows(number)));
        when(number.getTwilioBindingSource()).thenReturn("MANAGED_PURCHASE");
        assertFalse(scope.callAsSystem(() -> route.allows(number)));
        when(number.getTwilioBindingSource()).thenReturn("VERIFIED_EXISTING");
        when(number.getVoiceVerifiedAt()).thenReturn(null);
        assertFalse(scope.callAsSystem(() -> route.allows(number)));
        when(number.getVoiceVerifiedAt()).thenReturn(Instant.now());
        when(number.isActive()).thenReturn(false);
        assertFalse(scope.callAsSystem(() -> route.allows(number)));
        verifyNoInteractions(jdbc, pilot);
    }

    @Test
    void missingPilotRecordCannotBeMistakenForEnabledPilot() {
        when(pilot.evaluate(BUSINESS, ControlledPilotExternalEffectGuard.Effect.VOICE))
                .thenReturn(new ControlledPilotExternalEffectGuard.Decision(
                        true, false, "NOT_PILOT_MANAGED", null, ControlledPilotExternalEffectGuard.Effect.VOICE));
        assertFalse(scope.callAsSystem(() -> pinned(true).allows(number)));
        verifyNoInteractions(jdbc);
    }

    @Test
    void pausedPilotAndGlobalKillSwitchBothBlock() {
        when(pilot.evaluate(BUSINESS, ControlledPilotExternalEffectGuard.Effect.VOICE))
                .thenReturn(new ControlledPilotExternalEffectGuard.Decision(
                        false, true, "PILOT_NOT_RUNNING", "PAUSED", ControlledPilotExternalEffectGuard.Effect.VOICE));
        assertFalse(scope.callAsSystem(() -> pinned(true).allows(number)));
        runningPilot();
        when(pilot.globalExternalEffectsEnabled()).thenReturn(false);
        assertFalse(scope.callAsSystem(() -> pinned(true).allows(number)));
        verifyNoInteractions(jdbc);
    }

    @Test
    void noDurableTwoHumanEvidenceMeansNoRoutingAndNoProviderRequest() {
        runningPilot();
        when(jdbc.queryForObject(anyString(), eq(Boolean.class),
                any(), any(), any(), any(), any())).thenReturn(false);
        assertFalse(scope.callAsSystem(() -> pinned(true).allows(number)));
        verify(jdbc).queryForObject(argThat(sql ->
                sql.contains("c.requester_id <> c.approver_id")
                        && sql.contains("r.state = 'APPLIED'")
                        && sql.contains("a.action = 'EXECUTED'")
                        && sql.contains("r.business_id = c.business_id")
                        && sql.contains("c.phone_sid = ?")
                        && sql.contains("a.outcome = 'APPLIED'")),
                eq(Boolean.class), eq(PHONE), eq(BUSINESS),
                eq("+12025550123"), eq(ACCOUNT), eq("PN" + "b".repeat(32)));
    }

    @Test
    void exactDurableProofMayPassOnlyWithEverySafetyGatePresent() {
        runningPilot();
        when(jdbc.queryForObject(anyString(), eq(Boolean.class),
                any(), any(), any(), any(), any())).thenReturn(true);
        assertTrue(scope.callAsSystem(() -> pinned(true).allows(number)));
        assertEquals(TenantDatabaseContext.Mode.DENIED, scope.currentOrDenied().mode());
    }

    @Test
    void databaseFailureAndMissingPilotStateFailClosed() {
        runningPilot();
        when(jdbc.queryForObject(anyString(), eq(Boolean.class),
                any(), any(), any(), any(), any())).thenThrow(new IllegalStateException("Synthetic SQL failure"));
        assertFalse(scope.callAsSystem(() -> pinned(true).allows(number)));
        reset(pilot);
        assertFalse(scope.callAsSystem(() -> pinned(true).allows(number)));
    }

    @Test
    void missingPhoneAndTenantIdentifiersDenyBeforeAnyPilotOrDatabaseAccess() {
        var route = pinned(true);
        when(number.getId()).thenReturn(null);
        assertFalse(scope.callAsSystem(() -> route.allows(number)));
        when(number.getId()).thenReturn(PHONE);
        when(number.getBusinessId()).thenReturn(null);
        assertFalse(scope.callAsSystem(() -> route.allows(number)));
        verifyNoInteractions(pilot, jdbc);
    }

    @Test
    void foreignProviderAndMissingCarrierIdentityDenyBeforeAnyExternalEffect() {
        var route = pinned(true);
        when(number.getProvider()).thenReturn("OTHER");
        assertFalse(scope.callAsSystem(() -> route.allows(number)));
        when(number.getProvider()).thenReturn("TWILIO");
        when(number.getTwilioAccountSid()).thenReturn(null);
        assertFalse(scope.callAsSystem(() -> route.allows(number)));
        when(number.getTwilioAccountSid()).thenReturn(ACCOUNT);
        when(number.getExternalId()).thenReturn(null);
        assertFalse(scope.callAsSystem(() -> route.allows(number)));
        when(number.getExternalId()).thenReturn("PN" + "b".repeat(32));
        when(number.getPhoneNumber()).thenReturn(null);
        assertFalse(scope.callAsSystem(() -> route.allows(number)));
        verifyNoInteractions(pilot, jdbc);
    }

    @Test
    void registeredPilotInReadyRatherThanRunningCannotOpenExistingVoice() {
        when(pilot.evaluate(BUSINESS, ControlledPilotExternalEffectGuard.Effect.VOICE))
                .thenReturn(new ControlledPilotExternalEffectGuard.Decision(
                        true, true, "ALLOWED", "READY", ControlledPilotExternalEffectGuard.Effect.VOICE));
        assertFalse(scope.callAsSystem(() -> pinned(true).allows(number)));
        verifyNoInteractions(jdbc);
        verify(pilot, never()).globalExternalEffectsEnabled();
    }
}
