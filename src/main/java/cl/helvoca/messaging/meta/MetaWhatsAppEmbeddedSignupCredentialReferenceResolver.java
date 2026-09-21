package cl.helvoca.messaging.meta;

import org.springframework.stereotype.Component;

@Component
public class MetaWhatsAppEmbeddedSignupCredentialReferenceResolver {
    public static final String EMBEDDED_SIGNUP_SYSTEM_USER =
            "EMBEDDED_SIGNUP_SYSTEM_USER";

    private final MetaWhatsAppProperties metaProperties;

    public MetaWhatsAppEmbeddedSignupCredentialReferenceResolver(
            MetaWhatsAppProperties metaProperties) {
        this.metaProperties = metaProperties;
    }

    public String requireReference() {
        if (!metaProperties.hasEmbeddedSignupSystemUserAccessToken()) {
            throw new IllegalStateException(
                    "Meta Embedded Signup system user access token is not configured");
        }
        return EMBEDDED_SIGNUP_SYSTEM_USER;
    }
}
