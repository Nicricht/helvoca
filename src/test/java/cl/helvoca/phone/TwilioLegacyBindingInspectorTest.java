package cl.helvoca.phone;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TwilioLegacyBindingInspectorTest {
    private static final UUID BUSINESS = UUID.fromString("7aaaf4f7-3e0b-4a55-812c-bf3c972bbf55");
    private static final String PHONE = "+12025550123";
    private static final String PHONE_SID = "PN" + "a".repeat(32);
    private static final String ACCOUNT_SID = "AC" + "b".repeat(32);

    private final TwilioLegacyBindingInspector inspector = new TwilioLegacyBindingInspector();

    private static PhoneNumber completeRow() {
        PhoneNumber row = new PhoneNumber();
        row.setBusinessId(BUSINESS);
        row.setPhoneNumber(PHONE);
        row.setProvider("TWILIO");
        row.setActive(true);
        row.setExternalId(PHONE_SID);
        row.setTwilioBindingSource("MANAGED_PURCHASE");
        row.setTwilioAccountSid(ACCOUNT_SID);
        row.setVoiceVerifiedAt(Instant.parse("2026-01-01T00:00:00Z"));
        return row;
    }

    private List<TwilioLegacyBindingInspector.Gap> gaps(PhoneNumber row) {
        return inspector.inspect(row, BUSINESS, PHONE).gaps();
    }

    @Test
    void rejectsMissingTenantNullDestinationAndMalformedTargetBeforeReadingAnyRow() {
        PhoneNumber row = completeRow();
        assertEquals(List.of(TwilioLegacyBindingInspector.Gap.INVALID_TARGET),
                inspector.inspect(row, null, PHONE).gaps());
        assertEquals(List.of(TwilioLegacyBindingInspector.Gap.INVALID_TARGET),
                inspector.inspect(row, BUSINESS, null).gaps());
        assertEquals(List.of(TwilioLegacyBindingInspector.Gap.INVALID_TARGET),
                inspector.inspect(row, BUSINESS, "435-565-2512").gaps());
    }

    @Test
    void missingRowNeverClaimsARegisteredNumber() {
        var result = inspector.inspect(null, BUSINESS, PHONE);
        assertEquals(List.of(TwilioLegacyBindingInspector.Gap.PHONE_NOT_REGISTERED), result.gaps());
        assertFalse(result.hasCompletePersistedFields());
    }

    @Test
    void findsEveryMissingFieldInLegacyPilotRatherThanPromotingIt() {
        PhoneNumber row = completeRow();
        row.setExternalId(null);
        row.setTwilioBindingSource(null);
        row.setTwilioAccountSid(null);
        row.setVoiceVerifiedAt(null);

        var result = inspector.inspect(row, BUSINESS, PHONE);
        assertEquals(List.of(
                TwilioLegacyBindingInspector.Gap.PHONE_SID_MISSING,
                TwilioLegacyBindingInspector.Gap.PROVENANCE_UNVERIFIED,
                TwilioLegacyBindingInspector.Gap.ACCOUNT_SID_MISSING,
                TwilioLegacyBindingInspector.Gap.VOICE_NOT_VERIFIED), result.gaps());
        assertFalse(result.hasCompletePersistedFields());
        assertNull(row.getExternalId(), "Inspection is strictly read-only");
        assertNull(row.getTwilioBindingSource());
        assertNull(row.getTwilioAccountSid());
        assertNull(row.getVoiceVerifiedAt());
    }

    @Test
    void completePersistedFieldsStillNeedIndependentCarrierAttestation() {
        PhoneNumber row = completeRow();
        var result = inspector.inspect(row, BUSINESS, PHONE);
        assertTrue(result.hasCompletePersistedFields());
        assertTrue(result.gaps().isEmpty());
        assertEquals(PHONE_SID, row.getExternalId());
        assertEquals(ACCOUNT_SID, row.getTwilioAccountSid());
    }

    @Test
    void verifiedExistingWithAllStoredFieldsIsLocallyCompleteButNotCarrierAttested() {
        PhoneNumber row = completeRow();
        row.setTwilioBindingSource("VERIFIED_EXISTING");

        var result = inspector.inspect(row, BUSINESS, PHONE);
        assertTrue(result.hasCompletePersistedFields());
        assertTrue(result.gaps().isEmpty());
        assertEquals("VERIFIED_EXISTING", row.getTwilioBindingSource());
        assertEquals(PHONE_SID, row.getExternalId());
        assertEquals(ACCOUNT_SID, row.getTwilioAccountSid());
    }

    @Test
    void verifiedExistingWithoutCarrierSidAccountOrTimestampRemainsIncomplete() {
        PhoneNumber row = completeRow();
        row.setTwilioBindingSource("VERIFIED_EXISTING");
        row.setExternalId(null);
        row.setTwilioAccountSid(null);
        row.setVoiceVerifiedAt(null);

        assertEquals(List.of(
                TwilioLegacyBindingInspector.Gap.PHONE_SID_MISSING,
                TwilioLegacyBindingInspector.Gap.ACCOUNT_SID_MISSING,
                TwilioLegacyBindingInspector.Gap.VOICE_NOT_VERIFIED), gaps(row));
        assertEquals("VERIFIED_EXISTING", row.getTwilioBindingSource());
    }

    @Test
    void unknownOrNullProvenanceNeverBecomesApprovedJustBecauseSidsMatch() {
        PhoneNumber row = completeRow();
        for (String source : new String[]{null, "", "BYON", "MANUAL", "UNKNOWN"}) {
            row.setTwilioBindingSource(source);
            assertEquals(List.of(TwilioLegacyBindingInspector.Gap.PROVENANCE_UNVERIFIED),
                    gaps(row));
        }
    }

    @Test
    void rejectsOtherTenantDifferentDestinationWrongProviderAndInactiveNumber() {
        PhoneNumber row = completeRow();
        row.setBusinessId(UUID.randomUUID());
        row.setPhoneNumber("+12025550124");
        row.setProvider("OTHER");
        row.setActive(false);

        assertEquals(List.of(
                TwilioLegacyBindingInspector.Gap.TENANT_MISMATCH,
                TwilioLegacyBindingInspector.Gap.DESTINATION_MISMATCH,
                TwilioLegacyBindingInspector.Gap.PROVIDER_MISMATCH,
                TwilioLegacyBindingInspector.Gap.INACTIVE), gaps(row));
    }

    @Test
    void refusesBlankAndMalformedPhoneSidWithoutWritingEvidence() {
        PhoneNumber row = completeRow();
        row.setExternalId(" ");
        assertEquals(List.of(TwilioLegacyBindingInspector.Gap.PHONE_SID_MISSING), gaps(row));

        row.setExternalId("PNnot-a-real-sid");
        assertEquals(List.of(TwilioLegacyBindingInspector.Gap.PHONE_SID_INVALID), gaps(row));
    }

    @Test
    void refusesBlankAndMalformedAccountSidWithoutTreatingAnyAccountAsOwner() {
        PhoneNumber row = completeRow();
        row.setTwilioAccountSid("  ");
        assertEquals(List.of(TwilioLegacyBindingInspector.Gap.ACCOUNT_SID_MISSING), gaps(row));

        row.setTwilioAccountSid("ACnot-an-account");
        assertEquals(List.of(TwilioLegacyBindingInspector.Gap.ACCOUNT_SID_INVALID), gaps(row));
    }

    @Test
    void refusesUnprovenByonOrManualProvenanceDespiteValidSidFormats() {
        PhoneNumber row = completeRow();
        row.setTwilioBindingSource("BYON");
        assertEquals(List.of(TwilioLegacyBindingInspector.Gap.PROVENANCE_UNVERIFIED), gaps(row));
        assertEquals("BYON", row.getTwilioBindingSource());
    }

    @Test
    void assessmentCannotBeModifiedOrRewrittenWithAnotherList() {
        PhoneNumber row = completeRow();
        row.setExternalId(null);
        var result = inspector.inspect(row, BUSINESS, PHONE);
        assertThrows(UnsupportedOperationException.class,
                () -> result.gaps().add(TwilioLegacyBindingInspector.Gap.VOICE_NOT_VERIFIED));

        var mutable = new java.util.ArrayList<>(result.gaps());
        var snapshot = new TwilioLegacyBindingInspector.Assessment(mutable);
        mutable.clear();
        assertEquals(List.of(TwilioLegacyBindingInspector.Gap.PHONE_SID_MISSING), snapshot.gaps());
    }
}
