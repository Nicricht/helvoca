package cl.helvoca.console;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsoleInteractionAuditTest {

    @Test
    void legacyRootContainsOnlySafeCompatibilityNavigation() throws Exception {
        String html = read("static/index.html");

        assertTrue(html.contains("window.location.replace('/app/auth')"));
        assertTrue(html.contains("href=\"/app/auth\""));
        assertFalse(html.contains("id=\"logoutBtn\""));
        assertFalse(html.contains("id=\"refreshBtn\""));
        assertFalse(html.contains("id=\"addServiceBtn\""));
        assertFalse(html.contains("id=\"addKnowledgeBtn\""));
        assertFalse(html.contains("/commercial-status.js"));
        assertFalse(html.contains("/phone-provisioning.js"));
    }

    private static String read(String path) throws Exception {
        var resource = new ClassPathResource(path);
        try (var input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
