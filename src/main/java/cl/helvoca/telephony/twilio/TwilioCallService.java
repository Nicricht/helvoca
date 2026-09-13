package cl.helvoca.telephony.twilio;

import cl.helvoca.call.CallStatus;
import cl.helvoca.telephony.CallLifecycleService;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Thin Twilio adapter for carrier callbacks and pre-routing inbound admission.
 */
@Service
public class TwilioCallService {
    private final CallLifecycleService lifecycle;

    public TwilioCallService(CallLifecycleService lifecycle) {
        this.lifecycle = lifecycle;
    }

    public UUID startInboundCall(String providerCallId, String caller, String destination) {
        return lifecycle.startInboundCall("twilio", providerCallId, caller, destination);
    }

    public UUID updateStatus(String providerCallId, String providerStatus, Integer durationSeconds) {
        return lifecycle.updateStatus(providerCallId, providerStatus, durationSeconds);
    }

    public void markStreamStopped(String streamSid) {
        lifecycle.markStreamStopped(streamSid);
    }

    static CallStatus mapStatus(String value) {
        return CallLifecycleService.mapStatus(value);
    }
}
