package cl.helvoca.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Arrays;
import java.util.Map;

@Service
public class ResendHttpsSender {
    private final RestClient client;
    private final String apiKey;

    @Autowired
    public ResendHttpsSender(@Value("${spring.mail.password:}") String apiKey) {
        this(apiKey, RestClient.builder());
    }

    ResendHttpsSender(String apiKey, RestClient.Builder builder) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.client = builder.baseUrl("https://api.resend.com").build();
    }

    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    public void send(SimpleMailMessage message) {
        if (!isConfigured()) {
            throw new IllegalStateException("Resend HTTPS mail is not configured");
        }
        if (message.getFrom() == null || message.getTo() == null
                || message.getTo().length != 1 || message.getSubject() == null
                || message.getText() == null) {
            throw new IllegalArgumentException("Incomplete account email");
        }
        try {
            client.post()
                    .uri("/emails")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "from", message.getFrom(),
                            "to", Arrays.asList(message.getTo()),
                            "subject", message.getSubject(),
                            "text", message.getText()))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException failure) {
            throw new IllegalStateException("Account email delivery failed");
        }
    }
}
