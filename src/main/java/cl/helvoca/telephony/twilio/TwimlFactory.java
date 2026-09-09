package cl.helvoca.telephony.twilio;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TwimlFactory {
    private final TwilioProperties properties;

    public TwimlFactory(TwilioProperties properties) { this.properties = properties; }

    public String connectMediaStream(UUID callId) {
        if (!properties.hasMediaStreamUrl()) {
            return serviceUnavailable();
        }
        String url = escapeXml(properties.getMediaStreamUrl().trim());
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<Response><Connect><Stream url=\"" + url + "\">" +
                "<Parameter name=\"callId\" value=\"" + callId + "\"/>" +
                "</Stream></Connect></Response>";
    }

    public String rejectUnknownNumber() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<Response><Reject reason=\"rejected\"/></Response>";
    }

    public String serviceUnavailable() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<Response><Say>Voice service is temporarily unavailable.</Say><Hangup/></Response>";
    }

    private static String escapeXml(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;")
                .replace("<", "&lt;").replace(">", "&gt;");
    }
}
