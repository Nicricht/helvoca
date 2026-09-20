package cl.helvoca.messaging.meta;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.function.Function;

@Component
public class EnvironmentMetaWhatsAppCredentialAvailability
        implements MetaWhatsAppCredentialAvailability {

    private final Function<String, String> environment;

    @Autowired
    public EnvironmentMetaWhatsAppCredentialAvailability() {
        this(System::getenv);
    }

    EnvironmentMetaWhatsAppCredentialAvailability(Function<String, String> environment) {
        this.environment = environment;
    }

    @Override
    public boolean isAvailable(String credentialRef) {
        if (credentialRef == null) return false;
        String normalized = credentialRef.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9_]{2,80}")) return false;

        String variable = "HELVOCA_META_WHATSAPP_" + normalized + "_ACCESS_TOKEN";
        String token = environment.apply(variable);
        return token != null && !token.trim().isBlank();
    }
}
