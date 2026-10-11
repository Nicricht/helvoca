package cl.helvoca.telephony.twilio;

import cl.helvoca.call.CallSession;
import cl.helvoca.call.CallSessionRepository;
import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.phone.PhoneNumberRepository;
import cl.helvoca.security.TenantDatabaseContext;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Fail-closed boundary between authenticated Twilio callbacks and tenant data.
 * Carrier signatures are checked by TwilioSignatureValidationFilter; this guard
 * additionally binds the signed AccountSid, destination and existing call to
 * one certified managed phone before any tenant-owned operation can start.
 */
@Component
public class TwilioCallBindingGuard {
    private final PhoneNumberRepository phones;
    private final CallSessionRepository calls;
    private final TenantDatabaseContext databaseContext;

    public TwilioCallBindingGuard(PhoneNumberRepository phones,
                                  CallSessionRepository calls,
                                  TenantDatabaseContext databaseContext) {
        this.phones = phones;
        this.calls = calls;
        this.databaseContext = databaseContext;
    }

    public boolean allowsIncoming(String destination, String accountSid) {
        if (blank(destination) || blank(accountSid)) return false;
        return databaseContext.callAsSystem(() -> phones.findByPhoneNumberAndActiveTrue(destination)
                .filter(phone -> certified(phone, accountSid)).isPresent());
    }

    public boolean allowsCallback(String callSid, String accountSid) {
        if (blank(callSid) || blank(accountSid)) return false;
        return databaseContext.callAsSystem(() -> calls.findByProviderCallId(callSid)
                .filter(call -> ownsCall(call, accountSid)).isPresent());
    }

    public boolean allowsStreamCallback(String callSid, String streamSid, String accountSid) {
        if (blank(streamSid)) return false;
        if (!allowsCallback(callSid, accountSid)) return false;
        return databaseContext.callAsSystem(() -> calls.findByProviderCallId(callSid)
                .filter(call -> streamSid.equals(call.getStreamSid())).isPresent());
    }

    public boolean allowsMedia(String callSid, String destination, String caller, String accountSid) {
        if (blank(callSid) || blank(destination) || blank(caller) || blank(accountSid)) return false;
        return databaseContext.callAsSystem(() -> {
            PhoneNumber phone = phones.findByPhoneNumberAndActiveTrue(destination).orElse(null);
            if (!certified(phone, accountSid)) return false;
            return calls.findByProviderCallId(callSid)
                    .filter(call -> boundTo(call, phone, caller, destination))
                    .filter(call -> call.getStatus() != null && !call.getStatus().terminal())
                    .isPresent();
        });
    }

    private boolean ownsCall(CallSession call, String accountSid) {
        if (call.getPhoneNumberId() == null) return false;
        PhoneNumber phone = phones.findById(call.getPhoneNumberId()).orElse(null);
        return certified(phone, accountSid)
                && boundTo(call, phone, call.getCallerNumber(), phone.getPhoneNumber());
    }

    private static boolean boundTo(CallSession call, PhoneNumber phone,
                                   String caller, String destination) {
        return "twilio".equalsIgnoreCase(call.getTelephonyProvider())
                && Objects.equals(call.getBusinessId(), phone.getBusinessId())
                && Objects.equals(call.getPhoneNumberId(), phone.getId())
                && Objects.equals(call.getDestinationNumber(), destination)
                && Objects.equals(call.getCallerNumber(), caller);
    }

    private static boolean certified(PhoneNumber phone, String accountSid) {
        return phone != null
                && phone.isActive()
                && "TWILIO".equalsIgnoreCase(phone.getProvider())
                && "MANAGED_PURCHASE".equals(phone.getTwilioBindingSource())
                && phone.getVoiceVerifiedAt() != null
                && accountSid.equals(phone.getTwilioAccountSid())
                && accountSid.matches("AC[0-9a-fA-F]{32}")
                && phone.getExternalId() != null
                && phone.getExternalId().matches("PN[0-9a-fA-F]{32}")
                && phone.getBusinessId() != null;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
