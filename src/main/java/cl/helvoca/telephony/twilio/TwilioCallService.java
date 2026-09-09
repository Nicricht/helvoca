package cl.helvoca.telephony.twilio;

import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.call.CallStatus;
import cl.helvoca.telephony.CallLifecycleService;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Twilio adapter around Helvoca's provider-neutral call lifecycle.
 *
 * <p>Twilio-specific response generation stays here while durable call state
 * and business rules live in {@link CallLifecycleService}.</p>
 */
@Service
public class TwilioCallService {
    public static final String PROVIDER_ID = "twilio";

    private final CallLifecycleService lifecycle;
    private final TwimlFactory twiml;

    public TwilioCallService(CallLifecycleService lifecycle,
                             TwimlFactory twiml) {
        this.lifecycle = lifecycle;
        this.twiml = twiml;
    }

    public String startInboundCall(String providerCallId, String from, String to) {
        UUID callId = lifecycle.startInboundCall(PROVIDER_ID, providerCallId, from, to);
        return twiml.connectMediaStream(callId);
    }

    public RealtimeCallContext startTrialInboundCall(String providerCallId, String from, String to) {
        return lifecycle.startTrialCall(PROVIDER_ID, providerCallId, from, to);
    }

    public RealtimeCallContext getTrialContext(String providerCallId) {
        return lifecycle.getTrialContext(providerCallId);
    }

    public void markTrialEnded(String providerCallId) {
        lifecycle.markTrialEnded(providerCallId);
    }

    public void updateStatus(String providerCallId, String providerStatus, Integer durationSeconds) {
        lifecycle.updateStatus(providerCallId, providerStatus, durationSeconds);
    }

    public RealtimeCallContext markStreamStarted(UUID callId,
                                                 String providerCallId,
                                                 String streamSid,
                                                 String aiProvider) {
        return lifecycle.markStreamStarted(callId, providerCallId, streamSid, aiProvider);
    }

    /**
     * Compatibility overload for existing tests and callers while the provider
     * abstraction is rolled out. New realtime callers should pass the active AI provider.
     */
    public RealtimeCallContext markStreamStarted(UUID callId, String providerCallId, String streamSid) {
        return markStreamStarted(callId, providerCallId, streamSid, "openai");
    }

    public void markStreamStopped(String streamSid) {
        lifecycle.markStreamStopped(streamSid);
    }

    static CallStatus mapStatus(String value) {
        return CallLifecycleService.mapStatus(value);
    }
}
