package cl.helvoca.operations;

import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.phone.PhoneNumberRepository;
import cl.helvoca.security.TenantProvider;
import cl.helvoca.telephony.twilio.TwilioExistingNumberOwnershipVerifier;
import cl.helvoca.telephony.twilio.TwilioLiveCallAdmissionReadiness;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * On-demand, admin-only caller: performs Twilio GET-only ownership checks
 * for a number already registered to the authenticated tenant.
 *
 * Never changes carrier resources, local rows, secrets or pilot switches.
 */
@Service
public final class VoiceCarrierOwnershipPreflightService {
    private static final Pattern E164 = Pattern.compile("\\+[1-9][0-9]{7,14}");
    private static final Pattern ACCOUNT = Pattern.compile("AC[0-9a-fA-F]{32}");
    private static final Pattern PHONE = Pattern.compile("PN[0-9a-fA-F]{32}");

    private final TenantProvider tenant;
    private final PhoneNumberRepository numbers;
    private final TwilioExistingNumberOwnershipVerifier carrier;
    private final TwilioLiveCallAdmissionReadiness localAdmission;

    public VoiceCarrierOwnershipPreflightService(
            TenantProvider tenant,
            PhoneNumberRepository numbers,
            TwilioExistingNumberOwnershipVerifier carrier,
            TwilioLiveCallAdmissionReadiness localAdmission) {
        this.tenant = tenant;
        this.numbers = numbers;
        this.carrier = carrier;
        this.localAdmission = localAdmission;
    }

    @Transactional(readOnly = true)
    public Result inspect(String destination) {
        UUID business = tenant.requireBusinessId();
        if (destination == null || !E164.matcher(destination).matches()) {
            return result("INVALID_DESTINATION", "INVALID_TARGET", false, false);
        }

        // Never use a global lookup for a tenant-supplied phone number.
        PhoneNumber phone = numbers.findAllByBusinessIdOrderByCreatedAtDesc(business).stream()
                .filter(row -> destination.equals(row.getPhoneNumber()))
                .findFirst().orElse(null);
        if (phone == null || !phone.isActive()
                || !"TWILIO".equalsIgnoreCase(phone.getProvider())
                || phone.getTwilioAccountSid() == null
                || !ACCOUNT.matcher(phone.getTwilioAccountSid()).matches()
                || phone.getExternalId() == null
                || !PHONE.matcher(phone.getExternalId()).matches()) {
            return result("LOCAL_BINDING_INCOMPLETE", "NOT_CHECKED", false, false);
        }

        boolean localAllowed = localAdmission.locallyAllowed(phone);
        TwilioExistingNumberOwnershipVerifier.Outcome observation =
                carrier.verify(destination, phone.getTwilioAccountSid(), phone.getExternalId());

        // Carrier proof and platform launch authorization are independent.
        boolean verified = observation.verified();
        return result(verified && localAllowed ? "CARRIER_AND_LOCAL_CHECKS_PASSED"
                : "NOT_READY", observation.code().name(), verified, localAllowed);
    }

    private static Result result(String state, String carrierCode, boolean verified, boolean allowed) {
        return new Result(state, carrierCode, verified, allowed, false, false);
    }

    public record Result(String state,
                         String carrierCode,
                         boolean carrierOwnershipVerified,
                         boolean localCallAdmissionAllowed,
                         boolean liveCallCertified,
                         boolean commercialGo) {}
}
