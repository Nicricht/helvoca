package cl.helvoca.operations;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VoicePilotIntegratedReadinessServiceTest {
    private static final String NUMBER = "+14355652512";
    private VoiceRealCallPreflightService local;
    private VoiceCarrierOwnershipPreflightService carrier;
    private VoicePilotIntegratedReadinessService release;

    @BeforeEach
    void setUp() {
        local = mock(VoiceRealCallPreflightService.class);
        carrier = mock(VoiceCarrierOwnershipPreflightService.class);
        release = new VoicePilotIntegratedReadinessService(local, carrier);
    }

    private void local(boolean ready, String... blockers) {
        when(local.inspect(NUMBER)).thenReturn(new VoiceRealCallPreflightService.Result(
                ready ? "LOCAL_PREFLIGHT_PASSED" : "BLOCKED", ready, false, false,
                List.of(blockers)));
    }

    private void carrier(boolean ownership, boolean admission, String code) {
        when(carrier.inspect(NUMBER)).thenReturn(new VoiceCarrierOwnershipPreflightService.Result(
                ownership && admission ? "CARRIER_AND_LOCAL_CHECKS_PASSED" : "NOT_READY",
                code, ownership, admission, false, false));
    }

    @Test
    void tenantLocalBlockersMustPreventAllCarrierRequests() {
        local(false, "TWILIO_VOICE_BINDING_NOT_CERTIFIED", "PILOT_VOICE_EFFECT_BLOCKED");
        var result = release.inspect(NUMBER);
        assertEquals("BLOCKED_LOCAL", result.state());
        assertFalse(result.localPreflightPassed());
        assertFalse(result.carrierPreflightPassed());
        assertEquals("NOT_CHECKED", result.carrierCode());
        assertEquals(List.of("TWILIO_VOICE_BINDING_NOT_CERTIFIED", "PILOT_VOICE_EFFECT_BLOCKED"),
                result.localBlockers());
        assertFalse(result.liveCallCertified());
        assertFalse(result.commercialGo());
        verifyNoInteractions(carrier);
    }

    @Test
    void invalidInputCannotReachTwilioEvenWhenNoSpecificBlockerIsPresent() {
        when(local.inspect("not-e164")).thenReturn(new VoiceRealCallPreflightService.Result(
                "BLOCKED", false, false, false, List.of("INVALID_DESTINATION_E164")));
        var result = release.inspect("not-e164");
        assertEquals("BLOCKED_LOCAL", result.state());
        assertFalse(result.carrierPreflightPassed());
        verifyNoInteractions(carrier);
    }

    @Test
    void carrierOwnershipAndLocalAdmissionMustBothPassWithoutPromotingCommercialGo() {
        local(true);
        carrier(true, true, "VERIFIED");
        var result = release.inspect(NUMBER);
        assertEquals("LOCAL_AND_CARRIER_PREFLIGHT_PASSED", result.state());
        assertTrue(result.localPreflightPassed());
        assertTrue(result.carrierPreflightPassed());
        assertEquals("VERIFIED", result.carrierCode());
        assertEquals(List.of(), result.localBlockers());
        assertFalse(result.liveCallCertified());
        assertFalse(result.commercialGo());
        verify(carrier, times(1)).inspect(NUMBER);
        verifyNoMoreInteractions(carrier);
    }

    @Test
    void knownCarrierMismatchOverridesLocalSuccessAndNeverMeansGo() {
        local(true);
        carrier(false, true, "WEBHOOK_MISMATCH");
        var result = release.inspect(NUMBER);
        assertEquals("BLOCKED_CARRIER", result.state());
        assertTrue(result.localPreflightPassed());
        assertFalse(result.carrierPreflightPassed());
        assertEquals("WEBHOOK_MISMATCH", result.carrierCode());
        assertFalse(result.liveCallCertified());
        assertFalse(result.commercialGo());
    }

    @Test
    void validCarrierOwnershipCannotBypassBlockedBindingAdmission() {
        local(true);
        carrier(true, false, "VERIFIED");
        var result = release.inspect(NUMBER);
        assertEquals("BLOCKED_CARRIER", result.state());
        assertFalse(result.carrierPreflightPassed());
        assertFalse(result.commercialGo());
    }

    @Test
    void TwilioApiOutageFailsClosedAndHidesAnyProviderSuppliedText() {
        local(true);
        when(carrier.inspect(NUMBER)).thenThrow(new IllegalStateException(
                "FAKE_TWILIO_SECRET_MUST_NOT_BE_RETURNED"));
        var result = assertDoesNotThrow(() -> release.inspect(NUMBER));
        assertEquals("BLOCKED_CARRIER", result.state());
        assertEquals("CARRIER_CHECK_UNAVAILABLE", result.carrierCode());
        assertTrue(result.localPreflightPassed());
        assertFalse(result.carrierPreflightPassed());
        assertFalse(result.liveCallCertified());
        assertFalse(result.commercialGo());
        assertFalse(result.toString().contains("FAKE_TWILIO_SECRET_MUST_NOT_BE_RETURNED"));
    }

    @Test
    void backendCarrierUnavailableOutcomeIsNotAnApplicationReadinessSuccess() {
        local(true);
        carrier(false, false, "PROVIDER_UNAVAILABLE");
        var result = release.inspect(NUMBER);
        assertEquals("BLOCKED_CARRIER", result.state());
        assertEquals("PROVIDER_UNAVAILABLE", result.carrierCode());
        assertFalse(result.carrierPreflightPassed());
    }
}
