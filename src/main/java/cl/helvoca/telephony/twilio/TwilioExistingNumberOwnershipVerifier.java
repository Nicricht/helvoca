package cl.helvoca.telephony.twilio;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Internal read-only evidence for pre-existing Twilio numbers. Not enrollment,
 * call authorization or permission to edit any tenant row.
 */
@Component
public class TwilioExistingNumberOwnershipVerifier {
    private static final Pattern ACCOUNT = Pattern.compile("AC[0-9a-fA-F]{32}");
    private static final Pattern PHONE_SID = Pattern.compile("PN[0-9a-fA-F]{32}");
    private static final Pattern E164 = Pattern.compile("\\+[1-9][0-9]{7,14}");
    private static final String VOICE_PATH = "/webhooks/v1/twilio/voice";
    private static final String STATUS_PATH = "/webhooks/v1/twilio/status";

    private final TwilioProperties properties;
    private final String allowedSubaccount;
    private final RestClient client;

    @Autowired
    public TwilioExistingNumberOwnershipVerifier(
            TwilioProperties properties,
            @Value("#{environment['HELVOCA_TWILIO_SUBACCOUNT_SID'] ?: ''}") String allowedSubaccount) {
        this(properties, allowedSubaccount, defaultClient());
    }

    TwilioExistingNumberOwnershipVerifier(TwilioProperties properties, String allowedSubaccount, RestClient client) {
        this.properties = properties;
        this.allowedSubaccount = allowedSubaccount == null ? "" : allowedSubaccount.trim();
        this.client = client;
    }

    private static RestClient defaultClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        return RestClient.builder().baseUrl("https://api.twilio.com").requestFactory(factory).build();
    }

    public enum Code {
        VERIFIED, INVALID_TARGET, NOT_CONFIGURED, ACCOUNT_NOT_ALLOWED,
        ACCOUNT_NOT_VERIFIED, NUMBER_NOT_FOUND, AMBIGUOUS_NUMBER,
        INCOMPLETE_RESPONSE, PHONE_IDENTITY_MISMATCH, VOICE_UNAVAILABLE,
        WEBHOOK_MISMATCH, PROVIDER_UNAVAILABLE
    }

    public enum AccountKind { MAIN, SUBACCOUNT }

    /** Carrier identifiers are internal evidence, never a tenant DTO. */
    public record Evidence(String phoneSid, String ownerAccountSid, AccountKind accountKind) {
        @Override public String toString() { return "TwilioEvidence[REDACTED]"; }
    }

    public record Outcome(Code code, @JsonIgnore Evidence evidence) {
        public boolean verified() { return code == Code.VERIFIED && evidence != null; }
        @Override public String toString() { return "TwilioVerification[" + code + "]"; }
    }

    private static Outcome denied(Code code) { return new Outcome(code, null); }

    /**
     * Caller must be an authorized operator, not a tenant-controlled endpoint.
     * ownerAccountSid must be parent or explicitly configured child SID.
     * expectedPhoneSid may be null for a legacy row; if present it must match.
     * Only authenticated GET account, GET filtered list and GET PN are issued.
     */
    public Outcome verify(String destinationE164, String ownerAccountSid, String expectedPhoneSid) {
        if (destinationE164 == null || !E164.matcher(destinationE164).matches()
                || ownerAccountSid == null || !ACCOUNT.matcher(ownerAccountSid).matches()
                || expectedPhoneSid != null && !PHONE_SID.matcher(expectedPhoneSid).matches()) {
            return denied(Code.INVALID_TARGET);
        }
        if (!properties.hasCommercialProvisioningCredentials()
                || !ACCOUNT.matcher(properties.getAccountSid().trim()).matches()) {
            return denied(Code.NOT_CONFIGURED);
        }
        String parent = properties.getAccountSid().trim();
        if (!ownerAccountSid.equals(parent)
                && !(ownerAccountSid.equals(allowedSubaccount) && ACCOUNT.matcher(allowedSubaccount).matches())) {
            return denied(Code.ACCOUNT_NOT_ALLOWED);
        }
        try {
            Map<?, ?> account = get("/2010-04-01/Accounts/{sid}.json", parent, ownerAccountSid);
            if (account == null
                    || !ownerAccountSid.equals(text(account.get("sid")))
                    || !parent.equals(text(account.get("owner_account_sid")))
                    || !"active".equals(text(account.get("status")))) {
                return denied(Code.ACCOUNT_NOT_VERIFIED);
            }

            Map<?, ?> page = getPhoneList(ownerAccountSid, parent, destinationE164);
            if (page == null || !page.containsKey("next_page_uri")
                    || page.get("next_page_uri") != null
                    || !(page.get("incoming_phone_numbers") instanceof List<?> numbers)) {
                return denied(Code.INCOMPLETE_RESPONSE);
            }
            if (numbers.isEmpty()) return denied(Code.NUMBER_NOT_FOUND);
            if (numbers.size() != 1) return denied(Code.AMBIGUOUS_NUMBER);
            if (!(numbers.get(0) instanceof Map<?, ?> listed)) return denied(Code.INCOMPLETE_RESPONSE);

            String sid = text(listed.get("sid"));
            if (sid == null || !PHONE_SID.matcher(sid).matches()
                    || expectedPhoneSid != null && !expectedPhoneSid.equals(sid)) {
                return denied(Code.PHONE_IDENTITY_MISMATCH);
            }
            Code listCode = assessNumber(listed, destinationE164, ownerAccountSid, sid);
            if (listCode != Code.VERIFIED) return denied(listCode);

            Map<?, ?> fetched = get("/2010-04-01/Accounts/{owner}/IncomingPhoneNumbers/{sid}.json",
                    parent, ownerAccountSid, sid);
            if (fetched == null) return denied(Code.INCOMPLETE_RESPONSE);
            Code detailCode = assessNumber(fetched, destinationE164, ownerAccountSid, sid);
            if (detailCode != Code.VERIFIED) return denied(detailCode);
            AccountKind kind = ownerAccountSid.equals(parent) ? AccountKind.MAIN : AccountKind.SUBACCOUNT;
            return new Outcome(Code.VERIFIED, new Evidence(sid, ownerAccountSid, kind));
        } catch (RestClientException | IllegalArgumentException ex) {
            // Never return/log Twilio response bodies, which may contain secrets.
            return denied(Code.PROVIDER_UNAVAILABLE);
        }
    }

    private Map<?, ?> get(String path, String parent, Object... variables) {
        return client.get()
                .uri(path, variables)
                .headers(h -> h.setBasicAuth(parent, properties.getAuthToken().trim()))
                .retrieve().body(Map.class);
    }

    private Map<?, ?> getPhoneList(String owner, String parent, String number) {
        return client.get()
                .uri(b -> b.path("/2010-04-01/Accounts/{owner}/IncomingPhoneNumbers.json")
                        .queryParam("PhoneNumber", "{number}").build(owner, number))
                .headers(h -> h.setBasicAuth(parent, properties.getAuthToken().trim()))
                .retrieve().body(Map.class);
    }

    private Code assessNumber(Map<?, ?> number, String e164, String owner, String sid) {
        if (!sid.equals(text(number.get("sid")))
                || !owner.equals(text(number.get("account_sid")))
                || !e164.equals(text(number.get("phone_number")))) {
            return Code.PHONE_IDENTITY_MISMATCH;
        }
        if (!"in-use".equals(text(number.get("status")))
                || !"voice".equals(text(number.get("voice_receive_mode")))
                || !(number.get("capabilities") instanceof Map<?, ?> caps)
                || !Boolean.TRUE.equals(caps.get("voice"))) {
            return Code.VOICE_UNAVAILABLE;
        }
        if (!properties.absoluteWebhook(VOICE_PATH).equals(text(number.get("voice_url")))
                || !"POST".equals(text(number.get("voice_method")))
                || !properties.absoluteWebhook(STATUS_PATH).equals(text(number.get("status_callback")))
                || !"POST".equals(text(number.get("status_callback_method")))
                || populated(number.get("voice_application_sid"))
                || populated(number.get("trunk_sid"))) {
            return Code.WEBHOOK_MISMATCH;
        }
        return Code.VERIFIED;
    }

    private static boolean populated(Object value) {
        return value != null && !(value instanceof String s && s.isBlank());
    }

    private static String text(Object value) {
        return value instanceof String s ? s : null;
    }
}
