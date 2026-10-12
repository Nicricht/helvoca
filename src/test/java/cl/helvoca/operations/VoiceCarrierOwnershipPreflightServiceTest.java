package cl.helvoca.operations;

import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.phone.PhoneNumberRepository;
import cl.helvoca.security.TenantProvider;
import cl.helvoca.telephony.twilio.TwilioExistingNumberOwnershipVerifier;
import cl.helvoca.telephony.twilio.TwilioLiveCallAdmissionReadiness;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VoiceCarrierOwnershipPreflightServiceTest {
    private static final String PHONE = "+14355652512";
    private static final String MAIN = "AC" + "a".repeat(32);
    private static final String SID = "PN" + "b".repeat(32);

    private final UUID tenantId = UUID.randomUUID();
    private TenantProvider tenant;
    private PhoneNumberRepository numbers;
    private TwilioExistingNumberOwnershipVerifier verifier;
    private TwilioLiveCallAdmissionReadiness local;
    private VoiceCarrierOwnershipPreflightService service;
    private PhoneNumber row;

    @BeforeEach
    void setUp() {
        tenant = mock(TenantProvider.class);
        numbers = mock(PhoneNumberRepository.class);
        verifier = mock(TwilioExistingNumberOwnershipVerifier.class);
        local = mock(TwilioLiveCallAdmissionReadiness.class);
        row = new PhoneNumber();
        row.setBusinessId(tenantId);
        row.setPhoneNumber(PHONE);
        row.setProvider("TWILIO");
        row.setActive(true);
        row.setTwilioAccountSid(MAIN);
        row.setExternalId(SID);
        when(tenant.requireBusinessId()).thenReturn(tenantId);
        when(numbers.findAllByBusinessIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(row));
        service = new VoiceCarrierOwnershipPreflightService(tenant, numbers, verifier, local);
    }

    private void permit(boolean allowed, TwilioExistingNumberOwnershipVerifier.Code code) {
        when(local.locallyAllowed(row)).thenReturn(allowed);
        var evidence = code == TwilioExistingNumberOwnershipVerifier.Code.VERIFIED
                ? new TwilioExistingNumberOwnershipVerifier.Evidence(
                        SID, MAIN, TwilioExistingNumberOwnershipVerifier.AccountKind.MAIN)
                : null;
        when(verifier.verify(PHONE, MAIN, SID))
                .thenReturn(new TwilioExistingNumberOwnershipVerifier.Outcome(code, evidence));
    }

    @Test
    void verifiedCarrierAndAuthorizedLocalRouteStillNeverMeanRealCallOrCommercialGo() throws Exception {
        permit(true, TwilioExistingNumberOwnershipVerifier.Code.VERIFIED);
        var result = service.inspect(PHONE);
        assertEquals("CARRIER_AND_LOCAL_CHECKS_PASSED", result.state());
        assertEquals("VERIFIED", result.carrierCode());
        assertTrue(result.carrierOwnershipVerified());
        assertTrue(result.localCallAdmissionAllowed());
        assertFalse(result.liveCallCertified());
        assertFalse(result.commercialGo());
        assertTrue(VoiceCarrierOwnershipPreflightService.class.getMethod("inspect", String.class)
                .getAnnotation(Transactional.class).readOnly());
        verify(verifier).verify(PHONE, MAIN, SID);
        verify(numbers, never()).save(any());
    }

    @Test
    void externalOwnershipVerifiedButLegacyUnapprovedRouteRemainsBlocked() {
        permit(false, TwilioExistingNumberOwnershipVerifier.Code.VERIFIED);
        var result = service.inspect(PHONE);
        assertEquals("NOT_READY", result.state());
        assertTrue(result.carrierOwnershipVerified());
        assertFalse(result.localCallAdmissionAllowed());
        assertFalse(result.commercialGo());
    }

    @Test
    void localAdmissionCannotOverrideCarrierWebhookOrAccountMismatch() {
        permit(true, TwilioExistingNumberOwnershipVerifier.Code.WEBHOOK_MISMATCH);
        var result = service.inspect(PHONE);
        assertEquals("NOT_READY", result.state());
        assertEquals("WEBHOOK_MISMATCH", result.carrierCode());
        assertFalse(result.carrierOwnershipVerified());
        assertTrue(result.localCallAdmissionAllowed());
        assertFalse(result.liveCallCertified());
    }

    @Test
    void neitherLocalNorCarrierProofCanBeAssumedWhenUnavailable() {
        permit(false, TwilioExistingNumberOwnershipVerifier.Code.PROVIDER_UNAVAILABLE);
        var result = service.inspect(PHONE);
        assertEquals("NOT_READY", result.state());
        assertEquals("PROVIDER_UNAVAILABLE", result.carrierCode());
        assertFalse(result.carrierOwnershipVerified());
    }

    @Test
    void invalidCallerPhoneFailsBeforeProviderOrDatabaseLookup() {
        for (String phone : new String[]{"+1", "14355652512", "+99999999999999999", ""}) {
            assertEquals("INVALID_DESTINATION", service.inspect(phone).state());
        }
        assertEquals("INVALID_DESTINATION", service.inspect(null).state());
        verify(numbers, never()).findAllByBusinessIdOrderByCreatedAtDesc(tenantId);
        verifyNoInteractions(verifier, local);
    }

    @Test
    void anotherTenantsNumberCanNeverBeSentToTheCarrierVerifier() {
        when(numbers.findAllByBusinessIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of());
        var result = service.inspect(PHONE);
        assertEquals("LOCAL_BINDING_INCOMPLETE", result.state());
        assertEquals("NOT_CHECKED", result.carrierCode());
        assertFalse(result.carrierOwnershipVerified());
        verifyNoInteractions(verifier, local);
    }

    @Test
    void inactiveOrForeignProviderOrIncompleteAccountAndPnNeverTriggerCarrierRequest() {
        row.setActive(false);
        assertEquals("LOCAL_BINDING_INCOMPLETE", service.inspect(PHONE).state());
        row.setActive(true);
        row.setProvider("META");
        assertEquals("LOCAL_BINDING_INCOMPLETE", service.inspect(PHONE).state());
        row.setProvider("TWILIO");
        row.setTwilioAccountSid(null);
        assertEquals("LOCAL_BINDING_INCOMPLETE", service.inspect(PHONE).state());
        row.setTwilioAccountSid("ACwrong");
        assertEquals("LOCAL_BINDING_INCOMPLETE", service.inspect(PHONE).state());
        row.setTwilioAccountSid(MAIN);
        row.setExternalId(null);
        assertEquals("LOCAL_BINDING_INCOMPLETE", service.inspect(PHONE).state());
        row.setExternalId("PNwrong");
        assertEquals("LOCAL_BINDING_INCOMPLETE", service.inspect(PHONE).state());
        verifyNoInteractions(verifier, local);
    }
}
