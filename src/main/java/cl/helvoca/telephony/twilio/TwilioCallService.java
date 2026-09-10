package cl.helvoca.telephony.twilio;

import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.call.CallStatus;
import cl.helvoca.telephony.CallLifecycleService;
import cl.helvoca.telephony.twilio.trial.TrialVoiceProperties;
import cl.helvoca.voice.VoiceProviderProperties;
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
    private final VoiceProviderProperties voiceProviders;
    private final TrialVoiceProperties trial;

    public TwilioCallService(CallLifecycleService lifecycle,
                             TwimlFactory twiml,
                             VoiceProviderProperties voiceProviders,
                             TrialVoiceProperties trial) {
        this.lifecycle = lifecycle;
        this.twiml = twiml;
        this.voiceProviders = voiceProviders;
        this.trial = trial;
    }

    public String startInboundCall(String providerCallId, String from, String to) {
        UUID callId = lifecycle.startInboundCall(activeProviderId(), providerCallId, from, to);
        return twiml.connectMediaStream(callId);
    }

    public RealtimeCallContext startTrialInboundCall(String providerCallId, String from, String to) {
        String effectiveFrom = from;
        String effectiveTo = to;

        // Twilio's Console "Try out Voice" flow may originate the outbound test
        // from a platform-owned number instead of echoing the configured trial
        // number in From/To. Trial mode is already explicitly enabled and scoped
        // to a single demo number, so use that configured number as the carrier
        // identity fallback when neither webhook number matches it.
        if (trial.isEnabled() && trial.hasPhoneNumber()) {
            String trialNumber = trial.getPhoneNumber();
            boolean webhookContainsTrialNumber = trialNumber.equals(from) || trialNumber.equals(to);
            if (!webhookContainsTrialNumber) {
                effectiveFrom = trialNumber;
            }
        }

        return lifecycle.startTrialCall(activeProviderId(), providerCallId, effectiveFrom, effectiveTo);
    }

    public RealtimeCallContext getTrialContext(String providerCallId) {
        return lifecycle.getTrialContext(providerCallId);
    }

    public void markTrialEnded(String providerCallId) {
        lifecycle.markTrialEnded(providerCallId);
    }

    public UUID updateStatus(String providerCallId, String providerStatus, Integer durationSeconds) {
        return lifecycle.updateStatus(providerCallId, providerStatus, durationSeconds);
    }

    public RealtimeCallContext markStreamStarted(UUID callId,
                                                 String providerCallId,
                                                 String streamSid,
                                                 String aiProvider) {
        activeProviderId();
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

    private String activeProviderId() {
        String configured = voiceProviders.getTelephonyProvider();
        if (configured == null || !PROVIDER_ID.equalsIgnoreCase(configured.trim())) {
            throw new IllegalStateException(
                    "Twilio adapter cannot handle HELVOCA_TELEPHONY_PROVIDER='" + configured + "'");
        }
        return PROVIDER_ID;
    }
}
