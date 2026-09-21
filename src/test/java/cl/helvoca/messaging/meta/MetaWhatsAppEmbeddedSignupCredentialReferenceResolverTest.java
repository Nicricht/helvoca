package cl.helvoca.messaging.meta;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MetaWhatsAppEmbeddedSignupCredentialReferenceResolverTest {

    @Test
    void returnsOpaqueServerOwnedReferenceWhenSystemUserTokenExists() {
        MetaWhatsAppProperties properties = new MetaWhatsAppProperties();
        properties.setEmbeddedSignupSystemUserAccessToken("server-side-system-user-token");

        var resolver = new MetaWhatsAppEmbeddedSignupCredentialReferenceResolver(properties);

        assertEquals(
                MetaWhatsAppEmbeddedSignupCredentialReferenceResolver.EMBEDDED_SIGNUP_SYSTEM_USER,
                resolver.requireReference());
    }

    @Test
    void failsClosedWhenSystemUserTokenIsMissing() {
        var resolver = new MetaWhatsAppEmbeddedSignupCredentialReferenceResolver(
                new MetaWhatsAppProperties());

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                resolver::requireReference);

        assertEquals(
                "Meta Embedded Signup system user access token is not configured",
                error.getMessage());
    }
}
