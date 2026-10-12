package cl.helvoca.phone;

import cl.helvoca.telephony.twilio.TwilioExistingNumberOwnershipVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

import static cl.helvoca.phone.TwilioExistingEnrollmentPlanner.Authority;
import static cl.helvoca.phone.TwilioExistingEnrollmentPlanner.Code;
import static cl.helvoca.telephony.twilio.TwilioExistingNumberOwnershipVerifier.AccountKind;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TwilioExistingEnrollmentPlannerTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID ROW_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID CHANGE = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID REQUESTER = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID APPROVER = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final String E164 = "+12025550123";
    private static final String ACCOUNT = "AC" + "a".repeat(32);
    private static final String OTHER_ACCOUNT = "AC" + "c".repeat(32);
    private static final String PN = "PN" + "b".repeat(32);
    private static final String OTHER_PN = "PN" + "f".repeat(32);
    private TwilioExistingNumberOwnershipVerifier carrier;
    private TwilioExistingEnrollmentPlanner planner;
    private PhoneNumber existing;
    private TwilioExistingEnrollmentPlanner.Actor requester;
    private TwilioExistingEnrollmentPlanner.Actor approver;
    private TwilioExistingEnrollmentPlanner.Intent intent;

    @BeforeEach
    void setup() {
        carrier = mock(TwilioExistingNumberOwnershipVerifier.class);
        planner = new TwilioExistingEnrollmentPlanner(carrier);
        requester = new TwilioExistingEnrollmentPlanner.Actor(REQUESTER, Authority.PLATFORM_OPERATOR);
        approver = new TwilioExistingEnrollmentPlanner.Actor(APPROVER, Authority.PLATFORM_APPROVER);
        intent = new TwilioExistingEnrollmentPlanner.Intent(TENANT, ROW_ID, E164, ACCOUNT, CHANGE,
                requester, approver);
        existing = new PhoneNumber();
        ReflectionTestUtils.setField(existing, "id", ROW_ID);
        existing.setBusinessId(TENANT);
        existing.setPhoneNumber(E164);
        existing.setProvider("TWILIO");
        existing.setActive(true);
        existing.setExternalId(null);
    }

    private Code decision() {
        return planner.preview(intent, existing).code();
    }

    private void rejectsBeforeNetwork(Code expected) {
        assertEquals(expected, decision());
        verifyNoInteractions(carrier);
    }

    private void validCarrier(String sid, String accountSid) {
        when(carrier.verify(E164, ACCOUNT, existing.getExternalId())).thenReturn(
                new TwilioExistingNumberOwnershipVerifier.Outcome(
                        TwilioExistingNumberOwnershipVerifier.Code.VERIFIED,
                        new TwilioExistingNumberOwnershipVerifier.Evidence(sid, accountSid, AccountKind.MAIN)));
    }

    private TwilioExistingEnrollmentPlanner.Intent changed(UUID businessId, UUID row,
                                                              String number, String owner, UUID change,
                                                              TwilioExistingEnrollmentPlanner.Actor author,
                                                              TwilioExistingEnrollmentPlanner.Actor reviewer) {
        return new TwilioExistingEnrollmentPlanner.Intent(businessId, row, number, owner, change,
                author, reviewer);
    }

    @Test
    void exactLegacyNullSidWithTwoSeparatePlatformHumansIsReviewableButNeverAttached() throws Exception {
        validCarrier(PN, ACCOUNT);
        var plan = planner.preview(intent, existing);
        assertEquals(Code.REVIEWABLE_ONLY, plan.code());
        assertEquals(TwilioExistingNumberOwnershipVerifier.Code.VERIFIED, plan.carrierCode());
        assertTrue(plan.reviewable());
        var candidate = plan.candidate();
        assertEquals(CHANGE, candidate.changeId());
        assertEquals(TENANT, candidate.businessId());
        assertEquals(ROW_ID, candidate.phoneId());
        assertEquals(E164, candidate.destinationE164());
        assertEquals(PN, candidate.verifiedPhoneSid());
        assertEquals(ACCOUNT, candidate.ownerAccountSid());
        assertEquals(REQUESTER, candidate.requesterId());
        assertEquals(APPROVER, candidate.approverId());
        assertEquals("VERIFIED_EXISTING", candidate.proposedSource());
        assertEquals("EnrollmentCandidate[REDACTED]", candidate.toString());
        assertEquals("EnrollmentIntent[REDACTED]", intent.toString());
        assertEquals("EnrollmentPreview[REVIEWABLE_ONLY]", plan.toString());
        String json = new ObjectMapper().writeValueAsString(plan);
        assertFalse(json.contains(PN));
        assertFalse(json.contains(ACCOUNT));
        assertFalse(json.contains(E164));
        assertFalse(json.contains(TENANT.toString()));
        assertNull(existing.getExternalId());
        assertNull(existing.getTwilioBindingSource());
        assertNull(existing.getVoiceVerifiedAt());
        assertTrue(existing.isActive());
        verify(carrier).verify(E164, ACCOUNT, null);
        verifyNoMoreInteractions(carrier);
    }

    @Test
    void alsoChecksHistoricalStoredSidByCarrierNotByTenantSuppliedPayload() {
        existing.setExternalId(PN);
        validCarrier(PN, ACCOUNT);
        var preview = planner.preview(intent, existing);
        assertTrue(preview.reviewable());
        verify(carrier).verify(E164, ACCOUNT, PN);
        assertEquals(PN, existing.getExternalId());
    }

    @Test
    void rejectsEveryIncompleteOrMalformedServerIntentBeforeAnyNetwork() {
        assertEquals(Code.INVALID_INTENT, planner.preview(null, existing).code());
        var invalid = new TwilioExistingEnrollmentPlanner.Intent[]{
                changed(null, ROW_ID, E164, ACCOUNT, CHANGE, requester, approver),
                changed(TENANT, null, E164, ACCOUNT, CHANGE, requester, approver),
                changed(TENANT, ROW_ID, E164, ACCOUNT, null, requester, approver),
                changed(TENANT, ROW_ID, null, ACCOUNT, CHANGE, requester, approver),
                changed(TENANT, ROW_ID, "12025550123", ACCOUNT, CHANGE, requester, approver),
                changed(TENANT, ROW_ID, E164, null, CHANGE, requester, approver),
                changed(TENANT, ROW_ID, E164, "ACfake", CHANGE, requester, approver),
                changed(TENANT, ROW_ID, E164, ACCOUNT, CHANGE, null, approver),
                changed(TENANT, ROW_ID, E164, ACCOUNT, CHANGE, requester, null),
                changed(TENANT, ROW_ID, E164, ACCOUNT, CHANGE,
                        new TwilioExistingEnrollmentPlanner.Actor(null, Authority.PLATFORM_OPERATOR), approver),
                changed(TENANT, ROW_ID, E164, ACCOUNT, CHANGE,
                        requester, new TwilioExistingEnrollmentPlanner.Actor(null, Authority.PLATFORM_APPROVER))
        };
        for (var bad : invalid) {
            assertEquals(Code.INVALID_INTENT, planner.preview(bad, existing).code());
        }
        verifyNoInteractions(carrier);
    }

    @Test
    void explicitlyRejectsTenantAdminsIncorrectPlatformRolesAndOnePersonReview() {
        intent = changed(TENANT, ROW_ID, E164, ACCOUNT, CHANGE,
                new TwilioExistingEnrollmentPlanner.Actor(REQUESTER, Authority.TENANT_ADMIN), approver);
        rejectsBeforeNetwork(Code.PLATFORM_APPROVAL_REQUIRED);

        intent = changed(TENANT, ROW_ID, E164, ACCOUNT, CHANGE,
                new TwilioExistingEnrollmentPlanner.Actor(REQUESTER, null), approver);
        rejectsBeforeNetwork(Code.PLATFORM_APPROVAL_REQUIRED);

        intent = changed(TENANT, ROW_ID, E164, ACCOUNT, CHANGE, requester,
                new TwilioExistingEnrollmentPlanner.Actor(APPROVER, Authority.TENANT_ADMIN));
        rejectsBeforeNetwork(Code.PLATFORM_APPROVAL_REQUIRED);

        intent = changed(TENANT, ROW_ID, E164, ACCOUNT, CHANGE, requester,
                new TwilioExistingEnrollmentPlanner.Actor(APPROVER, null));
        rejectsBeforeNetwork(Code.PLATFORM_APPROVAL_REQUIRED);

        intent = changed(TENANT, ROW_ID, E164, ACCOUNT, CHANGE, requester,
                new TwilioExistingEnrollmentPlanner.Actor(REQUESTER, Authority.PLATFORM_APPROVER));
        rejectsBeforeNetwork(Code.SEPARATION_OF_DUTIES_REQUIRED);
    }

    @Test
    void rejectsAbsentUnpersistedForeignTenantOrDifferentDestinationBeforeTwilio() {
        assertEquals(Code.PHONE_NOT_FOUND, planner.preview(intent, null).code());
        verifyNoInteractions(carrier);
        ReflectionTestUtils.setField(existing, "id", null);
        rejectsBeforeNetwork(Code.PHONE_ROW_MISMATCH);
        ReflectionTestUtils.setField(existing, "id", UUID.randomUUID());
        rejectsBeforeNetwork(Code.PHONE_ROW_MISMATCH);
        ReflectionTestUtils.setField(existing, "id", ROW_ID);
        existing.setBusinessId(UUID.randomUUID());
        rejectsBeforeNetwork(Code.TENANT_MISMATCH);
        existing.setBusinessId(TENANT);
        existing.setPhoneNumber("+12025550124");
        rejectsBeforeNetwork(Code.DESTINATION_MISMATCH);
    }

    @Test
    void refusesWrongProviderAndExistingPurchaseOrOtherProvenance() {
        existing.setProvider("OTHER");
        rejectsBeforeNetwork(Code.PROVIDER_MISMATCH);
        existing.setProvider(null);
        rejectsBeforeNetwork(Code.PROVIDER_MISMATCH);
        existing.setProvider("TWILIO");
        for (String provenance : new String[]{"MANAGED_PURCHASE", "VERIFIED_EXISTING", "BYON", ""}) {
            existing.setTwilioBindingSource(provenance);
            rejectsBeforeNetwork(Code.EXISTING_PROVENANCE);
        }
        existing.setTwilioBindingSource(null);
        assertEquals("TWILIO", existing.getProvider());
    }

    @Test
    void refusesPreexistingVoiceVerificationMalformedSidOrAccountConflict() {
        existing.setVoiceVerifiedAt(Instant.now());
        rejectsBeforeNetwork(Code.EXISTING_VERIFICATION);
        existing.setVoiceVerifiedAt(null);
        existing.setExternalId("not-a-pn");
        rejectsBeforeNetwork(Code.INVALID_STORED_SID);
        existing.setExternalId("");
        rejectsBeforeNetwork(Code.INVALID_STORED_SID);
        existing.setExternalId(null);
        existing.setTwilioAccountSid(OTHER_ACCOUNT);
        rejectsBeforeNetwork(Code.OWNER_CONFLICT);
    }

    @Test
    void matchingPreviouslyStoredOwnerAllowsOnlyReadOnlyReview() {
        existing.setTwilioAccountSid(ACCOUNT);
        validCarrier(PN, ACCOUNT);
        assertEquals(Code.REVIEWABLE_ONLY, decision());
        assertEquals(ACCOUNT, existing.getTwilioAccountSid());
        assertNull(existing.getTwilioBindingSource());
    }

    @Test
    void providerExceptionNeverBecomesApprovalOrLeaksCredentialText() {
        when(carrier.verify(E164, ACCOUNT, null))
                .thenThrow(new IllegalStateException("mocked secret token must never escape"));
        var result = planner.preview(intent, existing);
        assertEquals(Code.CARRIER_REJECTED, result.code());
        assertEquals(TwilioExistingNumberOwnershipVerifier.Code.PROVIDER_UNAVAILABLE,
                result.carrierCode());
        assertFalse(result.reviewable());
        assertNull(result.candidate());
        assertFalse(result.toString().contains("secret"));
        verify(carrier).verify(E164, ACCOUNT, null);
    }

    @Test
    void nullOrCarrierRejectionNeverCreatesCandidate() {
        assertEquals(Code.CARRIER_REJECTED, decision());
        verify(carrier).verify(E164, ACCOUNT, null);
        reset(carrier);
        when(carrier.verify(E164, ACCOUNT, null)).thenReturn(
                new TwilioExistingNumberOwnershipVerifier.Outcome(
                        TwilioExistingNumberOwnershipVerifier.Code.WEBHOOK_MISMATCH, null));
        var result = planner.preview(intent, existing);
        assertEquals(Code.CARRIER_REJECTED, result.code());
        assertEquals(TwilioExistingNumberOwnershipVerifier.Code.WEBHOOK_MISMATCH,
                result.carrierCode());
        assertFalse(result.reviewable());
        assertNull(result.candidate());
        assertEquals("EnrollmentPreview[CARRIER_REJECTED]", result.toString());
        reset(carrier);
        when(carrier.verify(E164, ACCOUNT, null)).thenReturn(null);
        result = planner.preview(intent, existing);
        assertEquals(Code.CARRIER_REJECTED, result.code());
        assertEquals(TwilioExistingNumberOwnershipVerifier.Code.PROVIDER_UNAVAILABLE,
                result.carrierCode());
    }

    @Test
    void contradictoryCarrierAccountOrPhoneSidIsRejectedEvenOnNominalSuccess() {
        validCarrier(PN, OTHER_ACCOUNT);
        assertEquals(Code.OWNER_CONFLICT, decision());
        assertNull(planner.preview(intent, existing).candidate());
        reset(carrier);

        validCarrier("PNshort", ACCOUNT);
        assertEquals(Code.OWNER_CONFLICT, decision());
        reset(carrier);

        existing.setExternalId(PN);
        when(carrier.verify(E164, ACCOUNT, PN)).thenReturn(
                new TwilioExistingNumberOwnershipVerifier.Outcome(
                        TwilioExistingNumberOwnershipVerifier.Code.VERIFIED,
                        new TwilioExistingNumberOwnershipVerifier.Evidence(
                                OTHER_PN, ACCOUNT, AccountKind.MAIN)));
        assertEquals(Code.OWNER_CONFLICT, decision());
        assertEquals(PN, existing.getExternalId());
        assertNull(existing.getTwilioBindingSource());
    }
}
