package cl.helvoca.messaging.outbound;

import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.phone.PhoneNumberRepository;
import cl.helvoca.telephony.twilio.TwilioProperties;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
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
public class TwilioSmsMessagingProvider implements MessagingProvider {
    public static final String ID = "TWILIO_SMS";
    private static final String API_BASE = "https://api.twilio.com";
    private static final Pattern ACCOUNT_SID = Pattern.compile("^AC[0-9a-fA-F]{32}$");
    private static final Pattern E164 = Pattern.compile("^\\+[1-9][0-9]{7,14}$");

    private final TwilioProperties twilio;
    private final PhoneNumberRepository phones;
    private final HttpClient http;

    @Autowired
    public TwilioSmsMessagingProvider(TwilioProperties twilio, PhoneNumberRepository phones) {
        this(twilio, phones, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build());
    }

    TwilioSmsMessagingProvider(TwilioProperties twilio,
                               PhoneNumberRepository phones,
                               HttpClient http) {
        this.twilio = twilio;
        this.phones = phones;
        this.http = http;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean supports(OutboundMessage.Channel channel) {
        return channel == OutboundMessage.Channel.SMS;
    }

    @Override
    public SendResult send(SendCommand command) {
        if (command == null || command.businessId() == null || command.messageId() == null) {
            throw new IllegalArgumentException("Invalid outbound command");
        }
        if (!supports(command.channel())) {
            throw new IllegalArgumentException("Unsupported outbound channel");
        }
        if (!twilio.hasAccountSid() || !twilio.hasAuthToken()
                || !ACCOUNT_SID.matcher(twilio.getAccountSid().trim()).matches()) {
            throw new IllegalStateException("Twilio outbound credentials are not configured");
        }

        String recipient = normalizeE164(command.recipient());
        if (command.content() == null || command.content().isBlank()) {
            throw new IllegalArgumentException("Outbound content is required");
        }

        List<PhoneNumber> senders = phones.findAllByBusinessIdOrderByCreatedAtDesc(command.businessId()).stream()
                .filter(PhoneNumber::isActive)
                .filter(phone -> "TWILIO".equalsIgnoreCase(phone.getProvider()))
                .toList();
        if (senders.size() != 1) {
            throw new IllegalStateException(senders.isEmpty()
                    ? "Tenant has no active Twilio SMS sender"
                    : "Tenant has multiple active Twilio SMS senders; configuration is ambiguous");
        }
        String sender = normalizeE164(senders.getFirst().getPhoneNumber());

        try {
            String accountSid = twilio.getAccountSid().trim();
            String credentials = accountSid + ":" + twilio.getAuthToken().trim();
            String basic = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            String body = form("To", recipient)
                    + "&" + form("From", sender)
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
                throw new IllegalStateException("Twilio rejected SMS message with HTTP " + response.statusCode());
            }
            String sid = new JSONObject(response.body()).optString("sid", "").trim();
            if (sid.isBlank()) {
                throw new IllegalStateException("Twilio did not return a message SID");
            }
            return new SendResult(sid);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Twilio SMS send was interrupted", e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Twilio SMS send failed", e);
        }
    }

    private static String normalizeE164(String value) {
        String clean = value == null ? "" : value.trim();
        if (!E164.matcher(clean).matches()) {
            throw new IllegalArgumentException("SMS address must be E.164");
        }
        return clean;
    }

    private static String form(String key, String value) {
        return URLEncoder.encode(key, StandardCharsets.UTF_8)
                + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
