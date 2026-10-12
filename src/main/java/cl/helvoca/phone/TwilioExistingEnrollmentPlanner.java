package cl.helvoca.phone;

import cl.helvoca.telephony.twilio.TwilioExistingNumberOwnershipVerifier;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Phase 3: a pure, read-only operator enrollment PREVIEW.
 *
 * This is not an authentication boundary: operator identities must be obtained
 * from a trusted platform authorization gateway (phase 6), never tenant JSON.
 * It does not persist, activate, purchase, attach, or authorize any phone.
 */
@Component
public final class TwilioExistingEnrollmentPlanner {
    private static final Pattern E164 = Pattern.compile("\\+[1-9][0-9]{7,14}");
    private static final Pattern ACCOUNT = Pattern.compile("AC[0-9a-fA-F]{32}");
    private static final Pattern PHONE_SID = Pattern.compile("PN[0-9a-fA-F]{32}");

    private final TwilioExistingNumberOwnershipVerifier carrier;

    public TwilioExistingEnrollmentPlanner(TwilioExistingNumberOwnershipVerifier carrier) {
        this.carrier = carrier;
    }

    public enum Authority { PLATFORM_OPERATOR, PLATFORM_APPROVER, TENANT_ADMIN }

    /** Placeholder for phase 6's server-authenticated human principal. */
    public record Actor(UUID subjectId, Authority authority) {}

    /**
     * Trusted server-side intent, not a public controller request.
     * No account/PN SID supplied by tenants may be used as evidence.
     */
    public record Intent(UUID businessId, UUID persistedPhoneId,
                         String destinationE164, String ownerAccountSid,
                         UUID changeId, Actor requester, Actor approver) {
        @Override public String toString() { return "EnrollmentIntent[REDACTED]"; }
    }

    public enum Code {
        INVALID_INTENT, PLATFORM_APPROVAL_REQUIRED, SEPARATION_OF_DUTIES_REQUIRED,
        PHONE_NOT_FOUND, PHONE_ROW_MISMATCH, TENANT_MISMATCH,
        DESTINATION_MISMATCH, PROVIDER_MISMATCH, EXISTING_PROVENANCE,
        EXISTING_VERIFICATION, INVALID_STORED_SID, OWNER_CONFLICT,
        CARRIER_REJECTED, REVIEWABLE_ONLY
    }

    /**
     * Intentionally inert projection. Must never be treated as write permission:
     * phase 5 must re-read, compare-and-set and re-attest carrier state.
     */
    public record Candidate(UUID changeId, UUID businessId, UUID phoneId,
                            String destinationE164, String verifiedPhoneSid,
                            String ownerAccountSid, UUID requesterId,
                            UUID approverId, String proposedSource) {
        @Override public String toString() { return "EnrollmentCandidate[REDACTED]"; }
    }

    /** Only diagnostic codes are serializable: carrier and tenant IDs stay internal. */
    public record Preview(Code code, TwilioExistingNumberOwnershipVerifier.Code carrierCode,
                          @JsonIgnore Candidate candidate) {
        public boolean reviewable() { return code == Code.REVIEWABLE_ONLY && candidate != null; }
        @Override public String toString() {
            return "EnrollmentPreview[" + code + "]";
        }
    }

    private static Preview blocked(Code code) { return new Preview(code, null, null); }

    /**
     * Never do HTTP before validating the row and independent human approvals.
     * Does not query repositories; the caller must load the row in a protected
     * tenant/system context and require platform authority before calling.
     */
    public Preview preview(Intent intent, PhoneNumber persisted) {
        if (intent == null
                || intent.businessId() == null
                || intent.persistedPhoneId() == null
                || intent.changeId() == null
                || intent.destinationE164() == null
                || !E164.matcher(intent.destinationE164()).matches()
                || intent.ownerAccountSid() == null
                || !ACCOUNT.matcher(intent.ownerAccountSid()).matches()
                || intent.requester() == null
                || intent.approver() == null
                || intent.requester().subjectId() == null
                || intent.approver().subjectId() == null) {
            return blocked(Code.INVALID_INTENT);
        }
        if (intent.requester().authority() != Authority.PLATFORM_OPERATOR
                || intent.approver().authority() != Authority.PLATFORM_APPROVER) {
            return blocked(Code.PLATFORM_APPROVAL_REQUIRED);
        }
        if (intent.requester().subjectId().equals(intent.approver().subjectId())) {
            return blocked(Code.SEPARATION_OF_DUTIES_REQUIRED);
        }
        if (persisted == null) return blocked(Code.PHONE_NOT_FOUND);
        if (!intent.persistedPhoneId().equals(persisted.getId())) {
            return blocked(Code.PHONE_ROW_MISMATCH);
        }
        if (!intent.businessId().equals(persisted.getBusinessId())) {
            return blocked(Code.TENANT_MISMATCH);
        }
        if (!intent.destinationE164().equals(persisted.getPhoneNumber())) {
            return blocked(Code.DESTINATION_MISMATCH);
        }
        if (!"TWILIO".equalsIgnoreCase(persisted.getProvider())) {
            return blocked(Code.PROVIDER_MISMATCH);
        }
        // Never rewrite a purchased, independently enrolled, or manual source.
        if (persisted.getTwilioBindingSource() != null) {
            return blocked(Code.EXISTING_PROVENANCE);
        }
        if (persisted.getVoiceVerifiedAt() != null) {
            return blocked(Code.EXISTING_VERIFICATION);
        }
        String storedSid = persisted.getExternalId();
        if (storedSid != null && !PHONE_SID.matcher(storedSid).matches()) {
            return blocked(Code.INVALID_STORED_SID);
        }
        String storedAccount = persisted.getTwilioAccountSid();
        if (storedAccount != null && !storedAccount.equals(intent.ownerAccountSid())) {
            return blocked(Code.OWNER_CONFLICT);
        }

        TwilioExistingNumberOwnershipVerifier.Outcome outcome;
        try {
            outcome = carrier.verify(intent.destinationE164(), intent.ownerAccountSid(), storedSid);
        } catch (RuntimeException ignored) {
            // No exception detail may be surfaced to a tenant.
            return new Preview(Code.CARRIER_REJECTED,
                    TwilioExistingNumberOwnershipVerifier.Code.PROVIDER_UNAVAILABLE, null);
        }
        if (outcome == null || !outcome.verified()) {
            return new Preview(Code.CARRIER_REJECTED,
                    outcome == null ? TwilioExistingNumberOwnershipVerifier.Code.PROVIDER_UNAVAILABLE : outcome.code(),
                    null);
        }
        var evidence = outcome.evidence();
        if (!intent.ownerAccountSid().equals(evidence.ownerAccountSid())
                || !PHONE_SID.matcher(evidence.phoneSid()).matches()
                || storedSid != null && !storedSid.equals(evidence.phoneSid())) {
            return blocked(Code.OWNER_CONFLICT);
        }
        return new Preview(Code.REVIEWABLE_ONLY, TwilioExistingNumberOwnershipVerifier.Code.VERIFIED,
                new Candidate(intent.changeId(), intent.businessId(), intent.persistedPhoneId(),
                        intent.destinationE164(), evidence.phoneSid(), evidence.ownerAccountSid(),
                        intent.requester().subjectId(), intent.approver().subjectId(),
                        "VERIFIED_EXISTING"));
    }
}
