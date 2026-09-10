package cl.helvoca.console;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsoleFormSubmissionTest {

    @Test
    void busyStateMustNotDisableFormFieldsBeforeFormDataIsBuilt() throws Exception {
        var resource = new ClassPathResource("static/app.js");
        String script;
        try (var input = resource.getInputStream()) {
            script = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(script.contains("$$(\"button\", form).forEach(el => el.disabled = busy)"));
        assertFalse(script.contains("button, input, select, textarea"));
    }
}
