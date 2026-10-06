package cl.helvoca.console;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsoleAiOnboardingTest {

    @Test
    void publicRootDelegatesAuthenticationAndOnboardingToCanonicalReactSurfaces() throws Exception {
        String html = read("static/index.html");

        assertTrue(html.contains("url=/app/auth"));
        assertTrue(html.contains("window.location.replace('/app/auth')"));
        assertFalse(html.contains("id=\"aiForm\""));
        assertFalse(html.contains("id=\"setupForm\""));
        assertFalse(html.contains("id=\"phoneForm\""));
        assertFalse(html.contains("id=\"dashboardView\""));
        assertFalse(html.contains("/business-activation-guide.js"));
    }

    private static String read(String path) throws Exception {
        var resource = new ClassPathResource(path);
        try (var input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
