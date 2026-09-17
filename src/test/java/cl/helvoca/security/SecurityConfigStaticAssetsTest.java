package cl.helvoca.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityConfigStaticAssetsTest {

    @Test
    void conversationsPageAssetsArePublicSoTheBrowserCanLoadBeforeApiAuthentication() {
        var publicAssets = java.util.Set.of(SecurityConfig.PUBLIC_CONSOLE_ASSETS);

        assertTrue(publicAssets.contains("/conversations.html"));
        assertTrue(publicAssets.contains("/conversations.js"));
        assertTrue(publicAssets.contains("/conversations.css"));
    }
}
