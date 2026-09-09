package cl.helvoca.telephony.twilio;

import com.twilio.security.RequestValidator;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

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
        String url = publicHttpUrl(request);
        Map<String, String> parameters = new LinkedHashMap<>();
        request.getParameterMap().forEach((key, values) ->
                parameters.put(key, values == null || values.length == 0 ? "" : values[0]));
        return validator.validate(url, parameters, signature);
    }

    public boolean validateWebSocket(String requestUri, String signature) {
        if (!properties.hasAuthToken() || signature == null || signature.isBlank()) {
            return false;
        }
        RequestValidator validator = new RequestValidator(properties.getAuthToken());
        String url = properties.hasMediaStreamUrl() ? properties.getMediaStreamUrl().trim() : requestUri;
        if (validator.validate(url, Map.of(), signature)) {
            return true;
        }
        String alternate = url.endsWith("/") ? url.substring(0, url.length() - 1) : url + "/";
        return validator.validate(alternate, Map.of(), signature);
    }

    private String publicHttpUrl(HttpServletRequest request) {
        String query = request.getQueryString();
        if (properties.getPublicBaseUrl() != null && !properties.getPublicBaseUrl().isBlank()) {
            String base = properties.getPublicBaseUrl().trim();
            while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
            return base + request.getRequestURI() + (query == null || query.isBlank() ? "" : "?" + query);
        }
        return request.getRequestURL().toString() + (query == null || query.isBlank() ? "" : "?" + query);
    }
}
