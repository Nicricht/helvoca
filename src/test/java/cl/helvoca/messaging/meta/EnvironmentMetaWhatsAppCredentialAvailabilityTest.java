package cl.helvoca.messaging.meta;

import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EnvironmentMetaWhatsAppCredentialAvailabilityTest {

    @Test
    void embeddedSignupReferenceUsesConfiguredSystemUserToken() {
        MetaWhatsAppProperties properties = new MetaWhatsAppProperties();
        properties.setEmbeddedSignupSystemUserAccessToken("system-user-token");

        @SuppressWarnings("unchecked")
        Function<String, String> environment = mock(Function.class);
        var availability = new EnvironmentMetaWhatsAppCredentialAvailability(
                properties,
                environment);

        assertTrue(availability.isAvailable(
                MetaWhatsAppEmbeddedSignupCredentialReferenceResolver.EMBEDDED_SIGNUP_SYSTEM_USER));
        verifyNoInteractions(environment);
    }

    @Test
    void embeddedSignupReferenceFailsClosedWhenSystemUserTokenIsMissing() {
        @SuppressWarnings("unchecked")
        Function<String, String> environment = mock(Function.class);
        var availability = new EnvironmentMetaWhatsAppCredentialAvailability(
                new MetaWhatsAppProperties(),
                environment);

        assertFalse(availability.isAvailable(
                MetaWhatsAppEmbeddedSignupCredentialReferenceResolver.EMBEDDED_SIGNUP_SYSTEM_USER));
        verifyNoInteractions(environment);
    }
}
