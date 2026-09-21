package cl.helvoca.messaging.meta;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

@Component
public class EnvironmentMetaWhatsAppAccessTokenResolver
        implements MetaWhatsAppAccessTokenResolver {

    private final MetaWhatsAppTenantConfigRepository configs;
    private final MetaWhatsAppProperties metaProperties;
    private final Function<String, String> environment;

    @Autowired
    public EnvironmentMetaWhatsAppAccessTokenResolver(
            MetaWhatsAppTenantConfigRepository configs,
            MetaWhatsAppProperties metaProperties) {
        this(configs, metaProperties, System::getenv);
    }

    EnvironmentMetaWhatsAppAccessTokenResolver(
            MetaWhatsAppTenantConfigRepository configs,
            Function<String, String> environment) {
        this(configs, new MetaWhatsAppProperties(), environment);
    }

    EnvironmentMetaWhatsAppAccessTokenResolver(
            MetaWhatsAppTenantConfigRepository configs,
            MetaWhatsAppProperties metaProperties,
            Function<String, String> environment) {
        this.configs = configs;
        this.metaProperties = metaProperties;
        this.environment = environment;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> resolve(UUID businessId) {
        if (businessId == null) return Optional.empty();

        MetaWhatsAppTenantConfig config = configs.findById(businessId).orElse(null);
        if (config == null || !config.isEnabled()) return Optional.empty();

        String credentialRef = normalizeCredentialRef(config.getCredentialRef());
        if (credentialRef == null) return Optional.empty();

        if (MetaWhatsAppEmbeddedSignupCredentialReferenceResolver.EMBEDDED_SIGNUP_SYSTEM_USER
                .equals(credentialRef)) {
            String token = metaProperties.getEmbeddedSignupSystemUserAccessToken();
            return token.isBlank() ? Optional.empty() : Optional.of(token);
        }

        String variable = "HELVOCA_META_WHATSAPP_" + credentialRef + "_ACCESS_TOKEN";
        String token = environment.apply(variable);
        if (token == null || token.trim().isBlank()) return Optional.empty();

        return Optional.of(token.trim());
    }

    private static String normalizeCredentialRef(String value) {
        if (value == null) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return normalized.matches("[A-Z0-9_]{2,80}") ? normalized : null;
    }
}
