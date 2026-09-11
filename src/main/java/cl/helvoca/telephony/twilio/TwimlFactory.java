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
        String statusCallback = streamStatusCallbackUrl();
        String callbackAttributes = statusCallback == null ? ""
                : " statusCallback=\"" + escapeXml(statusCallback) + "\" statusCallbackMethod=\"POST\"";
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<Response><Connect><Stream url=\"" + url + "\"" + callbackAttributes + ">" +
                "<Parameter name=\"callId\" value=\"" + callId + "\"/>" +
                "</Stream></Connect></Response>";
    }

    public String trialGather(String prompt) {
        String actionUrl = trialActionUrl("/webhooks/v1/twilio/trial/gather");
        if (actionUrl == null) return serviceUnavailable();
        String action = escapeXml(actionUrl);
        String speechLanguage = escapeXml(trial.getLanguage());
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<Response>" +
                "<Gather input=\"speech\" action=\"" + action + "\" method=\"POST\" " +
                "language=\"" + speechLanguage + "\" speechTimeout=\"auto\" timeout=\"5\" actionOnEmptyResult=\"true\">" +
                trialSay(prompt) +
                "</Gather>" +
                trialSay("No escuché una respuesta. Hasta luego.") + "<Hangup/>" +
                "</Response>";
    }

    public String trialTransfer(String prompt, String targetPhone) {
        String actionUrl = trialActionUrl("/webhooks/v1/twilio/trial/transfer-result");
        if (actionUrl == null || targetPhone == null || targetPhone.isBlank()) return serviceUnavailable();
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<Response>" +
                trialSay(prompt) +
                "<Dial action=\"" + escapeXml(actionUrl) + "\" method=\"POST\" timeout=\"20\" answerOnBridge=\"true\">" +
                "<Number>" + escapeXml(targetPhone.trim()) + "</Number>" +
                "</Dial>" +
                "</Response>";
    }

    public String trialSayAndHangup(String text) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<Response>" + trialSay(text) + "<Hangup/></Response>";
    }

    public String rejectUnknownNumber() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<Response><Reject reason=\"rejected\"/></Response>";
    }

    public String serviceUnavailable() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<Response><Say>Voice service is temporarily unavailable.</Say><Hangup/></Response>";
    }

    private String trialSay(String text) {
        String voice = trial.getTtsVoice();
        String language = trial.getTtsLanguage();
        StringBuilder say = new StringBuilder("<Say");
        if (voice != null && !voice.isBlank()) {
            say.append(" voice=\"").append(escapeXml(voice.trim())).append("\"");
        }
        if (language != null && !language.isBlank()) {
            say.append(" language=\"").append(escapeXml(language.trim())).append("\"");
        }
        say.append(">").append(escapeXml(text)).append("</Say>");
        return say.toString();
    }

    private String trialActionUrl(String path) {
        String base = normalizedPublicBaseUrl();
        if (base == null) return null;
        String actionUrl = base + path;
        if (trial.hasWebhookSecret()) {
            actionUrl += "?trialKey=" + trial.getWebhookSecret();
        }
        return actionUrl;
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
