package cl.helvoca.telephony.twilio;

import cl.helvoca.business.BusinessMode;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.business.BusinessStatus;
import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.phone.PhoneNumberRepository;
import cl.helvoca.security.TenantDatabaseContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Restricts certification to a carrier-verified number owned by one active DEMO tenant. */
@Component
public final class TwilioCertificationDemoBindingGate {
    private final UUID pinnedBusinessId;
    private final String from;
    private final String accountSid;
    private final PhoneNumberRepository phones;
    private final BusinessRepository businesses;
    private final TwilioVerifiedExistingVoiceRouteGate existingRoute;
    private final TenantDatabaseContext databaseContext;

    public TwilioCertificationDemoBindingGate(
            @Value("${TWILIO_CERTIFICATION_DEMO_BUSINESS_ID:}") String businessId,
            @Value("${TWILIO_TEST_FROM:}") String from,
            @Value("${app.twilio.account-sid:}") String accountSid,
            PhoneNumberRepository phones,
            BusinessRepository businesses,
            TwilioVerifiedExistingVoiceRouteGate existingRoute,
            TenantDatabaseContext databaseContext) {
        this.pinnedBusinessId = parseUuid(businessId);
        this.from = from;
        this.accountSid = accountSid;
        this.phones = phones;
        this.businesses = businesses;
        this.existingRoute = existingRoute;
        this.databaseContext = databaseContext;
    }

    public UUID pinnedBusinessId() { return pinnedBusinessId; }

    public boolean allowsNumber(String businessPhone) {
        return from != null && from.equals(businessPhone) && allows(pinnedBusinessId);
    }

    public boolean allows(UUID commandBusinessId) {
        if (pinnedBusinessId == null || !pinnedBusinessId.equals(commandBusinessId)
                || from == null || from.isBlank() || accountSid == null
                || !accountSid.matches("AC[0-9a-fA-F]{32}")) return false;
        try {
            return databaseContext.callAsSystem(() -> phones.findByPhoneNumberAndActiveTrue(from)
                    .filter(phone -> carrierBound(phone, commandBusinessId))
                    .filter(phone -> businesses.findById(commandBusinessId)
                            .filter(business -> business.getMode() == BusinessMode.DEMO)
                            .filter(business -> business.getStatus() == BusinessStatus.ACTIVE)
                            .isPresent())
                    .filter(existingRoute::allows)
                    .isPresent());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean carrierBound(PhoneNumber phone, UUID businessId) {
        return phone.isActive() && businessId.equals(phone.getBusinessId())
                && "TWILIO".equalsIgnoreCase(phone.getProvider())
                && "VERIFIED_EXISTING".equals(phone.getTwilioBindingSource())
                && phone.getVoiceVerifiedAt() != null
                && accountSid.equals(phone.getTwilioAccountSid())
                && phone.getExternalId() != null
                && phone.getExternalId().matches("PN[0-9a-fA-F]{32}");
    }

    private static UUID parseUuid(String value) {
        try { return UUID.fromString(value == null ? "" : value.trim()); }
        catch (IllegalArgumentException ignored) { return null; }
    }
}
