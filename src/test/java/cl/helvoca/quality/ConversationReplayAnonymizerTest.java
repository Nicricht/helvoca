package cl.helvoca.quality;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConversationReplayAnonymizerTest {

    @Test
    void redactsKnownCustomerAndGenericContactPiiButKeepsCommercialFacts() {
        ConversationReplayAnonymizer anonymizer = new ConversationReplayAnonymizer();

        String result = anonymizer.redact(
                "Soy María Pérez, mi teléfono es +56 9 1234 5678, correo maria@example.com, "
                        + "RUT 12.345.678-5. Vi https://example.com/producto y cuesta $25.000.",
                Map.of("CUSTOMER_NAME", "María Pérez"));

        assertFalse(result.contains("María Pérez"));
        assertFalse(result.contains("+56 9 1234 5678"));
        assertFalse(result.contains("maria@example.com"));
        assertFalse(result.contains("12.345.678-5"));
        assertFalse(result.contains("https://example.com/producto"));
        assertTrue(result.contains("[CUSTOMER_NAME]"));
        assertTrue(result.contains("[PHONE]"));
        assertTrue(result.contains("[EMAIL]"));
        assertTrue(result.contains("[ID]"));
        assertTrue(result.contains("[URL]"));
        assertTrue(result.contains("$25.000"));
    }
}
