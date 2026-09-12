package cl.helvoca.telephony.twilio;

import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.call.CallStatus;
import cl.helvoca.telephony.CallLifecycleService;
import cl.helvoca.voice.VoiceProviderProperties;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class TwilioCallService {
    public static final String PROVIDER_ID = "twilio";

    private final CallLifecycleService lifecycle;
    private final TwimlFactory twiml;
    private final VoiceProviderProperties voiceProviders;

    public TwilioCallService(CallLifecycleService lifecycle,
                             TwimlFactory twiml,
                             VoiceProviderProperties voiceProviders) {
        this.lifecycle = lifecycle;
        this.twiml = twiml;
        this.voiceProviders = voiceProviders;
    }

    public String startInboundCall(String providerCallId, String from, String to) {
        UUID callId = lifecycle.startInboundCall(activeProviderId(), providerCallId, from, to);
        return twiml.connectMediaStream(callId);
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
