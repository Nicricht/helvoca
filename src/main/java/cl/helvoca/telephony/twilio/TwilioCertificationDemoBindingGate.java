package cl.helvoca.telephony.twilio;

import cl.helvoca.business.BusinessMode;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.business.BusinessStatus;
import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.phone.PhoneNumberRepository;
import cl.helvoca.security.TenantDatabaseContext;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final boolean scopedCustomerDemoEnabled;

    @Autowired
    public TwilioCertificationDemoBindingGate(
            @Value("${TWILIO_CERTIFICATION_DEMO_BUSINESS_ID:}") String businessId,
            @Value("${TWILIO_TEST_FROM:}") String from,
            @Value("${app.twilio.account-sid:}") String accountSid,
            PhoneNumberRepository phones,
            BusinessRepository businesses,
            TwilioVerifiedExistingVoiceRouteGate existingRoute,
            TenantDatabaseContext databaseContext,
            @Value("${TWILIO_CERTIFICATION_SCOPED_CUSTOMER_DEMO_ENABLED:false}") boolean scopedCustomerDemoEnabled) {
        this.pinnedBusinessId = parseUuid(businessId);
        this.scopedCustomerDemoEnabled = scopedCustomerDemoEnabled;
        this.from = from;
        this.accountSid = accountSid;
        this.phones = phones;
        this.businesses = businesses;
        this.existingRoute = existingRoute;
        this.databaseContext = databaseContext;
    }

    TwilioCertificationDemoBindingGate(String businessId, String from, String accountSid,
            PhoneNumberRepository phones, BusinessRepository businesses,
            TwilioVerifiedExistingVoiceRouteGate existingRoute, TenantDatabaseContext databaseContext) {
        this(businessId, from, accountSid, phones, businesses, existingRoute, databaseContext, false);
    }

    public UUID pinnedBusinessId() { return pinnedBusinessId; }


    /**
     * Read-only classification for an opt-in DEMO diagnostic. No carrier calls,
     * secret values, phone numbers, tenant IDs, writes or SQL errors are returned.
     */
    public String readinessBlocker() {
        if (pinnedBusinessId == null || from == null || from.isBlank()
                || accountSid == null || !accountSid.matches("AC[0-9a-fA-F]{32}")) {
            return "PINS_INVALID";
        }
        try {
            return databaseContext.callAsSystem(() -> {
                var found = phones.findByPhoneNumber(from);
                if (found.isEmpty()) return "PHONE_NOT_STAGED";
                PhoneNumber phone = found.get();
                if (!phone.isActive()) return "PHONE_INACTIVE";
                if (!carrierBound(phone, pinnedBusinessId)) return "CARRIER_BINDING_NOT_VERIFIED";
                var business = businesses.findById(pinnedBusinessId);
                if (business.isEmpty()) return "BUSINESS_NOT_FOUND";
                if (business.get().getMode() != BusinessMode.DEMO
                        && !(scopedCustomerDemoEnabled
                             && business.get().getMode() == BusinessMode.CUSTOMER)) {
                    return "BUSINESS_NOT_SCOPED";
                }
                if (business.get().getStatus() != BusinessStatus.ACTIVE) {
                    return "BUSINESS_NOT_ACTIVE";
                }
                return existingRoute.allows(phone) ? "READY" : "EXISTING_ROUTE_PIN_OR_PROOF_DENIED";
            });
        } catch (RuntimeException ignored) {
            return "DATABASE_QUERY_BLOCKED";
        }
    }

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
                            .filter(business -> business.getMode() == BusinessMode.DEMO
                                    || scopedCustomerDemoEnabled
                                       && business.getMode() == BusinessMode.CUSTOMER)
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
