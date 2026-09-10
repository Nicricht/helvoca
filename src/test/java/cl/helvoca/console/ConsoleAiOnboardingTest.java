package cl.helvoca.console;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ConsoleAiOnboardingTest {

    @Test
    void primaryRegistrationIsShortAndAIAnalysisRequiresConfirmation() throws Exception {
        String html = read("static/index.html");
        String script = read("static/app.js");

        assertTrue(html.contains("id=\"aiForm\""));
        assertTrue(html.contains("id=\"confirmProposalBtn\""));
        assertTrue(html.contains("id=\"editProposalBtn\""));
        assertTrue(html.contains("id=\"advancedToggleBtn\""));
        assertFalse(section(html, "id=\"registerForm\"", "</form>").contains("name=\"timezone\""));
        assertFalse(section(html, "id=\"registerForm\"", "</form>").contains("name=\"humanTransferPhone\""));

        assertTrue(script.contains("/api/v1/onboarding/analyze"));
        assertTrue(script.contains("$(\"#confirmProposalBtn\").addEventListener"));
        assertTrue(script.contains("await api(\"/api/v1/onboarding/setup\""));
        assertTrue(script.contains("window.confirm("), "Existing configuration needs replacement confirmation");
        assertTrue(script.contains("detectedTimezone()"));
        assertTrue(script.contains("detectedLanguage()"));
    }

    private static String section(String text, String startToken, String endToken) {
        int start = text.indexOf(startToken);
        if (start < 0) return "";
        int end = text.indexOf(endToken, start);
        return end < 0 ? text.substring(start) : text.substring(start, end + endToken.length());
    }

    private static String read(String path) throws Exception {
        var resource = new ClassPathResource(path);
        try (var input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
