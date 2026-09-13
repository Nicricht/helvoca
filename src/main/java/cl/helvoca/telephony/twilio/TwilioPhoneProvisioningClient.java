package cl.helvoca.telephony.twilio;

import cl.helvoca.common.ExternalProviderException;
import cl.helvoca.phone.AvailablePhoneNumberResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TwilioPhoneProvisioningClient {
    private static final Logger log = LoggerFactory.getLogger(TwilioPhoneProvisioningClient.class);
    private static final Pattern ACCOUNT_SID = Pattern.compile("^AC[0-9a-fA-F]{32}$");
    private static final Pattern PHONE_SID = Pattern.compile("^PN[0-9a-fA-F]{32}$");
    private static final Pattern E164 = Pattern.compile("^\\+[1-9][0-9]{7,14}$");
    private static final Pattern PROVIDER_MESSAGE = Pattern.compile("\\\"message\\\"\\s*:\\s*\\\"([^\\\"]{1,400})\\\"");
    private static final String DEFAULT_API_BASE = "https://api.twilio.com";

    private final TwilioProperties properties;
    private final RestClient restClient;

    @Autowired
    public TwilioPhoneProvisioningClient(TwilioProperties properties) {
        this(properties, RestClient.builder().baseUrl(DEFAULT_API_BASE).build());
    }

    TwilioPhoneProvisioningClient(TwilioProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    public boolean enabled() {
        return properties.isProvisioningEnabled();
    }

    public boolean configured() {
        return properties.hasCommercialProvisioningCredentials()
                && ACCOUNT_SID.matcher(properties.getAccountSid().trim()).matches();
    }

    public List<AvailablePhoneNumberResponse> searchAvailable(String country, String areaCode, int limit) {
        requireAvailable();
        String normalizedCountry = normalizeCountry(country);
        String normalizedAreaCode = normalizeAreaCode(areaCode);
        int normalizedLimit = Math.max(1, Math.min(limit, 20));

        try {
            Map<?, ?> payload = restClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder
                                .path("/2010-04-01/Accounts/{accountSid}/AvailablePhoneNumbers/{country}/Local.json")
                                .queryParam("VoiceEnabled", "true")
                                .queryParam("Limit", normalizedLimit);
                        if (normalizedAreaCode != null) builder.queryParam("AreaCode", normalizedAreaCode);
                        return builder.build(properties.getAccountSid().trim(), normalizedCountry);
                    })
                    .headers(headers -> headers.setBasicAuth(properties.getAccountSid().trim(), properties.getAuthToken().trim()))
                    .retrieve()
                    .body(Map.class);

            if (payload == null) return List.of();
            Object raw = payload.get("available_phone_numbers");
            if (!(raw instanceof List<?> list)) return List.of();

            List<AvailablePhoneNumberResponse> result = new ArrayList<>();
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map)) continue;
                String number = string(map.get("phone_number"));
                if (number == null || !E164.matcher(number).matches()) continue;
                boolean voice = capability(map.get("capabilities"), "voice");
                if (!voice) continue;
                result.add(new AvailablePhoneNumberResponse(
                        number,
                        string(map.get("friendly_name")),
                        string(map.get("locality")),
                        string(map.get("region")),
                        string(map.get("postal_code")),
                        string(map.get("iso_country")),
                        string(map.get("address_requirements")),
                        true
                ));
            }
            return List.copyOf(result);
        } catch (RestClientResponseException ex) {
            throw providerFailure("No pude buscar números disponibles en Twilio", ex);
        } catch (ExternalProviderException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ExternalProviderException("No pude comunicarme con Twilio para buscar números disponibles", ex);
        }
    }

    public ProvisionedPhoneNumber provision(String phoneNumber) {
        requireAvailable();
        String number = normalizePhone(phoneNumber);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("PhoneNumber", number);
        form.add("VoiceUrl", properties.absoluteWebhook("/webhooks/v1/twilio/voice"));
        form.add("VoiceMethod", "POST");
        form.add("StatusCallback", properties.absoluteWebhook("/webhooks/v1/twilio/status"));
        form.add("StatusCallbackMethod", "POST");

        try {
            Map<?, ?> payload = restClient.post()
                    .uri("/2010-04-01/Accounts/{accountSid}/IncomingPhoneNumbers.json", properties.getAccountSid().trim())
                    .headers(headers -> headers.setBasicAuth(properties.getAccountSid().trim(), properties.getAuthToken().trim()))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);

            String sid = payload == null ? null : string(payload.get("sid"));
            String provisionedNumber = payload == null ? null : string(payload.get("phone_number"));
            if (sid == null || !PHONE_SID.matcher(sid).matches() || provisionedNumber == null) {
                throw new ExternalProviderException("Twilio devolvió una respuesta incompleta. Revisa la cuenta antes de reintentar.");
            }
            return new ProvisionedPhoneNumber(sid, provisionedNumber);
        } catch (RestClientResponseException ex) {
            throw providerFailure("Twilio rechazó el aprovisionamiento del número", ex);
        } catch (ExternalProviderException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ExternalProviderException("No pude comunicarme con Twilio para aprovisionar el número", ex);
        }
    }

    public void releaseQuietly(String phoneSid) {
        if (phoneSid == null || !PHONE_SID.matcher(phoneSid).matches() || !configured()) return;
        try {
            restClient.delete()
                    .uri("/2010-04-01/Accounts/{accountSid}/IncomingPhoneNumbers/{phoneSid}.json",
                            properties.getAccountSid().trim(), phoneSid)
                    .headers(headers -> headers.setBasicAuth(properties.getAccountSid().trim(), properties.getAuthToken().trim()))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            log.error("Could not release provisioned Twilio number after local persistence failure: {}", ex.getMessage());
        }
    }

    private void requireAvailable() {
        if (!enabled()) {
            throw new ExternalProviderException("El aprovisionamiento automático de Twilio está deshabilitado.");
        }
        if (!properties.hasCommercialProvisioningCredentials()) {
            throw new ExternalProviderException("Twilio comercial aún no está configurado para aprovisionamiento.");
        }
        if (!ACCOUNT_SID.matcher(properties.getAccountSid().trim()).matches()) {
            throw new ExternalProviderException("TWILIO_ACCOUNT_SID no tiene un formato válido.");
        }
    }

    private static String normalizeCountry(String country) {
        if (country == null) throw new IllegalArgumentException("El país es obligatorio.");
        String value = country.trim().toUpperCase(Locale.ROOT);
        if (!value.matches("^[A-Z]{2}$")) {
            throw new IllegalArgumentException("El país debe usar código ISO de 2 letras, por ejemplo CL o US.");
        }
        return value;
    }

    private static String normalizeAreaCode(String areaCode) {
        if (areaCode == null || areaCode.isBlank()) return null;
        String value = areaCode.trim();
        if (!value.matches("^[0-9]{1,8}$")) {
            throw new IllegalArgumentException("El código de área debe contener solo dígitos.");
        }
        return value;
    }

    private static String normalizePhone(String phoneNumber) {
        if (phoneNumber == null) throw new IllegalArgumentException("El número es obligatorio.");
        String value = phoneNumber.trim();
        if (!E164.matcher(value).matches()) {
            throw new IllegalArgumentException("El número debe estar en formato E.164.");
        }
        return value;
    }

    private static boolean capability(Object raw, String key) {
        if (!(raw instanceof Map<?, ?> map)) return false;
        Object value = map.get(key);
        return value instanceof Boolean b && b;
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static ExternalProviderException providerFailure(String prefix, RestClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        Matcher matcher = body == null ? null : PROVIDER_MESSAGE.matcher(body);
        String detail = matcher != null && matcher.find() ? matcher.group(1) : "HTTP " + ex.getStatusCode().value();
        return new ExternalProviderException(prefix + ": " + detail, ex);
    }

    public record ProvisionedPhoneNumber(String sid, String phoneNumber) {}
}
