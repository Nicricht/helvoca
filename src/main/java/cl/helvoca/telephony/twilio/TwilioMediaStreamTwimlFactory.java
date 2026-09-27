package cl.helvoca.telephony.twilio;

import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class TwilioMediaStreamTwimlFactory {
    private final TwilioProperties properties;
    private final TwilioMediaRouteSigner signer;

    public TwilioMediaStreamTwimlFactory(TwilioProperties properties,
                                         TwilioMediaRouteSigner signer) {
        this.properties = properties;
        this.signer = signer;
    }

    public String twiml(String businessPhone,
                        String callerPhone,
                        String callSid,
                        String providerId) {
        return twiml(businessPhone, callerPhone, callSid, providerId, null);
    }

    public String twiml(String businessPhone,
                        String callerPhone,
                        String callSid,
                        String providerId,
                        String voiceOverride) {
        long issuedAt = Instant.now().getEpochSecond();
        String token = signer.sign(
                businessPhone, callerPhone, callSid, providerId, voiceOverride, issuedAt);
        String streamUrl = properties.mediaStreamWebSocketUrl();
        String statusUrl = properties.absoluteWebhook("/webhooks/v1/twilio/stream-status");

        String voiceParameter = voiceOverride == null || voiceOverride.isBlank()
                ? ""
                : parameter("voiceOverride", voiceOverride);

        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Response><Connect><Stream url=\"" + xml(streamUrl) + "\""
                + " statusCallback=\"" + xml(statusUrl) + "\" statusCallbackMethod=\"POST\">"
                + parameter("business", businessPhone)
                + parameter("caller", callerPhone)
                + parameter("callSid", callSid)
                + parameter("provider", providerId)
                + voiceParameter
                + parameter("issuedAt", String.valueOf(issuedAt))
                + parameter("route", token)
                + "</Stream></Connect></Response>";
    }

    private static String parameter(String name, String value) {
        return "<Parameter name=\"" + xml(name) + "\" value=\"" + xml(value) + "\"/>";
    }

    private static String xml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
