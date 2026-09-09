package cl.helvoca.telephony.twilio;

import cl.helvoca.telephony.twilio.trial.TrialVoiceProperties;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TwimlFactory {
    private final TwilioProperties properties;
    private final TrialVoiceProperties trial;

    public TwimlFactory(TwilioProperties properties, TrialVoiceProperties trial) {
        this.properties = properties;
        this.trial = trial;
    }

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

    public String trialGather(String prompt) {
        String base = normalizedPublicBaseUrl();
        if (base == null) return serviceUnavailable();
        String action = escapeXml(base + "/webhooks/v1/twilio/trial/gather");
        String speechLanguage = escapeXml(trial.getLanguage());
        String spoken = escapeXml(prompt);
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<Response>" +
                "<Gather input=\"speech\" action=\"" + action + "\" method=\"POST\" " +
                "language=\"" + speechLanguage + "\" speechTimeout=\"auto\" timeout=\"5\" actionOnEmptyResult=\"true\">" +
                "<Say language=\"es-MX\">" + spoken + "</Say>" +
                "</Gather>" +
                "<Say language=\"es-MX\">No escuché una respuesta. Hasta luego.</Say><Hangup/>" +
                "</Response>";
    }

    public String trialSayAndHangup(String text) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<Response><Say language=\"es-MX\">" + escapeXml(text) + "</Say><Hangup/></Response>";
    }

    public String rejectUnknownNumber() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<Response><Reject reason=\"rejected\"/></Response>";
    }

    public String serviceUnavailable() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<Response><Say>Voice service is temporarily unavailable.</Say><Hangup/></Response>";
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
