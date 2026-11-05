package cl.helvoca.messaging.meta;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MetaWhatsAppEmbeddedSignupBootstrapResponseTest {
    @Test
    void legacyFiveFieldConstructorKeepsCoexistenceDisabled() {
        var response = new MetaWhatsAppEmbeddedSignupBootstrapResponse(
                true, true, "123456789", "987654321", "v26.0");

        assertTrue(response.enabled());
        assertTrue(response.available());
        assertEquals("123456789", response.appId());
        assertEquals("987654321", response.configId());
        assertEquals("v26.0", response.graphApiVersion());
        assertFalse(response.coexistenceAvailable());
    }
}
