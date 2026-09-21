package cl.helvoca.security;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PwaPublicAssetsContractTest {

    @Test
    void pwaAssetsRemainPublic() {
        Set<String> publicAssets = Set.of(SecurityConfig.PUBLIC_CONSOLE_ASSETS);

        assertTrue(publicAssets.contains("/manifest.webmanifest"));
        assertTrue(publicAssets.contains("/service-worker.js"));
        assertTrue(publicAssets.contains("/recepvoz-icon-192.png"));
        assertTrue(publicAssets.contains("/recepvoz-icon-512.png"));
    }
}
