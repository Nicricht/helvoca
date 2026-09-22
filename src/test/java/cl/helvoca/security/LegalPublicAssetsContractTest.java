package cl.helvoca.security;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LegalPublicAssetsContractTest {

    @Test
    void publicLegalPagesRemainAccessibleWithoutAuthentication() {
        Set<String> publicAssets = Set.of(SecurityConfig.PUBLIC_CONSOLE_ASSETS);

        assertTrue(publicAssets.contains("/privacy.html"));
        assertTrue(publicAssets.contains("/terms.html"));
    }
}
