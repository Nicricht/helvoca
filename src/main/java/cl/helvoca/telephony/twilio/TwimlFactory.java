package cl.helvoca.telephony.twilio;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TwimlFactory {
    private final TwilioProperties properties;

    public TwimlFactory(TwilioProperties properties) {
        this.properties = properties;
    }

    public String connectMediaStream(UUID callId) {
        if (!properties.hasMediaStreamUrl()) {
            return serviceUnavailable();
        }
        return connectStream(properties.getMediaStreamUrl().trim(), streamStatusCallbackUrl(), callId);
    }

    private String connectStream(String streamUrl, String statusCallback, UUID callId) {
        String url = escapeXml(streamUrl);
        String callbackAttributes = statusCallback == null ? ""
                : " statusCallback=\"" + escapeXml(statusCallback) + "\" statusCallbackMethod=\"POST\"";
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<Response><Connect><Stream url=\"" + url + "\"" + callbackAttributes + ">" +
                "<Parameter name=\"callId\" value=\"" + callId + "\"/>" +
                "</Stream></Connect></Response>";
    }

    public String serviceUnavailable() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response><Hangup/></Response>";
    }

    private String streamStatusCallbackUrl() {
        String base = normalizedPublicBaseUrl();
        return base == null ? null : base + "/webhooks/v1/twilio/stream-status";
    }

    private String normalizedPublicBaseUrl() {
        String base = properties.getPublicBaseUrl();
        if (base == null || base.isBlank()) return null;
        base = base.trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base;
    }

    private static String escapeXml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("\"", "&quot;")
                .replace("<", "&lt;").replace(">", "&gt;");
    }
}
