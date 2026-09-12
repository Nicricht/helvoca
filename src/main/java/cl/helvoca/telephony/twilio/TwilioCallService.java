package cl.helvoca.telephony.twilio;

import cl.helvoca.call.CallStatus;
import cl.helvoca.telephony.CallLifecycleService;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Thin Twilio adapter for carrier status callbacks. GPT-Live SIP call creation
 * is owned by OpenAiLiveSipService and the provider-neutral CallLifecycleService.
 */
@Service
public class TwilioCallService {
    private final CallLifecycleService lifecycle;

    public TwilioCallService(CallLifecycleService lifecycle) {
        this.lifecycle = lifecycle;
    }

    public UUID updateStatus(String providerCallId, String providerStatus, Integer durationSeconds) {
        return lifecycle.updateStatus(providerCallId, providerStatus, durationSeconds);
    }

    static CallStatus mapStatus(String value) {
        return CallLifecycleService.mapStatus(value);
    }
}
