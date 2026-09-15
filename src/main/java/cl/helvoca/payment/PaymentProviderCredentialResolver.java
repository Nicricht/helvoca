package cl.helvoca.payment;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

@Component
public class PaymentProviderCredentialResolver {
    public Optional<Credentials> resolve(String credentialRef) {
        if (credentialRef == null || !credentialRef.matches("[A-Z0-9_]{2,80}")) return Optional.empty();
        String prefix = "HELVOCA_PAYMENT_" + credentialRef.toUpperCase(Locale.ROOT) + "_";
        String accessToken = env(prefix + "ACCESS_TOKEN");
        String webhookSecret = env(prefix + "WEBHOOK_SECRET");
        if (accessToken.isBlank() || webhookSecret.isBlank()) return Optional.empty();
        return Optional.of(new Credentials(accessToken, webhookSecret));
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value.trim();
    }

    public record Credentials(String accessToken, String webhookSecret) {}
}
