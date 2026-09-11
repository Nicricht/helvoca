package cl.helvoca.voice;

import java.util.List;

public record VoiceReadinessResponse(
        boolean openAiConfigured,
        boolean twilioAuthConfigured,
        boolean publicWebhookConfigured,
        boolean mediaStreamConfigured,
        boolean activePhoneConfigured,
        boolean realtimeReady,
        List<String> missing
) {}
