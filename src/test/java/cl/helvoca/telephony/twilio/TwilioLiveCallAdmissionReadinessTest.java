package cl.helvoca.telephony.twilio;

import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.security.TenantDatabaseContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Purely offline checks of actual carrier-binding admission prerequisites. */
class TwilioLiveCallAdmissionReadinessTest {
    private static final String PARENT = "AC" + "a".repeat(32);
    private static final String CHILD = "AC" + "b".repeat(32);
    private static final String FOREIGN = "AC" + "f".repeat(32);
    private static final String SID = "PN" + "c".repeat(32);

    private TwilioProperties properties;
    private TwilioVerifiedExistingVoiceRouteGate existingRoute;
    private TenantDatabaseContext scope;
    private PhoneNumber phone;

    @BeforeEach
    void prepare() {
        properties = new TwilioProperties();
        properties.setAccountSid(PARENT);
        existingRoute = mock(TwilioVerifiedExistingVoiceRouteGate.class);
        scope = new TenantDatabaseContext();
        phone = mock(PhoneNumber.class);
        when(phone.getId()).thenReturn(UUID.randomUUID());
        when(phone.getBusinessId()).thenReturn(UUID.randomUUID());
        when(phone.isActive()).thenReturn(true);
        when(phone.getProvider()).thenReturn("TWILIO");
        when(phone.getVoiceVerifiedAt()).thenReturn(Instant.parse("2026-10-12T00:00:00Z"));
        when(phone.getExternalId()).thenReturn(SID);
        when(phone.getTwilioAccountSid()).thenReturn(PARENT);
        when(phone.getTwilioBindingSource()).thenReturn("MANAGED_PURCHASE");
    }

    private TwilioLiveCallAdmissionReadiness readiness(String child) {
        return new TwilioLiveCallAdmissionReadiness(properties, child, existingRoute, scope);
    }

    @Test
    void purchasedAndAttestedNumberIsLocallyAllowedOnlyUnderMainAccount() {
        assertTrue(readiness(CHILD).locallyAllowed(phone));
        when(phone.getTwilioAccountSid()).thenReturn(CHILD);
        assertFalse(readiness(CHILD).locallyAllowed(phone));
        when(phone.getTwilioAccountSid()).thenReturn(PARENT);
        assertTrue(readiness(null).locallyAllowed(phone));
        verifyNoInteractions(existingRoute);
    }

    @Test
    void nullOrIncompleteCarrierBindingCannotPass() {
        var readiness = readiness(CHILD);
        assertFalse(readiness.locallyAllowed(null));
        when(phone.getId()).thenReturn(null);
        assertFalse(readiness.locallyAllowed(phone));
        when(phone.getId()).thenReturn(UUID.randomUUID());
        when(phone.getBusinessId()).thenReturn(null);
        assertFalse(readiness.locallyAllowed(phone));
        when(phone.getBusinessId()).thenReturn(UUID.randomUUID());
        when(phone.isActive()).thenReturn(false);
        assertFalse(readiness.locallyAllowed(phone));
        when(phone.isActive()).thenReturn(true);
        when(phone.getProvider()).thenReturn("TWILIO_WHATSAPP");
        assertFalse(readiness.locallyAllowed(phone));
        when(phone.getProvider()).thenReturn("TWILIO");
        when(phone.getVoiceVerifiedAt()).thenReturn(null);
        assertFalse(readiness.locallyAllowed(phone));
        when(phone.getVoiceVerifiedAt()).thenReturn(Instant.now());
        when(phone.getExternalId()).thenReturn(null);
        assertFalse(readiness.locallyAllowed(phone));
        when(phone.getExternalId()).thenReturn("PNinvalid");
        assertFalse(readiness.locallyAllowed(phone));
        when(phone.getExternalId()).thenReturn(SID);
        when(phone.getTwilioAccountSid()).thenReturn(null);
        assertFalse(readiness.locallyAllowed(phone));
        when(phone.getTwilioAccountSid()).thenReturn("ACinvalid");
        assertFalse(readiness.locallyAllowed(phone));
        verifyNoInteractions(existingRoute);
    }

    @Test
    void missingOrInvalidParentCredentialsAndForeignOwnerNeverAuthorize() {
        var readiness = readiness(CHILD);
        properties.setAccountSid("");
        assertFalse(readiness.locallyAllowed(phone));
        properties.setAccountSid("ACnot32hex");
        assertFalse(readiness.locallyAllowed(phone));
        properties.setAccountSid(PARENT);
        when(phone.getTwilioAccountSid()).thenReturn(FOREIGN);
        assertFalse(readiness.locallyAllowed(phone));
        verifyNoInteractions(existingRoute);
    }

    @Test
    void unverifiedLegacyOrUnknownProvenanceNeverAuthorizes() {
        var readiness = readiness(CHILD);
        when(phone.getTwilioBindingSource()).thenReturn(null);
        assertFalse(readiness.locallyAllowed(phone));
        when(phone.getTwilioBindingSource()).thenReturn("LEGACY");
        assertFalse(readiness.locallyAllowed(phone));
        verifyNoInteractions(existingRoute);
    }

    @Test
    void existingNumberRequiresTheTwoPersonPilotGateInsideTemporarySystemScope() {
        when(phone.getTwilioBindingSource()).thenReturn("VERIFIED_EXISTING");
        when(phone.getTwilioAccountSid()).thenReturn(CHILD);
        when(existingRoute.allows(phone)).thenAnswer(invocation -> {
            assertEquals(TenantDatabaseContext.Mode.SYSTEM, scope.currentOrDenied().mode());
            return true;
        });

        assertTrue(readiness(" " + CHILD + " ").locallyAllowed(phone));
        assertEquals(TenantDatabaseContext.Mode.DENIED, scope.currentOrDenied().mode());
        verify(existingRoute).allows(phone);
    }

    @Test
    void disabledOrRejectedExistingRouteCannotPassEvenIfSidLooksReal() {
        when(phone.getTwilioBindingSource()).thenReturn("VERIFIED_EXISTING");
        when(existingRoute.allows(phone)).thenReturn(false);
        assertFalse(readiness(CHILD).locallyAllowed(phone));
        verify(existingRoute).allows(phone);
        assertEquals(TenantDatabaseContext.Mode.DENIED, scope.currentOrDenied().mode());

        when(phone.getTwilioAccountSid()).thenReturn(FOREIGN);
        assertFalse(readiness(CHILD).locallyAllowed(phone));
        verifyNoMoreInteractions(existingRoute);
    }
}
