package cl.helvoca.messaging.meta;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MetaWhatsAppTenantConfigTest {

    @Test
    void storesOptionalWabaIdWithoutTreatingItAsASecret() {
        MetaWhatsAppTenantConfig config = new MetaWhatsAppTenantConfig();

        assertNull(config.getWabaId());

        config.setWabaId("123456789012345");
        assertEquals("123456789012345", config.getWabaId());

        config.setWabaId(null);
        assertNull(config.getWabaId());
    }
}
