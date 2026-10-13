package cl.helvoca.phone;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Read-only, fail-closed inventory inspection for a number registered before
 * managed Twilio ownership evidence was required.
 *
 * This does NOT attest Twilio ownership, authorize calls, or update a phone.
 * A complete local record still requires a fresh authenticated carrier GET
 * and a separately authorized operator enrollment before commercial use.
 */
@Component
public class TwilioLegacyBindingInspector {
    private static final Pattern E164 = Pattern.compile("^\\+[1-9][0-9]{7,14}$");
    private static final Pattern PHONE_SID = Pattern.compile("^PN[0-9a-fA-F]{32}$");
    private static final Pattern ACCOUNT_SID = Pattern.compile("^AC[0-9a-fA-F]{32}$");

    public enum Gap {
        INVALID_TARGET,
        PHONE_NOT_REGISTERED,
        TENANT_MISMATCH,
        DESTINATION_MISMATCH,
        PROVIDER_MISMATCH,
        INACTIVE,
        PHONE_SID_MISSING,
        PHONE_SID_INVALID,
        PROVENANCE_UNVERIFIED,
        ACCOUNT_SID_MISSING,
        ACCOUNT_SID_INVALID,
        VOICE_NOT_VERIFIED
    }

    /** Only diagnostic codes are returned; never expose account/number SIDs to tenant clients. */
    public record Assessment(List<Gap> gaps) {
        public Assessment {
            gaps = List.copyOf(gaps);
        }

        /**
         * Local metadata completeness only. NOT a carrier-attested certification
         * and NOT a commercial GO decision.
         */
        public boolean hasCompletePersistedFields() {
            return gaps.isEmpty();
        }
    }

    /**
     * Compares one persisted row with a trusted operator-supplied target.
     * The caller must enforce operator authority and tenant-scoped DB access.
     * This method makes no network calls and performs no persistence.
     */
    public Assessment inspect(PhoneNumber phone, UUID expectedBusinessId, String destinationE164) {
        if (expectedBusinessId == null || destinationE164 == null
                || !E164.matcher(destinationE164).matches()) {
            return new Assessment(List.of(Gap.INVALID_TARGET));
        }
        if (phone == null) {
            return new Assessment(List.of(Gap.PHONE_NOT_REGISTERED));
        }

        List<Gap> gaps = new ArrayList<>();
        if (!expectedBusinessId.equals(phone.getBusinessId())) gaps.add(Gap.TENANT_MISMATCH);
        if (!destinationE164.equals(phone.getPhoneNumber())) gaps.add(Gap.DESTINATION_MISMATCH);
        if (!"TWILIO".equalsIgnoreCase(phone.getProvider())) gaps.add(Gap.PROVIDER_MISMATCH);
        if (!phone.isActive()) gaps.add(Gap.INACTIVE);

        String phoneSid = phone.getExternalId();
        if (phoneSid == null || phoneSid.isBlank()) {
            gaps.add(Gap.PHONE_SID_MISSING);
        } else if (!PHONE_SID.matcher(phoneSid).matches()) {
            gaps.add(Gap.PHONE_SID_INVALID);
        }

        // Legacy/manual entries must never be silently promoted to a purchased number.
        String source = phone.getTwilioBindingSource();
        // Managed purchases and independently attested existing numbers are
        // distinct, legitimate evidence classes. A complete database row
        // still cannot replace fresh carrier proof or platform approval.
        if (!"MANAGED_PURCHASE".equals(source)
                && !"VERIFIED_EXISTING".equals(source)) {
            gaps.add(Gap.PROVENANCE_UNVERIFIED);
        }

        String accountSid = phone.getTwilioAccountSid();
        if (accountSid == null || accountSid.isBlank()) {
            gaps.add(Gap.ACCOUNT_SID_MISSING);
        } else if (!ACCOUNT_SID.matcher(accountSid).matches()) {
            gaps.add(Gap.ACCOUNT_SID_INVALID);
        }
        if (phone.getVoiceVerifiedAt() == null) gaps.add(Gap.VOICE_NOT_VERIFIED);
        return new Assessment(gaps);
    }
}
