package cl.helvoca.messaging.outbound;

import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.phone.PhoneNumberRepository;
import cl.helvoca.telephony.twilio.TwilioProperties;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class TwilioWhatsAppMessagingProvider implements MessagingProvider {
    public static final String ID = "TWILIO_WHATSAPP";
    private static final String API_BASE = "https://api.twilio.com";
    private static final Pattern ACCOUNT_SID = Pattern.compile("^AC[0-9a-fA-F]{32}$");
    private static final Pattern E164 = Pattern.compile("^\\+[1-9][0-9]{7,14}$");

    private final TwilioProperties twilio;
    private final PhoneNumberRepository phones;
    private final HttpClient http;

    public TwilioWhatsAppMessagingProvider(TwilioProperties twilio, PhoneNumberRepository phones) {
        this(twilio, phones, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build());
    }

    TwilioWhatsAppMessagingProvider(TwilioProperties twilio,
                                    PhoneNumberRepository phones,
                                    HttpClient http) {
        this.twilio = twilio;
        this.phones = phones;
        this.http = http;
    }

    @Override
    public String id() { return ID; }

    @Override
    public boolean supports(OutboundMessage.Channel channel) {
        return channel == OutboundMessage.Channel.WHATSAPP;
    }

    @Override
    public SendResult send(SendCommand command) {
        if (command == null || command.businessId() == null || command.messageId() == null) {
            throw new IllegalArgumentException("Invalid outbound command");
        }
        if (!supports(command.channel())) throw new IllegalArgumentException("Unsupported outbound channel");
        if (!twilio.hasAccountSid() || !twilio.hasAuthToken()
                || !ACCOUNT_SID.matcher(twilio.getAccountSid().trim()).matches()) {
            throw new IllegalStateException("Twilio outbound credentials are not configured");
        }

        String recipient = normalizeE164(command.recipient());
        if (command.content() == null || command.content().isBlank()) {
            throw new IllegalArgumentException("Outbound content is required");
        }

        List<PhoneNumber> senders = phones.findAllByBusinessIdAndActiveTrueAndWhatsappEnabledTrueOrderByCreatedAtDesc(
                command.businessId());
        if (senders.size() != 1) {
            throw new IllegalStateException(senders.isEmpty()
                    ? "Tenant has no WhatsApp-enabled sender"
                    : "Tenant has multiple WhatsApp-enabled senders; configuration is ambiguous");
        }
        String sender = normalizeE164(senders.getFirst().getPhoneNumber());

        try {
            String accountSid = twilio.getAccountSid().trim();
            String credentials = accountSid + ":" + twilio.getAuthToken().trim();
            String basic = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            String body = form("To", "whatsapp:" + recipient)
                    + "&" + form("From", "whatsapp:" + sender)
                    + "&" + form("Body", command.content());
            URI uri = URI.create(API_BASE + "/2010-04-01/Accounts/" + accountSid + "/Messages.json");
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(12))
                    .header("Authorization", "Basic " + basic)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Twilio rejected WhatsApp message with HTTP " + response.statusCode());
            }
            String sid = new JSONObject(response.body()).optString("sid", "").trim();
            if (sid.isBlank()) throw new IllegalStateException("Twilio did not return a message SID");
            return new SendResult(sid);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Twilio WhatsApp send was interrupted", e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Twilio WhatsApp send failed", e);
        }
    }

    private static String normalizeE164(String value) {
        String clean = value == null ? "" : value.trim();
        if (clean.regionMatches(true, 0, "whatsapp:", 0, 9)) clean = clean.substring(9).trim();
        if (!E164.matcher(clean).matches()) throw new IllegalArgumentException("WhatsApp address must be E.164");
        return clean;
    }

    private static String form(String key, String value) {
        return URLEncoder.encode(key, StandardCharsets.UTF_8)
                + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
