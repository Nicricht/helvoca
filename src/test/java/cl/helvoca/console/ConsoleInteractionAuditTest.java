package cl.helvoca.console;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsoleInteractionAuditTest {

    @Test
    void everyVisibleConsoleActionHasAHandlerAndPersistentBackendPath() throws Exception {
        String html = read("static/index.html");
        String script = read("static/app.js");

        for (String id : new String[]{
                "registerTab", "loginTab", "logoutBtn", "refreshBtn",
                "addServiceBtn", "addKnowledgeBtn"
        }) {
            assertTrue(html.contains("id=\"" + id + "\""), "Missing interactive element " + id);
            assertTrue(script.contains("$(\"#" + id + "\").addEventListener"), "Missing handler for " + id);
        }

        for (String form : new String[]{"registerForm", "loginForm", "setupForm", "phoneForm"}) {
            assertTrue(html.contains("id=\"" + form + "\""), "Missing form " + form);
            assertTrue(script.contains(form + ".addEventListener(\"submit\""), "Missing submit handler for " + form);
        }

        assertTrue(script.contains("button.addEventListener(\"click\", () => addHourRow())"),
                "Add-hour button must append a real interval");
        assertTrue(script.contains("node.dataset.id = service.id"),
                "Loaded services must preserve backend identity");
        assertTrue(script.contains("id: row.dataset.id || null"),
                "Saved rows must send backend identity");
        assertTrue(script.contains("node.dataset.id = item.id"),
                "Loaded knowledge must preserve backend identity");
        assertTrue(script.contains("/api/v1/onboarding/setup"),
                "Save configuration must persist through onboarding API");
        assertTrue(script.contains("/api/v1/phone-numbers/${phone.id}/active"),
                "Phone activation toggle must call backend");
        assertTrue(script.contains("phone.active ? \"Desactivar\" : \"Activar\""),
                "Phone list must expose activation control");
        assertTrue(script.contains("handleExpiredSession()"),
                "Expired authenticated sessions must be handled");
        assertTrue(script.contains("await loadDashboard();\n        showMessage(setupMessage"),
                "Successful setup must reload persisted IDs before another edit");
    }

    private static String read(String path) throws Exception {
        var resource = new ClassPathResource(path);
        try (var input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
