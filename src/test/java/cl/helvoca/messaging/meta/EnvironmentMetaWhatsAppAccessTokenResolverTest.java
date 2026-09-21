package cl.helvoca.messaging.meta;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class EnvironmentMetaWhatsAppAccessTokenResolverTest {

    @Test
    void resolvesTokenFromDeploymentSecretUsingTenantCredentialRef() {
        UUID businessId = UUID.randomUUID();
        MetaWhatsAppTenantConfigRepository configs = mock(MetaWhatsAppTenantConfigRepository.class);
        MetaWhatsAppTenantConfig config = config(businessId, true, " ACME_01 ");
        when(configs.findById(businessId)).thenReturn(Optional.of(config));

        Map<String, String> env = Map.of(
                "HELVOCA_META_WHATSAPP_ACME_01_ACCESS_TOKEN",
                "  tenant-secret-token  ");

        var resolver = new EnvironmentMetaWhatsAppAccessTokenResolver(
                configs,
                env::get);

        assertEquals(Optional.of("tenant-secret-token"), resolver.resolve(businessId));
    }

    @Test
    void resolvesEmbeddedSignupSystemUserTokenWithoutReadingTenantNamedSecret() {
        UUID businessId = UUID.randomUUID();
        MetaWhatsAppTenantConfigRepository configs = mock(MetaWhatsAppTenantConfigRepository.class);
        when(configs.findById(businessId)).thenReturn(Optional.of(config(
                businessId,
                true,
                MetaWhatsAppEmbeddedSignupCredentialReferenceResolver.EMBEDDED_SIGNUP_SYSTEM_USER)));

        MetaWhatsAppProperties properties = new MetaWhatsAppProperties();
        properties.setEmbeddedSignupSystemUserAccessToken("  embedded-system-user-token  ");

        @SuppressWarnings("unchecked")
        Function<String, String> env = mock(Function.class);
        var resolver = new EnvironmentMetaWhatsAppAccessTokenResolver(
                configs,
                properties,
                env);

        assertEquals(Optional.of("embedded-system-user-token"), resolver.resolve(businessId));
        verifyNoInteractions(env);
    }

    @Test
    void disabledTenantNeverReadsDeploymentSecret() {
        UUID businessId = UUID.randomUUID();
        MetaWhatsAppTenantConfigRepository configs = mock(MetaWhatsAppTenantConfigRepository.class);
        when(configs.findById(businessId))
                .thenReturn(Optional.of(config(businessId, false, "ACME_01")));

        @SuppressWarnings("unchecked")
        Function<String, String> env = mock(Function.class);
        var resolver = new EnvironmentMetaWhatsAppAccessTokenResolver(configs, env);

        assertTrue(resolver.resolve(businessId).isEmpty());
        verifyNoInteractions(env);
    }

    @Test
    void missingTenantConfigFailsClosed() {
        UUID businessId = UUID.randomUUID();
        MetaWhatsAppTenantConfigRepository configs = mock(MetaWhatsAppTenantConfigRepository.class);
        when(configs.findById(businessId)).thenReturn(Optional.empty());

        var resolver = new EnvironmentMetaWhatsAppAccessTokenResolver(configs, key -> "should-not-be-used");

        assertTrue(resolver.resolve(businessId).isEmpty());
    }

    @Test
    void invalidCredentialRefFailsClosedBeforeReadingEnvironment() {
        UUID businessId = UUID.randomUUID();
        MetaWhatsAppTenantConfigRepository configs = mock(MetaWhatsAppTenantConfigRepository.class);
        when(configs.findById(businessId))
                .thenReturn(Optional.of(config(businessId, true, "../BAD")));

        @SuppressWarnings("unchecked")
        Function<String, String> env = mock(Function.class);
        var resolver = new EnvironmentMetaWhatsAppAccessTokenResolver(configs, env);

        assertTrue(resolver.resolve(businessId).isEmpty());
        verifyNoInteractions(env);
    }

    @Test
    void missingDeploymentSecretFailsClosed() {
        UUID businessId = UUID.randomUUID();
        MetaWhatsAppTenantConfigRepository configs = mock(MetaWhatsAppTenantConfigRepository.class);
        when(configs.findById(businessId))
                .thenReturn(Optional.of(config(businessId, true, "ACME_01")));

        var resolver = new EnvironmentMetaWhatsAppAccessTokenResolver(configs, key -> null);

        assertTrue(resolver.resolve(businessId).isEmpty());
    }

    private static MetaWhatsAppTenantConfig config(
            UUID businessId,
            boolean enabled,
            String credentialRef) {
        MetaWhatsAppTenantConfig config = new MetaWhatsAppTenantConfig();
        config.setBusinessId(businessId);
        config.setEnabled(enabled);
        config.setCredentialRef(credentialRef);
        return config;
    }
}
