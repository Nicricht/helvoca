package cl.helvoca.console;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsoleFormSubmissionTest {

    @Test
    void legacyRootIsCompatibilityOnlyAndDoesNotOwnForms() throws Exception {
        String html = read("static/index.html");

        assertTrue(html.contains("/app/auth"));
        assertFalse(html.contains("<form"));
        assertFalse(html.contains("/app.js"));
        assertFalse(html.contains("id=\"registerForm\""));
        assertFalse(html.contains("id=\"loginForm\""));
    }

    private static String read(String path) throws Exception {
        var resource = new ClassPathResource(path);
        try (var input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
