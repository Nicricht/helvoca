package cl.helvoca.telephony.twilio;

import com.twilio.security.RequestValidator;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Component
public class TwilioSignatureValidator {
    private final TwilioProperties properties;

    public TwilioSignatureValidator(TwilioProperties properties) {
        this.properties = properties;
    }

    public boolean validateHttp(HttpServletRequest request) {
        String signature = request.getHeader("X-Twilio-Signature");
        if (!properties.hasAuthToken() || signature == null || signature.isBlank()) {
            return false;
        }

        RequestValidator validator = new RequestValidator(properties.getAuthToken());
        Map<String, String> parameters = new LinkedHashMap<>();
        request.getParameterMap().forEach((key, values) ->
                parameters.put(key, values == null || values.length == 0 ? "" : values[0]));

        for (String url : httpCandidates(request)) {
            if (validator.validate(url, parameters, signature)) {
                return true;
            }
        }
        return false;
    }

    public boolean validateWebSocket(String requestUri, String signature) {
        if (!properties.hasAuthToken() || signature == null || signature.isBlank()) {
            return false;
        }

        RequestValidator validator = new RequestValidator(properties.getAuthToken());
        Set<String> candidates = new LinkedHashSet<>();
        if (properties.hasMediaStreamUrl()) {
            addWebSocketCandidates(candidates, properties.getMediaStreamUrl().trim());
        }
        addWebSocketCandidates(candidates, requestUri);

        for (String url : candidates) {
            if (validator.validate(url, Map.of(), signature)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> httpCandidates(HttpServletRequest request) {
        Set<String> candidates = new LinkedHashSet<>();
        String query = request.getQueryString();
        String suffix = request.getRequestURI() + (query == null || query.isBlank() ? "" : "?" + query);

        if (properties.getPublicBaseUrl() != null && !properties.getPublicBaseUrl().isBlank()) {
            String base = trimTrailingSlash(properties.getPublicBaseUrl().trim());
            candidates.add(base + suffix);
        }

        candidates.add(request.getRequestURL().toString()
                + (query == null || query.isBlank() ? "" : "?" + query));

        String forwardedProto = firstForwardedValue(request.getHeader("X-Forwarded-Proto"));
        String forwardedHost = firstForwardedValue(request.getHeader("X-Forwarded-Host"));
        if (forwardedProto != null && forwardedHost != null) {
            candidates.add(forwardedProto + "://" + forwardedHost + suffix);
        }

        return candidates;
    }

    private static void addWebSocketCandidates(Set<String> candidates, String url) {
        if (url == null || url.isBlank()) return;
        String value = url.trim();
        addWithSlashVariant(candidates, value);

        if (value.startsWith("wss://")) {
            addWithSlashVariant(candidates, "https://" + value.substring("wss://".length()));
        } else if (value.startsWith("ws://")) {
            addWithSlashVariant(candidates, "http://" + value.substring("ws://".length()));
        } else if (value.startsWith("https://")) {
            addWithSlashVariant(candidates, "wss://" + value.substring("https://".length()));
        } else if (value.startsWith("http://")) {
            String rest = value.substring("http://".length());
            addWithSlashVariant(candidates, "https://" + rest);
            addWithSlashVariant(candidates, "ws://" + rest);
            addWithSlashVariant(candidates, "wss://" + rest);
        }
    }

    private static void addWithSlashVariant(Set<String> candidates, String url) {
        candidates.add(url);
        candidates.add(url.endsWith("/") ? url.substring(0, url.length() - 1) : url + "/");
    }

    private static String firstForwardedValue(String value) {
        if (value == null || value.isBlank()) return null;
        int comma = value.indexOf(',');
        return (comma >= 0 ? value.substring(0, comma) : value).trim();
    }

    private static String trimTrailingSlash(String value) {
        String out = value;
        while (out.endsWith("/")) out = out.substring(0, out.length() - 1);
        return out;
    }
}
