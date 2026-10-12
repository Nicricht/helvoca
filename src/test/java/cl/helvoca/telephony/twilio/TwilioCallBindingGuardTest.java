package cl.helvoca.telephony.twilio;

import cl.helvoca.call.CallSession;
import cl.helvoca.call.CallSessionRepository;
import cl.helvoca.call.CallStatus;
import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.phone.PhoneNumberRepository;
import cl.helvoca.security.TenantDatabaseContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** All tenant routing assertions use fake carrier IDs and no network. */
class TwilioCallBindingGuardTest {
    private static final String ACCOUNT = "ACaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String PROVIDER_NUMBER = "PNbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String NUMBER = "+14355550101";
    private static final String CALLER = "+56911111111";
    private static final String CALL_SID = "CA11111111111111111111111111111111";
    private static final String STREAM_SID = "MZ11111111111111111111111111111111";

    private PhoneNumberRepository phones;
    private CallSessionRepository calls;
    private TenantDatabaseContext databaseContext;
    private TwilioCallBindingGuard guard;
    private PhoneNumber phone;
    private CallSession call;
    private UUID businessId;
    private UUID phoneId;

    @BeforeEach
    void arrange() {
        phones = mock(PhoneNumberRepository.class);
        calls = mock(CallSessionRepository.class);
        databaseContext = new TenantDatabaseContext();
        guard = new TwilioCallBindingGuard(phones, calls, databaseContext);
        phone = mock(PhoneNumber.class);
        call = mock(CallSession.class);
        businessId = UUID.randomUUID();
        phoneId = UUID.randomUUID();

        when(phone.getId()).thenReturn(phoneId);
        when(phone.getBusinessId()).thenReturn(businessId);
        when(phone.getPhoneNumber()).thenReturn(NUMBER);
        when(phone.isActive()).thenReturn(true);
        when(phone.getProvider()).thenReturn("TWILIO");
        when(phone.getTwilioBindingSource()).thenReturn("MANAGED_PURCHASE");
        when(phone.getTwilioAccountSid()).thenReturn(ACCOUNT);
        when(phone.getExternalId()).thenReturn(PROVIDER_NUMBER);
        when(phone.getVoiceVerifiedAt()).thenReturn(Instant.now());
        when(call.getPhoneNumberId()).thenReturn(phoneId);
        when(call.getBusinessId()).thenReturn(businessId);
        when(call.getTelephonyProvider()).thenReturn("twilio");
        when(call.getDestinationNumber()).thenReturn(NUMBER);
        when(call.getCallerNumber()).thenReturn(CALLER);
        when(call.getStatus()).thenReturn(CallStatus.RINGING);
        when(call.getStreamSid()).thenReturn(STREAM_SID);
        when(phones.findByPhoneNumberAndActiveTrue(NUMBER)).thenReturn(Optional.of(phone));
        when(phones.findById(phoneId)).thenReturn(Optional.of(phone));
        when(calls.findByProviderCallId(CALL_SID)).thenReturn(Optional.of(call));
    }

    @Test
    void acceptsOnlyVerifiedTenantBindingAndRestoresSystemScope() {
        when(phones.findByPhoneNumberAndActiveTrue(NUMBER)).thenAnswer(invocation -> {
            assertEquals(TenantDatabaseContext.Mode.SYSTEM, databaseContext.currentOrDenied().mode());
            return Optional.of(phone);
        });
        assertTrue(guard.allowsIncoming(CALL_SID, NUMBER, CALLER, ACCOUNT));
        assertTrue(guard.allowsCallback(CALL_SID, ACCOUNT));
        assertTrue(guard.allowsStreamCallback(CALL_SID, STREAM_SID, ACCOUNT));
        assertTrue(guard.allowsMedia(CALL_SID, NUMBER, CALLER, ACCOUNT));
        assertEquals(TenantDatabaseContext.Mode.DENIED, databaseContext.currentOrDenied().mode());
    }

    @Test
    void rejectsMissingOrBlankIngressIdentifiersWithoutDatabaseLookup() {
        assertFalse(guard.allowsIncoming(null, NUMBER, CALLER, ACCOUNT));
        assertFalse(guard.allowsIncoming(" ", NUMBER, CALLER, ACCOUNT));
        assertFalse(guard.allowsIncoming(CALL_SID, null, CALLER, ACCOUNT));
        assertFalse(guard.allowsIncoming(CALL_SID, " ", CALLER, ACCOUNT));
        assertFalse(guard.allowsIncoming(CALL_SID, NUMBER, null, ACCOUNT));
        assertFalse(guard.allowsIncoming(CALL_SID, NUMBER, " ", ACCOUNT));
        assertFalse(guard.allowsIncoming(CALL_SID, NUMBER, CALLER, null));
        assertFalse(guard.allowsIncoming(CALL_SID, NUMBER, CALLER, " "));
        assertFalse(guard.allowsCallback(null, ACCOUNT));
        assertFalse(guard.allowsCallback(" ", ACCOUNT));
        assertFalse(guard.allowsCallback(CALL_SID, null));
        assertFalse(guard.allowsCallback(CALL_SID, " "));
        assertFalse(guard.allowsStreamCallback(CALL_SID, null, ACCOUNT));
        assertFalse(guard.allowsStreamCallback(CALL_SID, " ", ACCOUNT));
        assertFalse(guard.allowsMedia(null, NUMBER, CALLER, ACCOUNT));
        assertFalse(guard.allowsMedia(" ", NUMBER, CALLER, ACCOUNT));
        assertFalse(guard.allowsMedia(CALL_SID, null, CALLER, ACCOUNT));
        assertFalse(guard.allowsMedia(CALL_SID, " ", CALLER, ACCOUNT));
        assertFalse(guard.allowsMedia(CALL_SID, NUMBER, null, ACCOUNT));
        assertFalse(guard.allowsMedia(CALL_SID, NUMBER, " ", ACCOUNT));
        assertFalse(guard.allowsMedia(CALL_SID, NUMBER, CALLER, null));
        assertFalse(guard.allowsMedia(CALL_SID, NUMBER, CALLER, " "));
        verifyNoInteractions(phones, calls);
    }

    @Test
    void rejectsMissingPhoneAndMissingCall() {
        when(phones.findByPhoneNumberAndActiveTrue(NUMBER)).thenReturn(Optional.empty());
        assertFalse(guard.allowsIncoming(CALL_SID, NUMBER, CALLER, ACCOUNT));
        assertFalse(guard.allowsMedia(CALL_SID, NUMBER, CALLER, ACCOUNT));
        when(phones.findByPhoneNumberAndActiveTrue(NUMBER)).thenReturn(Optional.of(phone));
        when(calls.findByProviderCallId(CALL_SID)).thenReturn(Optional.empty());
        assertTrue(guard.allowsIncoming(CALL_SID, NUMBER, CALLER, ACCOUNT));
        assertFalse(guard.allowsMedia(CALL_SID, NUMBER, CALLER, ACCOUNT));
        assertFalse(guard.allowsCallback(CALL_SID, ACCOUNT));
        assertFalse(guard.allowsStreamCallback(CALL_SID, STREAM_SID, ACCOUNT));
    }

    @Test
    void invalidOrHistoricalPhoneNeverOpensCall() {
        when(phone.isActive()).thenReturn(false);
        assertFalse(guard.allowsIncoming(CALL_SID, NUMBER, CALLER, ACCOUNT));
        when(phone.isActive()).thenReturn(true);
        when(phone.getProvider()).thenReturn("OTHER");
        assertFalse(guard.allowsIncoming(CALL_SID, NUMBER, CALLER, ACCOUNT));
        when(phone.getProvider()).thenReturn("TWILIO");
        when(phone.getTwilioBindingSource()).thenReturn(null);
        assertFalse(guard.allowsIncoming(CALL_SID, NUMBER, CALLER, ACCOUNT));
        when(phone.getTwilioBindingSource()).thenReturn("MANAGED_PURCHASE");
        when(phone.getVoiceVerifiedAt()).thenReturn(null);
        assertFalse(guard.allowsIncoming(CALL_SID, NUMBER, CALLER, ACCOUNT));
        when(phone.getVoiceVerifiedAt()).thenReturn(Instant.now());
        assertFalse(guard.allowsIncoming(CALL_SID, NUMBER, CALLER, "ACcccccccccccccccccccccccccccccccc"));
        when(phone.getTwilioAccountSid()).thenReturn("wrong");
        assertFalse(guard.allowsIncoming(CALL_SID, NUMBER, CALLER, "wrong"));
        when(phone.getTwilioAccountSid()).thenReturn(ACCOUNT);
        when(phone.getExternalId()).thenReturn(null);
        assertFalse(guard.allowsIncoming(CALL_SID, NUMBER, CALLER, ACCOUNT));
        when(phone.getExternalId()).thenReturn("not-a-PN");
        assertFalse(guard.allowsIncoming(CALL_SID, NUMBER, CALLER, ACCOUNT));
        when(phone.getExternalId()).thenReturn(PROVIDER_NUMBER);
        when(phone.getBusinessId()).thenReturn(null);
        assertFalse(guard.allowsIncoming(CALL_SID, NUMBER, CALLER, ACCOUNT));
        when(phones.findByPhoneNumberAndActiveTrue(NUMBER)).thenReturn(Optional.empty());
        assertFalse(guard.allowsIncoming(CALL_SID, NUMBER, CALLER, ACCOUNT));
    }

    @Test
    void mismatchedExistingCallCannotBeReplayedAsAnotherTenant() {
        when(call.getTelephonyProvider()).thenReturn("other");
        assertFalse(guard.allowsIncoming(CALL_SID, NUMBER, CALLER, ACCOUNT));
        assertFalse(guard.allowsCallback(CALL_SID, ACCOUNT));
        when(call.getTelephonyProvider()).thenReturn("twilio");
        when(call.getBusinessId()).thenReturn(UUID.randomUUID());
        assertFalse(guard.allowsIncoming(CALL_SID, NUMBER, CALLER, ACCOUNT));
        when(call.getBusinessId()).thenReturn(businessId);
        when(call.getPhoneNumberId()).thenReturn(UUID.randomUUID());
        assertFalse(guard.allowsIncoming(CALL_SID, NUMBER, CALLER, ACCOUNT));
        when(call.getPhoneNumberId()).thenReturn(phoneId);
        when(call.getDestinationNumber()).thenReturn("+14355550999");
        assertFalse(guard.allowsIncoming(CALL_SID, NUMBER, CALLER, ACCOUNT));
        when(call.getDestinationNumber()).thenReturn(NUMBER);
        when(call.getCallerNumber()).thenReturn("+56988888888");
        assertFalse(guard.allowsIncoming(CALL_SID, NUMBER, CALLER, ACCOUNT));
        when(call.getCallerNumber()).thenReturn(CALLER);
        assertTrue(guard.allowsIncoming(CALL_SID, NUMBER, CALLER, ACCOUNT));
        when(call.getPhoneNumberId()).thenReturn(null);
        assertFalse(guard.allowsCallback(CALL_SID, ACCOUNT));
        when(call.getPhoneNumberId()).thenReturn(phoneId);
        when(phones.findById(phoneId)).thenReturn(Optional.empty());
        assertFalse(guard.allowsCallback(CALL_SID, ACCOUNT));
        when(phones.findById(phoneId)).thenReturn(Optional.of(phone));
        when(phone.getTwilioAccountSid()).thenReturn("ACcccccccccccccccccccccccccccccccc");
        assertFalse(guard.allowsCallback(CALL_SID, ACCOUNT));
    }

    @Test
    void rejectsUnboundStreamCallbacksAndTerminalOrMissingMediaCalls() {
        assertFalse(guard.allowsStreamCallback(CALL_SID, "MZ-other", ACCOUNT));
        when(calls.findByProviderCallId(CALL_SID)).thenReturn(Optional.empty());
        assertFalse(guard.allowsStreamCallback(CALL_SID, STREAM_SID, ACCOUNT));
        when(calls.findByProviderCallId(CALL_SID)).thenReturn(Optional.of(call));
        when(call.getStatus()).thenReturn(null);
        assertFalse(guard.allowsMedia(CALL_SID, NUMBER, CALLER, ACCOUNT));
        when(call.getStatus()).thenReturn(CallStatus.COMPLETED);
        assertFalse(guard.allowsMedia(CALL_SID, NUMBER, CALLER, ACCOUNT));
        when(call.getStatus()).thenReturn(CallStatus.IN_PROGRESS);
        assertTrue(guard.allowsMedia(CALL_SID, NUMBER, CALLER, ACCOUNT));
        when(call.getCallerNumber()).thenReturn("other");
        assertFalse(guard.allowsMedia(CALL_SID, NUMBER, CALLER, ACCOUNT));
    }

    @Test
    void nestedTenantScopeIsRestoredAfterCarrierValidation() {
        UUID tenant = UUID.randomUUID();
        databaseContext.runAsTenant(tenant, () -> {
            assertTrue(guard.allowsIncoming(CALL_SID, NUMBER, CALLER, ACCOUNT));
            assertEquals(TenantDatabaseContext.Mode.TENANT, databaseContext.currentOrDenied().mode());
            assertEquals(tenant, databaseContext.currentOrDenied().businessId());
        });
        assertEquals(TenantDatabaseContext.Mode.DENIED, databaseContext.currentOrDenied().mode());
    }

    @Test
    void truthfulVerifiedExistingDoesNotOpenAnyCallPathBeforeCommercialAuthorization() {
        when(phone.getTwilioBindingSource()).thenReturn("VERIFIED_EXISTING");
        assertFalse(guard.allowsIncoming(CALL_SID, NUMBER, CALLER, ACCOUNT));
        assertFalse(guard.allowsMedia(CALL_SID, NUMBER, CALLER, ACCOUNT));
        assertFalse(guard.allowsCallback(CALL_SID, ACCOUNT));
        assertFalse(guard.allowsStreamCallback(CALL_SID, STREAM_SID, ACCOUNT));
        assertEquals(TenantDatabaseContext.Mode.DENIED, databaseContext.currentOrDenied().mode());
    }


    @Test
    void independentlyAuthorizedExistingPhoneCanReachAllFourBoundRoutesWithoutWeakeningIdentity() {
        when(phone.getTwilioBindingSource()).thenReturn("VERIFIED_EXISTING");
        var permitted = new TwilioCallBindingGuard(phones, calls, databaseContext,
                candidate -> candidate == phone);
        assertTrue(permitted.allowsIncoming(CALL_SID, NUMBER, CALLER, ACCOUNT));
        assertTrue(permitted.allowsCallback(CALL_SID, ACCOUNT));
        assertTrue(permitted.allowsStreamCallback(CALL_SID, STREAM_SID, ACCOUNT));
        assertTrue(permitted.allowsMedia(CALL_SID, NUMBER, CALLER, ACCOUNT));

        assertFalse(permitted.allowsIncoming(CALL_SID, NUMBER, CALLER,
                "AC" + "c".repeat(32)));
        assertFalse(permitted.allowsCallback(CALL_SID, "AC" + "c".repeat(32)));
        assertFalse(permitted.allowsMedia(CALL_SID, NUMBER, "+56933333333", ACCOUNT));
        assertFalse(permitted.allowsStreamCallback(CALL_SID, "MZ" + "f".repeat(32), ACCOUNT));
        when(call.getBusinessId()).thenReturn(UUID.randomUUID());
        assertFalse(permitted.allowsIncoming(CALL_SID, NUMBER, CALLER, ACCOUNT));
        assertFalse(permitted.allowsCallback(CALL_SID, ACCOUNT));
        assertFalse(permitted.allowsMedia(CALL_SID, NUMBER, CALLER, ACCOUNT));
        assertEquals(TenantDatabaseContext.Mode.DENIED, databaseContext.currentOrDenied().mode());
    }

    @Test
    void existingRouteOptInCannotApplyToLegacyManagedMissingVoiceOrInactivePhone() {
        var permissiveGate = new TwilioCallBindingGuard(phones, calls, databaseContext,
                candidate -> true);
        when(phone.getTwilioBindingSource()).thenReturn(null);
        assertFalse(permissiveGate.allowsIncoming(CALL_SID, NUMBER, CALLER, ACCOUNT));
        when(phone.getTwilioBindingSource()).thenReturn("VERIFIED_EXISTING");
        when(phone.getVoiceVerifiedAt()).thenReturn(null);
        assertFalse(permissiveGate.allowsIncoming(CALL_SID, NUMBER, CALLER, ACCOUNT));
        when(phone.getVoiceVerifiedAt()).thenReturn(Instant.now());
        when(phone.isActive()).thenReturn(false);
        assertFalse(permissiveGate.allowsIncoming(CALL_SID, NUMBER, CALLER, ACCOUNT));
        when(phone.isActive()).thenReturn(true);
        when(phone.getExternalId()).thenReturn("unverified");
        assertFalse(permissiveGate.allowsIncoming(CALL_SID, NUMBER, CALLER, ACCOUNT));
    }
}
