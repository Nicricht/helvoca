package cl.helvoca.voice;

import cl.helvoca.ai.realtime.OpenAiRealtimeProperties;
import cl.helvoca.phone.PhoneNumberRepository;
import cl.helvoca.security.TenantProvider;
import cl.helvoca.telephony.twilio.TwilioProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class VoiceReadinessService {
    private final OpenAiRealtimeProperties openAi;
    private final TwilioProperties twilio;
    private final PhoneNumberRepository phones;
    private final TenantProvider tenantProvider;

    public VoiceReadinessService(OpenAiRealtimeProperties openAi,
                                 TwilioProperties twilio,
                                 PhoneNumberRepository phones,
                                 TenantProvider tenantProvider) {
        this.openAi = openAi;
        this.twilio = twilio;
        this.phones = phones;
        this.tenantProvider = tenantProvider;
    }

    @Transactional(readOnly = true)
    public VoiceReadinessResponse current() {
        UUID businessId = tenantProvider.requireBusinessId();
        boolean openAiConfigured = openAi.hasApiKey();
        boolean twilioAuthConfigured = twilio.hasAuthToken();
        boolean publicWebhookConfigured = twilio.getPublicBaseUrl() != null && !twilio.getPublicBaseUrl().isBlank();
        boolean mediaStreamConfigured = twilio.hasMediaStreamUrl();
        boolean activePhoneConfigured = phones.findAllByBusinessIdOrderByCreatedAtDesc(businessId)
                .stream().anyMatch(phone -> phone.isActive());

        List<String> missing = new ArrayList<>();
        if (!openAiConfigured) missing.add("OPENAI_API_KEY");
        if (!twilioAuthConfigured) missing.add("TWILIO_AUTH_TOKEN");
        if (!publicWebhookConfigured) missing.add("TWILIO_PUBLIC_BASE_URL");
        if (!mediaStreamConfigured) missing.add("TWILIO_MEDIA_STREAM_URL");
        if (!activePhoneConfigured) missing.add("ACTIVE_PHONE_NUMBER");

        return new VoiceReadinessResponse(
                openAiConfigured,
                twilioAuthConfigured,
                publicWebhookConfigured,
                mediaStreamConfigured,
                activePhoneConfigured,
                missing.isEmpty(),
                List.copyOf(missing));
    }
}
