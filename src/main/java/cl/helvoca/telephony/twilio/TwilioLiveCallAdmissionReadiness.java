package cl.helvoca.telephony.twilio;

import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.security.TenantDatabaseContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Read-only mirror of actual incoming voice admission requirements.
 * A valid PN SID alone is NOT enough to receive a phone call.
 * Carrier ownership must be verified separately from this LOCAL gate.
 */
@Component
public final class TwilioLiveCallAdmissionReadiness {
    private static final Pattern AC = Pattern.compile("AC[0-9a-fA-F]{32}");
    private static final Pattern PN = Pattern.compile("PN[0-9a-fA-F]{32}");

    private final TwilioProperties properties;
    private final String subaccountSid;
    private final TwilioVerifiedExistingVoiceRouteGate existingRoute;
    private final TenantDatabaseContext scope;

    public TwilioLiveCallAdmissionReadiness(
            TwilioProperties properties,
            @Value("${HELVOCA_TWILIO_SUBACCOUNT_SID:}") String subaccountSid,
            TwilioVerifiedExistingVoiceRouteGate existingRoute,
            TenantDatabaseContext scope) {
        this.properties = properties;
        this.subaccountSid = subaccountSid == null ? "" : subaccountSid.trim();
        this.existingRoute = existingRoute;
        this.scope = scope;
    }

    public boolean locallyAllowed(PhoneNumber phone) {
        if (phone == null || phone.getId() == null || phone.getBusinessId() == null
                || !phone.isActive() || !"TWILIO".equalsIgnoreCase(phone.getProvider())
                || phone.getVoiceVerifiedAt() == null
                || phone.getExternalId() == null || !PN.matcher(phone.getExternalId()).matches()
                || phone.getTwilioAccountSid() == null
                || !AC.matcher(phone.getTwilioAccountSid()).matches()
                || !properties.hasAccountSid()
                || !AC.matcher(properties.getAccountSid().trim()).matches()) {
            return false;
        }

        String owner = phone.getTwilioAccountSid();
        String parent = properties.getAccountSid().trim();
        if (!Objects.equals(owner, parent) && !Objects.equals(owner, subaccountSid)) {
            return false;
        }

        return switch (phone.getTwilioBindingSource() == null
                ? "" : phone.getTwilioBindingSource()) {
            case "MANAGED_PURCHASE" -> owner.equals(parent);
            case "VERIFIED_EXISTING" ->
                scope.callAsSystem(() -> existingRoute.allows(phone));
            default -> false;
        };
    }
}
